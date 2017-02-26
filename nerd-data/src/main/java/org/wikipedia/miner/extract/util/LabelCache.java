package org.wikipedia.miner.extract.util;

import java.io.*;
//import java.nio.file.*;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.record.CsvRecordInput;
import org.apache.log4j.Logger;

import org.wikipedia.miner.util.ProgressTracker;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

public class LabelCache {

	private Env env;
  	private Database db;
  	private String envFilePath = null;
  	private boolean isLoaded = false ;

    public LabelCache(String envFilePath0, String lang) throws IOException {
    	this.envFilePath = envFilePath0;
    	if (envFilePath != null) {
    	   	this.isLoaded = true;
    	   	Logger.getLogger(LabelCache.class).info("Loading from existing LabelCache DB: " + envFilePath);
    	}
    	else {
    		File path = new File("/tmp/lmdb-temp-labels-"+lang);
    		if (!path.exists()) {
    			path.mkdir();
    			Logger.getLogger(LabelCache.class).info("new LabelCache DB: " + path.toString());
    		} else {
    			this.isLoaded = true;
    			Logger.getLogger(LabelCache.class).info("Existing LabelCache DB found: DB will not be reloaded from label files");
    		}
    	   	envFilePath = path.toString();
    	}

    	//java.nio.file.Path path = Files.createTempDirectory("lmdb-temp-labels");
    	env = new Env();
    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for ~8 million labels
    	env.open(envFilePath);
		db = env.openDatabase();
    }

    public void close() {
 	   	db.close();
    	env.close();
	}

	/*public static LabelCache get() throws IOException {
		if (cache == null)
			cache = new LabelCache() ;

		return cache ;
	}*/

	//Set<String> labelVocabulary ;

	/*public LabelCache() throws IOException {
		
		DB db = DBMaker.newAppendFileDB(File.createTempFile("mapdb-temp", "labels"))
	       .deleteFilesAfterClose().closeOnJvmShutdown().cacheHardRefEnable().make();
		
		labelVocabulary = db.getHashSet("labels");
	}*/
	
	
	public String getEnvFile() {
		return this.envFilePath;
	}

	public boolean isLoaded() {
		return this.isLoaded ;
	}

	public boolean isKnown(String label) {
		/*if (tx == null)
			tx = env.createReadTransaction(); 
		else
			tx.renew();*/

		byte[] res = null;
		try (Transaction tx = env.createReadTransaction()) {
			try {
				res = db.get(tx, bytes(label));
			} catch(LMDBException e) {
				Logger.getLogger(LabelCache.class).error("Caught exception while isKnown label: " + label, e);
			}
		} 

		if (res != null)
			return true;
		else 
			return false;
	}

	private long getBytes(List<Path> paths) {
		long bytes = 0 ;
		for (Path path:paths) {
			File file = new File(path.toString()) ;
			bytes = bytes + file.length() ;
		}

		return bytes ;
	}

	public void load(List<Path> paths, Reporter reporter) throws IOException {
		if (isLoaded) {
			Logger.getLogger(LabelCache.class).info("Label cache already loaded, skipping reload...");
			return;
		}
		
		Runtime r = Runtime.getRuntime() ;
		long memBefore = r.totalMemory() ;
		
		ProgressTracker tracker = new ProgressTracker(getBytes(paths), "Loading labels", getClass()) ;
		long bytesRead = 0 ;

		int nbToAdd = 0;
		Transaction txw = env.createWriteTransaction();
		FileSystem fs = FileSystem.get(new Configuration());
		try {
			for (Path path:paths) {
				BufferedReader fis = new BufferedReader(new InputStreamReader(fs.open(path)));
				String line = null;

				while ((line = fis.readLine()) != null) {
					if (nbToAdd == 10000) {
						txw.commit();
						nbToAdd = 0;
						txw = env.createWriteTransaction();
					}

					bytesRead = bytesRead + line.length() + 1 ;
					tracker.update(bytesRead) ;
					try {
						CsvRecordInput cri = new CsvRecordInput(new ByteArrayInputStream(line.getBytes("UTF8"))) ;
						String labelText = cri.readString("labelText");
						if (labelText.length() < 500) {
							db.put(txw, bytes(labelText), bytes("1"));
							nbToAdd++;
						}
					} catch (Exception e) {
						Logger.getLogger(getClass()).error("Caught exception while gathering label from '" + line + "' in '" + path + "'", e);
					}

					if (reporter != null)
						reporter.progress();
				}
				fis.close();
			}
			txw.commit();
		} catch(Exception e) {
			Logger.getLogger(Util.class).error("Caught exception while gathering page", e) ;
		} finally {
			if (txw != null)
				txw.close();
		}

		long memAfter = r.totalMemory() ;
		Logger.getLogger(getClass()).info("Memory Used: " + (memAfter - memBefore) / (1024*1024) + "Mb") ;
		
		isLoaded = true ;	
	}
}
