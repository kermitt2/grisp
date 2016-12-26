package com.scienceminer.grisp.nerd.data;

import java.util.*;    
import java.io.*;    

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.IOUtils;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

/**
 * This class processes the latest Wikipedia cross-language files (.sql) into
 * csv files as expected by NERD. We assume that these files are small
 * and basic enough to avoid the need of Hadoop and distributed process.
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
  	private List<String> targetLanguages = Arrays.asList("en","fr", "de");

  	public ProcessTranslation() {
  		// init LMDB 
		File path = new File("/tmp/lmdb-temp-translations");
		if (!path.exists()) {
			path.mkdir();
			System.out.println("new temp translation DB: " + path.toString());
		} else {
			System.out.println("Existing temp translation DB found: DB will not overwritten");
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
	}
	
	public int process(String inputSqlPath, String outputCsvPath) {
		int nb = 0;
		try {
			// open file
			BoundedInputStream boundedInput = new BoundedInputStream(new FileInputStream(inputSqlPath));
			BufferedReader reader = new BufferedReader(new InputStreamReader(boundedInput), 8192);

            Writer writer = new OutputStreamWriter(new FileOutputStream(outputCsvPath), "UTF8");

			final String insertString = "INSERT INTO `langlinks` VALUES "; 
			String line = null;
			try {
    			char[] chars = new char[8192];
    			boolean prelude = true;
    			String remaining = "";
    			for(int len; (len = reader.read(chars)) > 0;) {
    				String piece = String.valueOf(chars);
    				int pos = 0;
    				while (prelude) {
    					String[] chunks = piece.split("\n");
    					for(int p=0; p< chunks.length; p++) {
	    					String chunk = chunks[p];
	    					if (chunk.startsWith(insertString)) {
    							prelude = true;
    							pos += insertString.length();
    						}
    					}
    				} 

    				if (!prelude) {
    					piece = remaining + piece;
    					while (pos != -1) {
	    					int posEnd = piece.indexOf("'),(", pos+1);
	    					if (posEnd == -1) {
	    						remaining = piece.substring(pos+1, piece.length());
	    					} else {
	    						String translationElement = piece.substring(pos+1, posEnd);
	    						// input: (746,'ab','Азербаиџьан')
	    						int nextPos1 = translationElement.indexOf(",");
	    						String pageId = translationElement.substring(1, nextPos1);
	    						int nextPos2 = translationElement.indexOf(",", nextPos1+1);
	    						String langId = translationElement.substring(nextPos1+1, nextPos2);

	    						String translatedTitle = "";

	    						// format: 5886651,m{'en,'Category:Dames Grand Cross of the Order of St John}
	    						// 5886606,m{'pt,'Museu da Terra de Miranda}
	    						// normally sorted!

	    						writer.write("");
	    					}
	    					pos = posEnd;
	    				}
    				}
    				writer.flush();
    			}
			} finally {
    			reader.close();
    			writer.close();
			}

			/*while ((line = reader.readLine()) != null) {
				if (line.length() > 100000)
					continue;
				bytesRead += line.length() + 1 ;
				pt.update(bytesRead);

				writer.write(line) ;
				writer.newLine() ;
			}*/



			// input is (8274899,'af','Alconaba') page_id, lang, translated_title
		} catch(Exception e) {
			e.printStackTrace();
		}
		return nb;
	}


	private void writeTranslationsCsv() {
		// iterate through the DB

	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Invalid arguments: [input_path_to_sql_translation_file] [output_path_to_csv_translation_file]");
		}

		ProcessTranslation translate = new ProcessTranslation() ;
				
		long start = System.currentTimeMillis();
		int nbResult = translate.process(args[0], args[1]);
		long end = System.currentTimeMillis();

		translate.close();

		System.out.println(nbResult + " translations in " + (end - start) + " ms");
	}
}