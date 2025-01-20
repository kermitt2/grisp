package com.scienceminer.grisp.nerd.data;

import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.fusesource.lmdbjni.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.fusesource.lmdbjni.Constants.bytes;
import static org.fusesource.lmdbjni.Constants.string;

/**
 * This class processes the latest Wikipedia cross-language files (.sql) into
 * csv files as expected by entity-fishing.
 * 
 *
 * @author Patrice Lopez
 *
 */
public class ProcessTranslation {

	private Env env;
  	private Database db;
  	private String envFilePath = null;

  	// this is the list of languages we consider for target translations, we will ignore the other
  	// languages
  	private static final List<String> targetLanguages =
  		Arrays.asList("en", "fr", "de", "it", "es", "ar", "zh", "jp", "ru", "pt", "fa", "bn", "hi", "sv", "uk", "nl");

  	public ProcessTranslation(String lang) {
  		// init LMDB - the default usage of LMDB will ensure that the entries in the resulting 
  		// translations.csv file will be sorted by the page id
		File path = new File("/tmp/lmdb-temp-translations"+lang);
		if (!path.exists()) {
			path.mkdir();
			System.out.println("new temp translation DB for language " + lang + " : " + path.toString());
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
    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for > 20 million translation
    	env.open(envFilePath);
		db = env.openDatabase();
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
	
	public int process(String inputSqlPath) {
		int nb = 0;
		InputStream inputStream = null;
		try {
			// open file
			inputStream = new FileInputStream(inputSqlPath);
			if (inputSqlPath.endsWith(".gz")) {
				inputStream = new GZIPInputStream(inputStream);
			}			
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream), 8192);

			final String insertString = "INSERT INTO `langlinks` VALUES ("; 
			String line = null;
			Transaction tx = env.createWriteTransaction();
			int nbToAdd = 0;
			try {
    			char[] chars = new char[8192];
    			boolean prelude = true;
    			String remaining = null;
    			Map<String, String> tempToBeAddedMap = new TreeMap<String,String>();
    			for(int len; (len = reader.read(chars)) > 0;) {
    				if (nbToAdd == 10000) {
						tx.commit();
						nbToAdd = 0;
						tx = env.createWriteTransaction();
						tempToBeAddedMap = new TreeMap<String,String>();
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
    					while (pos != -1) {
    						if (nbToAdd == 10000) {
								tx.commit();
								nbToAdd = 0;
								tx = env.createWriteTransaction();
								tempToBeAddedMap = new TreeMap<String,String>();
							}

	    					int posEnd = piece.indexOf("'),(", pos+1);
	    					if (posEnd == -1) {
	    						remaining = piece.substring(pos+1, piece.length());
	    					} else {
	    						String translationElement = piece.substring(pos+3, posEnd+2);
	    						String convertedPiece = convertSqlEntry(translationElement);
	    						if (convertedPiece != null) {
	    							int ind = convertedPiece.indexOf("|");
	    							if (ind != -1) {
		    							String keyId = convertedPiece.substring(0, ind);
		    							convertedPiece = convertedPiece.substring(ind+1,convertedPiece.length());
		    							String val = null;
		    							// check if this identifier is already indexed or not
		    							try (Transaction txr = env.createReadTransaction()) {
		    								val = string(db.get(txr, bytes(keyId)));
		    							} /*catch(Excepion e) {
		    								e.printStackTrace();
		    							}*/
		    							if (val == null) {
		    								// also look at the temp stuff not yet aded
		    								val = tempToBeAddedMap.get(keyId);
		    							}
		    							if (val != null) {
		    								int ind2 = val.indexOf("|");
		    								if ( (ind2 != -1) && (val.length()>ind2) ) {
		    									convertedPiece += "|" + val;
		    								}
		    							}		    						
				    					db.put(tx, bytes(keyId), bytes(convertedPiece));
				    					tempToBeAddedMap.put(keyId, convertedPiece);
				    					nbToAdd++;
				    					nb++;
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
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
		return nb;
	}

	public static String convertSqlEntry(String translationElement) {
		StringBuilder builder = new StringBuilder();
		// input: (746,'ab','Азербаиџьан')
		int startPos = 1;
		if (!translationElement.startsWith("(")) {
			// re-adjust start in case of shift in start pos
			startPos = 0;
		}
		int nextPos1 = translationElement.indexOf(",");
		if (nextPos1 == -1)
			return null;
		String pageId = translationElement.substring(startPos, nextPos1);
		int nextPos2 = translationElement.indexOf(",'", nextPos1+1);
		if (nextPos2 == -1)
			return null;
		String langId = translationElement.substring(nextPos1+2, nextPos2-1);
		int nextPos3 = translationElement.indexOf("')", nextPos2);
		if (nextPos3 == -1)
			return null;
		if (!targetLanguages.contains(langId))
			return null;
		String translatedTitle = translationElement.substring(nextPos2+2, nextPos3);
		if (translatedTitle.trim().length() == 0)
			return null;
		builder
			.append(pageId)
			.append("|")
			.append(langId)
			.append("|")
			.append(translatedTitle);
		return builder.toString();
	}

	private void writeTranslationsCsv(String outputCsvPath) {
		System.out.println("Outputting translations under " + outputCsvPath);
		Writer writer =  null;
		try {
			writer = new OutputStreamWriter(Files.newOutputStream(Paths.get(outputCsvPath)), StandardCharsets.UTF_8);

			// format: 5886651,m{'en,'Category:Dames Grand Cross of the Order of St John}
			// 5886606,m{'pt,'Museu da Terra de Miranda}

			// iterate through the DB
			try (Transaction tx = env.createReadTransaction()) {
				int nbWritten = 0;
				StringBuilder builder = null;
				try (EntryIterator it = db.iterate(tx)) {
					for (Entry next : it.iterable()) {
						byte[] keyBytes = next.getKey();
						byte[] valueBytes = next.getValue();
						if ( (keyBytes != null) && (valueBytes != null) ) {
							String pageId = string(keyBytes);
							String value = string(valueBytes);
							
							value = value.replace(",", "%2C");
							value = value.replace("%", "%25");

							int pos = 0;
							boolean first = true;
							while(pos != -1) {
								String langId = null;
								String translatedTitle = null;
								int ind1 = value.indexOf("|", pos+1);
								if (ind1 != -1)
									langId = value.substring(pos, ind1);
								else 
									break;
								pos = ind1;
								int ind2 = value.indexOf("|", pos+1);
								if (ind2 != -1) {
									translatedTitle = value.substring(pos+1, ind2);
									pos = ind2+1;
								}
								else {
									translatedTitle = value.substring(pos+1, value.length());
									pos = -1;
								}

								if ( (langId == null) || (translatedTitle == null) )
									break;

								if (!first)
									builder.append(",");
								else {
									first = false;
									builder = new StringBuilder();
									builder
										.append(pageId)
										.append(",m{");
								}
								builder	
									.append("'")
									.append(langId)
									.append(",'")
									.append(translatedTitle);
								
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

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println("Invalid arguments: [lang] [input_path_to_sql_translation_file] [output_path_to_csv_translation_file]");
		}

		String lang = args[0];
		String sqlTranslationFilePath = args[1];
		String outputPath = args[2];

		// TBD: sanity check on arguments

		ProcessTranslation translate = new ProcessTranslation(lang);
				
		long start = System.currentTimeMillis();
		int nbResult = translate.process(sqlTranslationFilePath);
		translate.writeTranslationsCsv(outputPath+"/translations.csv");
		long end = System.currentTimeMillis();

		translate.close();

		System.out.println(nbResult + " translations produced in " + (end - start) + " ms");
	}
}