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
//import org.mapdb.DB;
//import org.mapdb.DBMaker;
import org.wikipedia.miner.db.struct.DbPage;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.ProgressTracker;

//import gnu.trove.map.hash.TObjectIntHashMap;

import org.fusesource.lmdbjni.*;
import static org.fusesource.lmdbjni.Constants.*;

import java.math.BigInteger;

public class PagesByTitleCache {
	//private static volatile PagesByTitleCache instance;

	private Env env = null;
	private String envFilePath = null;
  	private Database dbPages = null;

  	private List<PageType> acceptablePageTypesArticles = new ArrayList<PageType>() ;
	private List<PageType> acceptablePageTypesCategories = new ArrayList<PageType>() ;

	private boolean isLoaded = false;
	
	//private Transaction tx = null;

	/*public static PagesByTitleCache getInstance() throws IOException {
        if (instance == null)
			getNewInstance();
        return instance;
    }*/

    /**
     * Creates a new instance.
     */
	/*private static synchronized void getNewInstance() throws IOException {
		instance = new PagesByTitleCache();
	}*/

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
    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for ~32 million titles
    	env.open(envFilePath);
		dbPages = env.openDatabase();
		//dbCategories = env.openDatabase();

		acceptablePageTypesArticles.add(PageType.article);
		acceptablePageTypesArticles.add(PageType.redirect);
		acceptablePageTypesArticles.add(PageType.disambiguation);

		acceptablePageTypesCategories.add(PageType.category);
		//tx = null;
    }

    public void close() {
    	/*if (tx != null)
    		tx.close();*/
 	   	dbPages.close();
    	env.close();
	}

	/*private static PagesByTitleCache articlesCache ;
	private static PagesByTitleCache categoriesCache ;
	
	private Map<String,Integer> pageIdsByTitle  ;
	private Set<PageType> acceptablePageTypes = new HashSet<PageType>() ;
	
	public PagesByTitleCache() throws IOException {
		DB db = DBMaker.newAppendFileDB(File.createTempFile("mapdb-temp", "titles"))
			       .deleteFilesAfterClose().closeOnJvmShutdown().cacheHardRefEnable().make();

		pageIdsByTitle = db.getHashMap("titles") ;
		
	}*/
	
	
	/*public static PagesByTitleCache getArticlesCache() throws IOException {
		
		if (articlesCache == null) {
			articlesCache = new PagesByTitleCache() ;
			
			articlesCache.acceptablePageTypes.add(PageType.article) ;
			articlesCache.acceptablePageTypes.add(PageType.redirect) ;
			articlesCache.acceptablePageTypes.add(PageType.disambiguation) ;
		}
		
		return articlesCache ;
	}
	
	public static PagesByTitleCache getCategoriesCache() throws IOException {
		
		if (categoriesCache == null) {
			categoriesCache = new PagesByTitleCache() ;
			
			categoriesCache.acceptablePageTypes.add(PageType.category) ;
		}
		
		return categoriesCache ;
	}*/
	
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
	
	/*public void loadArticles(List<Path> pageFiles, Reporter reporter) throws IOException {
		load(pageFiles, reporter, 0);
	}

	public void loadCategories(List<Path> pageFiles, Reporter reporter) throws IOException { 
		load(pageFiles, reporter, 1);
	}*/

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
		
						int id = cri.readInt("id") ;
						DbPage page = new DbPage() ;
						page.deserialize(cri) ;
		
						String title = page.getTitle() ;
						PageType type = PageType.values()[page.getType()] ;
						
						if ( acceptablePageTypesArticles.contains(type) ||  acceptablePageTypesCategories.contains(type) ) {
							dbPages.put(txw, bytes(Util.normaliseTitle(title)), BigInteger.valueOf(id).toByteArray());
							nbToAdd++;
						} 
						
						if (reporter != null)
							reporter.progress() ;
						
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

		isLoaded = true;
	}
	
	/*public int getArticleId(String title) {
		String nTitle = Util.normaliseTitle(title) ;
		byte[] res = null;
		try {
			if (tx == null)
				tx = env.createReadTransaction(); 
			else
				tx.renew();
			res = dbPages.get(tx, bytes(nTitle));
		} catch(Exception e) {
			Logger.getLogger(PagesByTitleCache.class).error("Caught exception while getArticleId: " + title, e) ;
			if (tx != null)
				tx.close();
			tx = null;
		} finally {
			if (tx != null)
				tx.reset();	
				//tx.close();
		}

		if (res == null)
			return -1;
		else 
			return new BigInteger(res).intValue();
	}

	public int getCategoryId(String title) {
		String nTitle = Util.normaliseTitle(title) ;
		byte[] res = null;
		try {
			if (tx == null)
				tx = env.createReadTransaction(); 
			else
				tx.renew();
			res = dbPages.get(tx, bytes(nTitle));
		} catch(Exception e) {
			Logger.getLogger(PagesByTitleCache.class).error("Caught exception while getCategoryId: " + title, e) ;
			if (tx != null)	
				tx.close();
			tx = null;
		} finally {
			if (tx != null)
				tx.reset();	
				//tx.close();
		}

		if (res == null)
			return -1;
		else 
			return new BigInteger(res).intValue();
	}*/

	public int getPageId(String title) {
		String nTitle = Util.normaliseTitle(title);
		byte[] res = null;
		try (Transaction tx = env.createReadTransaction()) {
			try {
				res = dbPages.get(tx, bytes(nTitle));
			} catch(LMDBException e) {
				Logger.getLogger(RedirectCache.class).error("Caught LMDB exception while getPageId: " + title, e) ;
			}
		}
		
		/*try {
			if (tx == null)
				tx = env.createReadTransaction(); 
			else
				tx.renew();
			res = dbPages.get(tx, bytes(nTitle));
		} catch(Exception e) {
			Logger.getLogger(PagesByTitleCache.class).error("Caught exception while getPageId: " + title, e) ;
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
	
}
