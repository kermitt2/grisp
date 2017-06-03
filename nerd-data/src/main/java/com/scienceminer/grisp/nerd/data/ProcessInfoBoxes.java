package com.scienceminer.grisp.nerd.data;

import java.util.*;    
import java.io.*;    
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.IOUtils;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import org.wikipedia.miner.extract.util.PagesByTitleCache;

import org.apache.hadoop.fs.*;

/**
 * This class processes the DBpedia info boxes files into
 * csv files as expected by NERD. We assume that these files are small
 * and basic enough to avoid the need of Hadoop and distributed process.
 *
 * Beware the crappiness of DBpedia !
 * 
 * @author Patrice Lopez
 *
 */
public class ProcessInfoBoxes {

	private Env env;
  	private Database db;
  	private String envFilePath = null;

  	private PagesByTitleCache pageCache = null;

  	public ProcessInfoBoxes(String lang, String pathPageCsvPath) {
  		try {
	  		// init page cache to get page id from page title
	  		pageCache = new PagesByTitleCache(null, lang);

	  		// load cache content from existing csv file
	  		List<Path> pageFiles = new ArrayList<Path>();
	  		pageFiles.add(new Path(pathPageCsvPath));
	  		pageCache.loadAll(pageFiles, null); 

	  		// init LMDB 
			File path = new File("/tmp/lmdb-temp-infoboxes");
			if (!path.exists()) {
				path.mkdir();
				System.out.println("new temp infobox DB: " + path.toString());
			} else {
				//System.out.println("Existing temp translation DB found: DB will not overwritten");
				try {
					FileUtils.deleteDirectory(path);
					path.mkdir();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
	    	envFilePath = path.toString();

	    	env = new Env();
	    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for > 20 millions
	    	env.open(envFilePath);
			db = env.openDatabase();
		} catch(Exception e) {
			e.printStackTrace();
		}
  	}

  	public void close() {
 	   	db.close();
    	env.close();
    	try {
    		File tmpFile = new File(envFilePath);
    		if (tmpFile.exists())
	    		FileUtils.deleteDirectory(tmpFile);
    	} catch(Exception e) {
    		e.printStackTrace();
    	}
	}
	
	public int process(String inputPath) {
		int nb = 0;
		int ignored = 0;
		BufferedReader reader = null;
		try {
			// open .tql file
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath)));
			String line = null;
			int nbToAdd = 0;
			Transaction tx = env.createWriteTransaction();
			Map<Integer,String> tempToBeAddedMap = new TreeMap<Integer,String>();
			while ((line = reader.readLine()) != null) {
				//if (nb > 100000)
				//	break;
				if (line.length() == 0) continue;
				if (line.startsWith("#")) continue;
//System.out.println(line);
				if (nbToAdd == 1000) {
					tx.commit();
					nbToAdd = 0;
					tx = env.createWriteTransaction();
					tempToBeAddedMap = new TreeMap<Integer,String>();
					System.out.print(".");
					System.out.flush();
				}
                
                List<String> elements = tokenizeTqlEntry(line);
                if (elements == null) {
                	//System.out.println("Error when processing tql line: " + line);
                	ignored++;
                } else {
	                // first element gives the page title
	                String title = getLastPart(elements.get(0));
	                // second element give the property identifier
	                String property = getLastPart(elements.get(1)); 
	               	// third element gives the value of the property, either a string or another page title
	                String value = getLastPart(elements.get(2));
	                if ( (value == null) || (value.trim().length() == 0) ) {
	                	//System.out.println("Error when processing value argument in tql line: " + line);
	                	ignored++;
	                	continue;
	                }
	                // if value is a page title, we replace it with the page id
	                int valueId = -1; 
	                if (elements.get(2).startsWith("<")) {
	                	valueId = pageCache.getArticleId(value);
	                }

	                // fourth element is a weird redundant mess where we can get the wikipedia template name
	                String template = getTemplateName(elements.get(3));

	                // get the page id corresponding to the page title
	                int pageId = pageCache.getArticleId(title);
	                if (pageId == -1) {
	                	//System.out.println("Title not found in page id cache in tql line: " + line);
	                	ignored++;
	                	continue;
	                }

	                StringBuilder propertyString = new StringBuilder();
	                propertyString.append(property).append("|");
	                if (valueId != -1) 
	                	propertyString.append(""+valueId);
	                else
	                	propertyString.append(value);
	                propertyString.append("|").append(template);

	                // we have all the pieces for storing the property
	                // check if the page id is not already in the db
	                String val = null;
					// check if this identifier is already indexed or not
					try (Transaction txr = env.createReadTransaction()) {
						val = string(db.get(txr, BigInteger.valueOf(pageId).toByteArray()));
					}
					if (val == null) {
						// also look at the temp stuff not yet aded
		    			val = tempToBeAddedMap.get(new Integer(pageId));
					}
					if (val != null) {
						propertyString.append("|").append(val);
					}
					String propString = propertyString.toString();
					db.put(tx, BigInteger.valueOf(pageId).toByteArray(), bytes(propString));
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
	}

	private List<String> tokenizeTqlEntry(String line) {
		List<String> result = new ArrayList<String>();
		int ind1 = line.indexOf(" ");
		if (ind1 == -1)
			return null;
		result.add(line.substring(0,ind1));
		int ind2 = line.indexOf(" ", ind1+1);
		if (ind2 == -1)
			return null;
		result.add(line.substring(ind1+1,ind2));

		// third element can be something between <> or a string between two "
		int ind3 = -1;
		if (line.charAt(ind2+1) == '<') {
			ind3 = line.indexOf(" ", ind2+1);
			if (ind3 != -1)
				result.add(line.substring(ind2+1, ind3));
		} else if (line.charAt(ind2+1) == '\"') {
			ind3 = line.indexOf("\"@", ind2+2);
			/*if (ind3 == -1)
				ind3 = line.indexOf("\"^^", ind2+2);*/
			if (ind3 != -1) {
				result.add(line.substring(ind2+1, ind3+1));
			}
		}

		if (ind3 == -1) 
			return null;
		ind3 = line.indexOf("<", ind3+1);
		// last element
		int ind4 = line.indexOf(" ", ind3+1);
		if (ind4 == -1)
			return null;
		result.add(line.substring(ind3, ind4));

		return result;
	}

	private void write(String outputPath) {
		Writer writer =  null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(outputPath), "UTF8");
			// iterate through the DB
			try (Transaction tx = env.createReadTransaction()) {
				int nbWritten = 0;
				StringBuilder builder = null;
				try (EntryIterator it = db.iterate(tx)) {
					for (Entry next : it.iterable()) {
						byte[] keyBytes = next.getKey();
						byte[] valueBytes = next.getValue();
						if ( (keyBytes != null) && (valueBytes != null) ) {
							int pageId = new BigInteger(keyBytes).intValue();
							String value = string(valueBytes);
							writer.write(""+pageId);
							writer.write("|");
							writer.write(value+"\n");
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
	}

	private static String getLastPart(String element) {
//System.out.println(element);
		if (element == null)
			return null;
		if (element.startsWith("<")) {
			// this is a sort of URL
			int ind = element.lastIndexOf("/");
			if (ind == -1) {
				// most likely the value itself here is an url
				return null;
			}
			return element.substring(ind+1, element.length()-1);
		} else if (element.startsWith("\"")) {
			// this is a string value
			int ind = element.lastIndexOf("\"");
			return element.substring(1, ind);
		} else 
			return null;
	}

	private static String getTemplateName(String element) {
		String templatePattern = "template=";
		int ind1 = element.indexOf(templatePattern);
		if (ind1 == -1)
			return null;
		int ind2 = element.indexOf("&", ind1+1);
		if (ind2 == -1)
			return null;
		return element.substring(ind1+templatePattern.length(), ind2);
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println("Invalid arguments: [input_path_to_dbpedia_file] [output_path_to_csv_file] [page.csv_file] [lang_code]");
		}

		ProcessInfoBoxes translate = new ProcessInfoBoxes(args[3], args[2]) ;
				
		long start = System.currentTimeMillis();
		int nbResult = translate.process(args[0]);
		translate.write(args[1]);
		long end = System.currentTimeMillis();

		translate.close();

		System.out.println(nbResult + " properties produced in " + (end - start) + " ms");
	}
}