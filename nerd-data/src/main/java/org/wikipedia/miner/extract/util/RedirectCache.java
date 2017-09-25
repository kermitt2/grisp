package org.wikipedia.miner.extract.util;

//import gnu.trove.map.hash.TIntIntHashMap;
//import gnu.trove.set.TIntSet;
//import gnu.trove.set.hash.TIntHashSet;

import java.util.*;
import java.util.concurrent.*;  

import java.io.*;
//import java.nio.file.*;
import java.util.List;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.mapred.Reporter;
import org.apache.log4j.Logger;

import org.wikipedia.miner.extract.util.ProgressTracker;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import java.math.BigInteger;

public class RedirectCache {

	//private static volatile RedirectCache instance;

	private Env env;
  	private Database db;
  	private String envFilePath = null;
  	private boolean isLoaded = false ;

  	//private Transaction tx = null; 

	/*public static RedirectCache getInstance() throws IOException {
        if (instance == null)
			getNewInstance();
        return instance;
    }*/

    /**
     * Creates a new instance.
     */
	/*private static synchronized void getNewInstance() throws IOException {
		instance = new RedirectCache();
	}*/

    public RedirectCache(String envFilePath0, String lang) throws IOException {
    	this.envFilePath = envFilePath0;
    	if (this.envFilePath != null) {
    	   	this.isLoaded = true;
    	   	Logger.getLogger(RedirectCache.class).info("Loading from existing RedirectCache DB: " + envFilePath);
    	}
    	else {
    		File path = new File("/tmp/lmdb-temp-redirects-"+lang);
    		if (!path.exists()) {
    			path.mkdir();
    			Logger.getLogger(RedirectCache.class).info("new RedirectCache DB: " + path.toString());
    		} else {
    			this.isLoaded = true;
    			Logger.getLogger(RedirectCache.class).info("Existing RedirectCache DB found: DB will not be reloaded from redirect files");
    		}
    	   	//java.nio.file.Path path = java.nio.file.Files.createDirectory(java.nio.file.FileSystems.getDefault().getPath("/tmp/lmdb-temp-redirects"));
    	   	this.envFilePath = path.toString();
    	}

    	//java.nio.file.Path path = Files.createTempDirectory("lmdb-temp-redirects");
    	env = new Env();
    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for ~40 million redirections
    	env.open(envFilePath);
		db = env.openDatabase();

		// prepare read transaction
		//tx = null; 
    }

    public void close() {
    	/*if (tx != null)
    		tx.close();*/
 	   	db.close();
    	env.close();
	}

	/*private static RedirectCache cache ;

	public static RedirectCache get() {
		if (cache == null)
			cache = new RedirectCache() ;

		return cache ;
	}
	
	//private TIntIntHashMap redirectTargetsBySource ;
	private ConcurrentMap<Integer, Integer> redirectTargetsBySource = null;
	
	public RedirectCache() {
		//redirectTargetsBySource = new TIntIntHashMap() ;
		redirectTargetsBySource = new ConcurrentHashMap<Integer, Integer>();
	}*/

	public String getEnvFile() {
		return this.envFilePath;
	}
	
	public boolean isLoaded() {
		return this.isLoaded ;
	}

	private long getBytes(List<Path> paths) {
		long bytes = 0 ;

		for (Path path:paths) {
			File file = new File(path.toString()) ;
			bytes = bytes + file.length() ;
		}

		return bytes ;
	}

	public void load(List<Path> redirectFiles, Reporter reporter) throws IOException {

		if (isLoaded) {
			Logger.getLogger(RedirectCache.class).info("Redirects cache already loaded, skipping reload...");
			return ;
		}
		
		Runtime r = Runtime.getRuntime() ;
		long memBefore = r.totalMemory() ;
		
		ProgressTracker tracker = new ProgressTracker(getBytes(redirectFiles), "Loading redirects", getClass()) ; 
		long bytesRead = 0 ;

		int nbToAdd = 0;
		Transaction txw = env.createWriteTransaction();
		FileSystem fs = FileSystem.get(new Configuration());
		try {
			for (Path redirectFile : redirectFiles) {
				BufferedReader fis = new BufferedReader(new InputStreamReader(fs.open(redirectFile)));
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
						String[] values = line.split(",") ;

						int sourceId = Integer.parseInt(values[0]) ;
						int targetId = Integer.parseInt(values[1]) ;

						//redirectTargetsBySource.put(sourceId, targetId) ;	
						db.put(txw, BigInteger.valueOf(sourceId).toByteArray(), BigInteger.valueOf(targetId).toByteArray());
						nbToAdd++;

						if (reporter != null)
							reporter.progress() ;
					} catch (Exception e) {
						Logger.getLogger(RedirectCache.class).error("Caught exception while gathering redirect from '" + line + "' in '" + redirectFile + "'", e);
					}
				}

				fis.close();
			}
		} catch(Exception e) {
			Logger.getLogger(RedirectCache.class).error("Caught exception while gathering page", e) ;
		} finally {
			if (txw != null)	
				txw.close();
		}

		long memAfter = r.totalMemory() ;
		Logger.getLogger(getClass()).info("Memory Used: " + (memAfter - memBefore) / (1024*1024) + "Mb") ;
		
		isLoaded = true ;
	}

	public int getTargetId(int sourceId) {
		//if (!redirectTargetsBySource.contains(sourceId))
			//return null ;

		//return redirectTargetsBySource.get(sourceId) ;
		//return db.get(sourceId) ;
		//Transaction tx = null; 
		byte[] res = null;
		try (Transaction tx = env.createReadTransaction()) {
			try {
				res = db.get(tx, BigInteger.valueOf(sourceId).toByteArray());
			} catch(LMDBException e) {
				Logger.getLogger(RedirectCache.class).error("Caught LMDB exception while getTargetId: " + sourceId, e) ;
			}
		} /*catch(Exception e) {
			Logger.getLogger(RedirectCache.class).error("Caught exception while getTargetId: " + sourceId, e) ;
			if (tx != null)
				tx.close();
			tx = null;
		} finally {
			if (tx != null)
				tx.reset();	
				//tx.close();
		}*/

		if (res == null)
			return -1;
		else 
			return new BigInteger(res).intValue();
	}

	public int getTargetId(String targetTitle, PagesByTitleCache articlesById) throws IOException {

		//Integer currId = PagesByTitleCache.getArticlesCache().getPageId(targetTitle) ;
		int currId = articlesById.getPageId(targetTitle);
		if (currId == -1)
			return -1 ;

		List<Integer> targetsSeen = new ArrayList<Integer>() ;

		while (currId != -1) {

			//if there is no entry for this id, then this isn't a redirect, so no need to continue
			//if (!redirectTargetsBySource.containsKey(currId))
			if (getTargetId(currId) == -1)	
				return currId ;

			//otherwise we need to resolve the redirect
			if (targetsSeen.contains(currId)) {
				// seen this redirect before, so we have entered a loop
				return -1;
			} else {
				//recurse to the next id
				targetsSeen.add(currId) ;
				//currId = redirectTargetsBySource.get(currId);
				currId = getTargetId(currId);
			}
		}

		return currId ;
	}


}
