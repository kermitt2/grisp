package com.scienceminer.grisp.nerd.data;

import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.compress.compressors.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.io.*;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import org.wikipedia.miner.extract.util.PagesByTitleCache;

import org.apache.hadoop.fs.*;

/**
 * This class processes the WikiData JSON dump file (latest-all.json), extracting wikidata
 * identifiers, properties and relations. This will create the backbone of the global
 * knowledge model.
 *
 * Language-specific wikipedia are plug on this via the **wiki-latest-page_props.sql file
 * of each language.
 *
 * @author Patrice Lopez
 *
 */
public class ProcessWikiData {

	private Env env_id;
	private Env env_data;
  	private Database db_id; // database (map) for storing the page ids in different languages
  	private Database db_data; // database (map) for storing the properties and relations present in wikidata
  	private String envFilePath_id = null;
	private String envFilePath_data = null;
  	//private PagesByTitleCache pageCache = null;

  	// this is the list of languages we consider for target translations, we will ignore the other
  	// languages
  	private static List<String> targetLanguages = Arrays.asList("en","fr", "de", "es", "it");

  	public ProcessWikiData(String pathWikidataJSONPath, String pathLanguagePropsDir) {
  		try {
	  		// init page cache to get page id from page title
	  		/*pageCache = new PagesByTitleCache(null, lang);

	  		// load cache content from existing csv file
	  		List<Path> pageFiles = new ArrayList<Path>();
	  		pageFiles.add(new Path(pathPageCsvPath));
	  		pageCache.loadAll(pageFiles, null); */

	  		// init LMDB
	  		// first temporary DB for storing the page ids in different languages
			File path = new File("/tmp/lmdb-temp-wikidata-id");
			if (!path.exists()) {
				path.mkdir();
				System.out.println("new temp wikidata DB: " + path.toString());
			} else {
				try {
					FileUtils.deleteDirectory(path);
					path.mkdir();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
	    	envFilePath_id = path.toString();
	    	env_id = new Env();
	    	env_id.setMapSize(200 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for > 40 millions
	    	env_id.open(envFilePath_id);
			db_id = env_id.openDatabase();

			// second temporary DB for storing the properties and relations present in wikidata
			path = new File("/tmp/lmdb-temp-wikidata-data");
			if (!path.exists()) {
				path.mkdir();
				System.out.println("new temp wikidata DB: " + path.toString());
			} else {
				try {
					FileUtils.deleteDirectory(path);
					path.mkdir();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
	    	envFilePath_data = path.toString();
	    	env_data = new Env();
	    	env_data.setMapSize(200 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for > 40 millions
	    	env_data.open(envFilePath_data);
			db_data = env_data.openDatabase();
		} catch(Exception e) {
			e.printStackTrace();
		}
  	}

  	public void close() {
 	   	db_id.close();
    	env_id.close();
    	try {
    		File tmpFile = new File(envFilePath_id);
    		if (tmpFile.exists())
	    		FileUtils.deleteDirectory(tmpFile);
    	} catch(Exception e) {
    		e.printStackTrace();
    	}

		db_data.close();
    	env_data.close();
    	try {
    		File tmpFile = new File(envFilePath_data);
    		if (tmpFile.exists())
	    		FileUtils.deleteDirectory(tmpFile);
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
	}

	/**
	 * Map all language specific PageID to Wikidata entity identifier
	 */
	public int processAllProps(String inputPath, String resultPath) {
		int nbAll = 0;
		for(String lang : targetLanguages) {
			String localResultPath = resultPath + "/" + lang + "/wikidata.txt" ;
			int nb = processProps(inputPath+"/"+lang+"wiki-latest-page_props.sql.gz", localResultPath, lang);
			System.out.println(lang + ": " + nb + " page id mapping to Wikidata entities");
			nbAll += nb;
		}
		System.out.println("total of " + nbAll + " mappings with " + targetLanguages.size() + " languages.");
		String localResultPath = resultPath + "/" +"wikidataIds.csv" ;
		writeProp(localResultPath);
		return nbAll;
	}

	/**
	 * Map language specific PageID to Wikidata entity identifier
	 */
	private int processProps(String inputPath, String resultPath, String lang) {
		System.out.println("inputPath: " + inputPath);
		System.out.println("resultPath: " + resultPath);
		
		int nb = 0;
		Writer writer =  null;
		try {
			// open file
			InputStream fileStream = new FileInputStream(inputPath);
			InputStream gzipStream = new GZIPInputStream(fileStream);
			BoundedInputStream boundedInput = new BoundedInputStream(gzipStream);
			BufferedReader reader = new BufferedReader(new InputStreamReader(boundedInput), 8192);

			// output file
			writer = new OutputStreamWriter(new FileOutputStream(resultPath), "UTF-8");

			final String insertString = "INSERT INTO `page_props` VALUES (";
			String line = null;
			Transaction tx = env_id.createWriteTransaction();
			int nbToAdd = 0;
			String previousPageId = null;
			Integer previousPageIdInteger = null;
			try {
    			char[] chars = new char[8192];
    			boolean prelude = true;
    			String remaining = "";
    			Map<String, String> tempToBeAddedMap = new TreeMap<String,String>();
    			for(int len=0; (len = reader.read(chars)) > 0;) {
    				if (nbToAdd == 10000) {
						tx.commit();
						nbToAdd = 0;
						tx = env_id.createWriteTransaction();
						tempToBeAddedMap = new TreeMap<String,String>();
						writer.flush();
					}

    				String piece = String.valueOf(chars);
    				int pos = 0;
    				while (prelude) {
    					String[] chunks = piece.split("\n");
    					for(int p=0; p< chunks.length; p++) {
	    					String chunk = chunks[p];
	    					if (chunk.startsWith(insertString)) {
    							prelude = false;
    							pos += insertString.length();
    							break;
    						}
    						pos += chunk.length()+1;
    					}
    				}

    				if (!prelude) {
    					piece = remaining + piece;
//System.out.println(piece);
    					while (pos != -1) {
    						if (nbToAdd == 10000) {
								tx.commit();
								nbToAdd = 0;
								tx = env_id.createWriteTransaction();
								tempToBeAddedMap = new TreeMap<String,String>();
							}

	    					int posEnd = piece.indexOf("),(", pos+1);
	    					if (posEnd == -1) {
	    						remaining = piece.substring(pos+1, piece.length());
	    					} else {
	    						String propsElement = piece.substring(pos+3, posEnd+2);
	    						String convertedPiece = convertSqlEntry(propsElement);
	    						if (convertedPiece != null) {
//System.out.println(convertedPiece);
	    							int ind = convertedPiece.indexOf("|");
	    							if (ind != -1) {
		    							String pageId = convertedPiece.substring(0, ind);
		    							Integer pageIdInteger = null;

		    							if ( (previousPageId != null) && (pageId.length() < previousPageId.length()) ) {
//System.out.println(previousPageId + " -> " + pageId);

		    								// from time to time there are apparently page id where the first digit 
		    								// is lost, we need re-inject this digit, example 33702 (canis lupus) 
		    								// appears as 3702 incorrectly in the sql mapping 

			    							try {
			    								pageIdInteger = Integer.parseInt(pageId);
			    							} catch (Exception e) {
//System.out.println("parse failure: " + pageId);	
			    							}

			    							String newPageId = previousPageId.charAt(0) + pageId;
			    							Integer newPageIdInteger = null;
			    							try {
			    								newPageIdInteger = Integer.parseInt(newPageId);
			    							} catch (Exception e) {
//System.out.println("parse failure: " + newPageId);	
			    							}

			    							if ( (pageIdInteger != null) && 
			    								 (newPageIdInteger != null) && 
			    								 (newPageIdInteger > pageIdInteger) ) {
//System.out.println(pageIdInteger + " modified as " + newPageIdInteger);			    								
			    								pageId = newPageId;
			    								pageIdInteger = newPageIdInteger;
			    							}
		    							}

		    							convertedPiece = convertedPiece.substring(ind+1,convertedPiece.length());
		    							String wikidataId = convertedPiece;
		    							if (wikidataId.startsWith("Q")) {
			    							// check if this identifier is already indexed or not
			    							String val = null;
			    							try (Transaction txr = env_id.createReadTransaction()) {
			    								val = string(db_id.get(txr, bytes(wikidataId)));
			    							}
			    							if (val == null) {
			    								// also look at the temp stuff not yet added
			    								val = tempToBeAddedMap.get(wikidataId);
			    							}
			    							convertedPiece = lang + "|" + pageId;
			    							if (val != null) {
			    								convertedPiece += "|" + val;
			    							} 
					    					db_id.put(tx, bytes(wikidataId), bytes(convertedPiece));
					    					tempToBeAddedMap.put(wikidataId, convertedPiece);
//System.out.println("adding: " + convertedPiece);

					    					writer.write(pageId+"\t"+wikidataId+"\n");
					    					nbToAdd++;
					    					nb++;
					    				}
					    				previousPageId = pageId;
					    				previousPageIdInteger = pageIdInteger;
		    						}
	    						}
	    					}
	    					pos = posEnd;
	    				}
    				}
    			}
    			tx.commit();
			} finally {
    			reader.close();
    			if (tx != null)
					tx.close();
				writer.flush();
				writer.close();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return nb;
	}

	private void writeProp(String pathResultDir) {
		System.out.println("Outputing id mapping under " + pathResultDir);
		Writer writer =  null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(pathResultDir), "UTF-8");

			// iterate through the DB
			try (Transaction tx = env_id.createReadTransaction()) {
				int nbWritten = 0;
				StringBuilder builder = null;
				try (EntryIterator it = db_id.iterate(tx)) {
					for (Entry next : it.iterable()) {
						byte[] keyBytes = next.getKey();
						byte[] valueBytes = next.getValue();
						if ( (keyBytes != null) && (valueBytes != null) ) {
							String wikidataId = string(keyBytes);
							String value = string(valueBytes);

							//value = value.replace(",", "%2C");
							//value = value.replace("%", "%25");

							int pos = 0;
							boolean first = true;
							while(pos != -1) {
								String langId = null;
								String pageId = null;
								int ind1 = value.indexOf("|", pos+1);
								if (ind1 != -1)
									langId = value.substring(pos, ind1);
								else
									break;
								pos = ind1;
								int ind2 = value.indexOf("|", pos+1);
								if (ind2 != -1) {
									pageId = value.substring(pos+1, ind2);
									pos = ind2+1;
								}
								else {
									pageId = value.substring(pos+1, value.length());
									pos = -1;
								}

								if ( (langId == null) || (pageId == null) )
									break;

								if (!first)
									builder.append(",");
								else {
									first = false;
									builder = new StringBuilder();
									builder
										.append(wikidataId)
										.append(",m{");
								}
								builder
									.append("'")
									.append(langId)
									.append(",'")
									.append(pageId);

							}
							if (builder != null) {
								builder
									.append("}")
									.append("\n");
								writer.write(builder.toString());
								nbWritten++;
							}
							if (nbWritten == 1000) {
								writer.flush();
								nbWritten = 0;
							}
							builder = null;
						}
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.flush();
    				writer.close();
    			} catch(Exception e) {
    				e.printStackTrace();
    			}
    		}
		}
		System.out.println("done");
	}

	private String convertSqlEntry(String element) {
		if (element.indexOf("wikibase_item") == -1)
			return null;
//System.out.println(element);
		StringBuilder builder = new StringBuilder();
		// input: 12,'wikibase_item','Q6199',NULL),
		int nextPos1 = element.indexOf(",");
//System.out.println("nextPos1:" + nextPos1);
		if (nextPos1 == -1)
			return null;
		String pageId = element.substring(0, nextPos1);
//System.out.println("pageId:" + pageId);
		int nextPos2 = element.indexOf(",'", nextPos1+1);
		if (nextPos2 == -1)
			return null;
//System.out.println("nextPos2:" + nextPos2);
		int nextPos3 = element.indexOf(",", nextPos2+1);
		if (nextPos3 == -1)
			return null;
//System.out.println("nextPos3:" + nextPos3);
		String wikidataID = element.substring(nextPos2+2, nextPos3-1);
//System.out.println("wikidataID:" + wikidataID);
		if (wikidataID.trim().length() == 0)
			return null;
		builder
			.append(pageId)
			.append("|")
			.append(wikidataID);
		return builder.toString();
	}

	/*public int process(String inputPath, String resultPath) {
		int nb = 0;
		int ignored = 0;
		BufferedReader reader = null;
		try {
			// open file
			BufferedInputStream bis = new BufferedInputStream(new InputStreamReader(new FileInputStream(inputPath)));
		    CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
		    reader = new BufferedReader(new InputStreamReader(input));

			String line = null;
			int nbToAdd = 0;
			Transaction tx = env_data.createWriteTransaction();
			Map<Integer,String> tempToBeAddedMap = new TreeMap<Integer,String>();
			while ((line = reader.readLine()) != null) {
				//if (nb > 100000)
				//	break;
				if (line.length() == 0) continue;
				if (line.startsWith("[")) continue;
//System.out.println(line);
				if (nbToAdd == 1000) {
					tx.commit();
					nbToAdd = 0;
					tx = env_data.createWriteTransaction();
					tempToBeAddedMap = new TreeMap<Integer,String>();
					System.out.print(".");
					System.out.flush();
				}
                
				JsonNode rootNode = mapper.readTree(line);
				JsonNode idNode = rootNode.findPath("id");
				String itemId = null;
				if ((idNode != null) && (!idNode.isMissingNode())) {
					itemId = idNode.textValue();
				}
                
                if (itemId == null)
                	continue;

				JsonNode claimsNode = rootNode.findPath("claims");
				if ((claimsNode != null) && (!claimsNode.isMissingNode())) {

				}

					db.put(tx, BigInteger.valueOf(itemId).toByteArray(), bytes(propString));
					tempToBeAddedMap.put(new Integer(pageId), propString);
					nbToAdd++;
					nb++;
				}
			}
			tx.commit();
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		System.out.println("\nproperties ignored: " + ignored);
		return nb;
	}*/

	public static void main(String[] args) throws Exception {
System.out.println(args.length + " arguments");
		if (args.length != 3) {
			System.err.println("Invalid arguments: [input_path_to_wikidata_json_file] [path_to_language_page_props.sql_directory] [result_directory]");
		} else {
			ProcessWikiData wikidata = new ProcessWikiData(args[0], args[1]) ;

			long start = System.currentTimeMillis();
			int nbResult = wikidata.processAllProps(args[1], args[2]);
			long end = System.currentTimeMillis();

			System.out.println(nbResult + " props mapping produced in " + (end - start) + " ms");

			/*start = System.currentTimeMillis();
			nbResult = wikidata.process(args[1], args[2]);
			end = System.currentTimeMillis();

			System.out.println(nbResult + " Wikidata statements produced in " + (end - start) + " ms");*/

			wikidata.close();
		}
	}
}