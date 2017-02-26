package org.wikipedia.miner.extract.util;

//import java.nio.file.*;
import java.io.*;
import java.util.*;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.record.CsvRecordInput;
import org.apache.log4j.Logger;
import org.wikipedia.miner.db.struct.DbPage;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.ProgressTracker;

//import gnu.trove.map.hash.TObjectIntHashMap;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import java.math.BigInteger;

public class PagesByTitleCache {
	private Env env = null;
	private String envFilePath = null;
  	private Database dbArticles = null;
  	private Database dbCategories = null;

  	private List<PageType> acceptablePageTypesArticles = new ArrayList<PageType>() ;
	private List<PageType> acceptablePageTypesCategories = new ArrayList<PageType>() ;

	private boolean isLoaded = false;

    public PagesByTitleCache(String envFilePath0, String lang) throws IOException {
    	envFilePath = envFilePath0;
    	if (envFilePath != null) {
    		Logger.getLogger(PagesByTitleCache.class).info("Loading from existing PagesByTitleCache DB: " + envFilePath);
    		isLoaded = true;
    	}
    	else {
    		File path = new File("/tmp/lmdb-temp-titles-"+lang);
    		if (!path.exists()) {
	    	   	//java.nio.file.Path path = java.nio.file.Files.createDirectory(java.nio.file.FileSystems.getDefault().getPath("/tmp/lmdb-temp-titles"));
	    	   	path.mkdir();
	    	   	isLoaded = false;
	    	   	Logger.getLogger(PagesByTitleCache.class).info("new PagesByTitleCache DB: " + path.toString());
	    	} else {
	    		isLoaded = true;
    			Logger.getLogger(PagesByTitleCache.class).info("Existing PagesByTitleCache DB found: DB will not be reloaded from page files");
	    	}
    	   	envFilePath = path.toString();
    	}
    	env = new Env();
    	env.setMaxDbs(2);
    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for ~32 million titles
    	env.open(envFilePath);
		dbArticles = env.openDatabase("articles");
		dbCategories = env.openDatabase("categories");

		acceptablePageTypesArticles.add(PageType.article);
		acceptablePageTypesArticles.add(PageType.redirect);
		acceptablePageTypesArticles.add(PageType.disambiguation);

		acceptablePageTypesCategories.add(PageType.category);
		//tx = null;
    }

    public void close() {
    	/*if (tx != null)
    		tx.close();*/
 	   	dbArticles.close();
 	   	dbCategories.close();
    	env.close();
	}
	
	public String getEnvFile() {
		return this.envFilePath;
	}

	public boolean isLoaded() {
		return this.isLoaded;
	}

	private long getBytes(List<Path> paths) {
		long bytes = 0 ;
		for (Path path:paths) {
			File file = new File(path.toString()) ;
			bytes = bytes + file.length() ;
		}
		
		return bytes ;
	}

	public void loadAll(List<Path> pageFiles, Reporter reporter) throws IOException { 
		if (isLoaded) {
			Logger.getLogger(PagesByTitleCache.class).info("Page title cache already loaded, skipping reload...");
			return;
		}

		Runtime r = Runtime.getRuntime() ;
		long memBefore = r.totalMemory() ;
		
		ProgressTracker tracker = new ProgressTracker(getBytes(pageFiles), "Loading page files", getClass()) ;
		long bytesRead = 0;
		int nbToAdd = 0;
		Transaction txw = env.createWriteTransaction();
		FileSystem fs = FileSystem.get(new Configuration());
		try {
			for (Path pageFile:pageFiles) {
				BufferedReader fis = new BufferedReader(new InputStreamReader(fs.open(pageFile)));
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
						CsvRecordInput cri = new CsvRecordInput(new ByteArrayInputStream((line + "\n").getBytes("UTF-8"))) ;
		
						int id = cri.readInt("id");
						DbPage page = new DbPage();
						page.deserialize(cri);
		
						String title = page.getTitle();
						PageType type = PageType.values()[page.getType()];
						
						if ( acceptablePageTypesArticles.contains(type) ) {
							dbArticles.put(txw, bytes(Util.normaliseTitle(title)), BigInteger.valueOf(id).toByteArray());
							nbToAdd++;
						} else if ( acceptablePageTypesCategories.contains(type) ) {
							dbCategories.put(txw, bytes(Util.normaliseTitle(title)), BigInteger.valueOf(id).toByteArray());
							nbToAdd++;
						} 
						
						if (reporter != null)
							reporter.progress();
						
					} catch (Exception e) {
						Logger.getLogger(Util.class).error("Caught exception while gathering page from '" + line + "' in '" + pageFile + "'", e) ;
					}
				}
				
				fis.close() ;
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
		Logger.getLogger(getClass()).info("Number entries in dbArticle: " + getDatabaseSize(dbArticles));
		Logger.getLogger(getClass()).info("Number entries in dbCategories: " + getDatabaseSize(dbCategories));

		isLoaded = true;
	}
	
	public int getArticleId(String title) {
		String nTitle = Util.normaliseTitle(title);
		byte[] res = null;
		try (Transaction tx = env.createReadTransaction()) {
			try {
				res = dbArticles.get(tx, bytes(nTitle));
			} catch(LMDBException e) {
				Logger.getLogger(RedirectCache.class).error("Caught LMDB exception with getPageId: " + title, e) ;
			}
		}

		if (res == null)
			return -1;
		else 
			return new BigInteger(res).intValue();
	}

	public int getCategoryId(String title) {
		String nTitle = Util.normaliseTitle(title);
		byte[] res = null;
		try (Transaction tx = env.createReadTransaction()) {
			try {
				res = dbCategories.get(tx, bytes(nTitle));
			} catch(LMDBException e) {
				Logger.getLogger(RedirectCache.class).error("Caught LMDB exception with getPageId: " + title, e) ;
			}
		}

		if (res == null) {
			return -1;
		} else 
			return new BigInteger(res).intValue();
	}

	public int getPageId(String title) {
		String nTitle = Util.normaliseTitle(title);
		byte[] res = null;
		try (Transaction tx = env.createReadTransaction()) {
			try {
				res = dbArticles.get(tx, bytes(nTitle));
			} catch(LMDBException e) {
				Logger.getLogger(RedirectCache.class).error("Caught LMDB exception with getPageId: " + title, e) ;
			}
		}
		if (res == null) {
			try (Transaction tx = env.createReadTransaction()) {
				try {
					res = dbCategories.get(tx, bytes(nTitle));
				} catch(LMDBException e) {
					Logger.getLogger(RedirectCache.class).error("Caught LMDB exception with getPageId: " + title, e) ;
				}
			}
		}
		if (res == null) {
			return -1;
		} else 
			return new BigInteger(res).intValue();
	}
	
	private static long getDatabaseSize(Database db) {
		//return getDatabase(true).count();

		Stat statistics = db.stat();
		return statistics.ms_entries;
	}

	public long getArticleDatabaseSize() {
		return  getDatabaseSize(dbArticles);
	}

	public long getCategoryDatabaseSize() {
		return  getDatabaseSize(dbCategories);
	}
}
