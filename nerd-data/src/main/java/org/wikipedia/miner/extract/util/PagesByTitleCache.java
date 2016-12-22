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
  	private Database dbArticles = null;
  	private Database dbCategories = null;

  	private List<PageType> acceptablePageTypesArticles = new ArrayList<PageType>() ;
	private List<PageType> acceptablePageTypesCategories = new ArrayList<PageType>() ;

	private boolean isLoadedArticles = false;
	private boolean isLoadedCategories = false;
	

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

    public PagesByTitleCache(String envFilePath0) throws IOException {
    	envFilePath = envFilePath0;
    	if (envFilePath != null) {
    		Logger.getLogger(PagesByTitleCache.class).info("Loading from existing PagesByTitleCache DB: " + envFilePath);
    		isLoadedArticles = true;
    		isLoadedCategories = true;
    	}
    	else {
    		File path = new File("/tmp/lmdb-temp-titles");
    		if (!path.exists()) {
	    	   	//java.nio.file.Path path = java.nio.file.Files.createDirectory(java.nio.file.FileSystems.getDefault().getPath("/tmp/lmdb-temp-titles"));
	    	   	path.mkdir();
	    	   	Logger.getLogger(PagesByTitleCache.class).info("new PagesByTitleCache DB: " + path.toString());
	    	} else {
	    		isLoadedArticles = true;
    			isLoadedCategories = true;
    			Logger.getLogger(PagesByTitleCache.class).info("Existing PagesByTitleCache DB found: DB will not be reloaded from page files");
	    	}
    	   	envFilePath = path.toString();
    	}
    	env = new Env();
    	env.setMapSize(100 * 1024 * 1024, ByteUnit.KIBIBYTES); // space for ~32 million titles
    	env.open(envFilePath);
		dbArticles = env.openDatabase();
		dbCategories = env.openDatabase();

		acceptablePageTypesArticles.add(PageType.article);
		acceptablePageTypesArticles.add(PageType.redirect);
		acceptablePageTypesArticles.add(PageType.disambiguation);

		acceptablePageTypesCategories.add(PageType.category);
    }

    public void close() {
 	   	dbArticles.close();
 	   	dbCategories.close();
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

	public boolean isLoadedArticles() {
		return this.isLoadedArticles;
	}
	
	public boolean isLoadedCategories() {
		return this.isLoadedCategories;
	}

	private long getBytes(List<Path> paths) {
		long bytes = 0 ;
		for (Path path:paths) {
			File file = new File(path.toString()) ;
			bytes = bytes + file.length() ;
		}
		
		return bytes ;
	}
	
	public void loadArticles(List<Path> pageFiles, Reporter reporter) throws IOException {
		load(pageFiles, reporter, 0);
	}

	public void loadCategories(List<Path> pageFiles, Reporter reporter) throws IOException { 
		load(pageFiles, reporter, 1);
	}

	public void loadAll(List<Path> pageFiles, Reporter reporter) throws IOException { 
		load(pageFiles, reporter, 2);
	}

	public void load(List<Path> pageFiles, Reporter reporter, int pageType) throws IOException {
		if (isLoadedArticles && (pageType == 0)) {
			Logger.getLogger(PagesByTitleCache.class).info("Article page title cache already loaded, skipping reload...");
			return;
		}

		if (isLoadedCategories && (pageType == 1)) {
			Logger.getLogger(PagesByTitleCache.class).info("Category page title cache already loaded, skipping reload...");
			return;
		}
		
		if (isLoadedArticles && isLoadedCategories && (pageType == 2)) {
			Logger.getLogger(PagesByTitleCache.class).info("Page title cache already loaded, skipping reload...");
			return;
		}

		Runtime r = Runtime.getRuntime() ;
		long memBefore = r.totalMemory() ;
		
		ProgressTracker tracker = new ProgressTracker(getBytes(pageFiles), "Loading page files", getClass()) ;
		long bytesRead = 0;
		int nbToAdd = 0;
		Transaction tx = env.createWriteTransaction();
		FileSystem fs = FileSystem.get(new Configuration());
		try {
			for (Path pageFile:pageFiles) {
				BufferedReader fis = new BufferedReader(new InputStreamReader(fs.open(pageFile)));
				String line = null;
			
				while ((line = fis.readLine()) != null) {
					if (nbToAdd == 10000) {
						tx.commit();
						nbToAdd = 0;
						tx = env.createWriteTransaction();
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
						
						if ( ((pageType == 0) || (pageType ==2)) && (acceptablePageTypesArticles.contains(type)) ) {
							dbArticles.put(tx, bytes(Util.normaliseTitle(title)), BigInteger.valueOf(id).toByteArray());
							nbToAdd++;
						}  
						if ( ((pageType == 1) || (pageType ==2)) && (acceptablePageTypesCategories.contains(type)) ) {
							dbCategories.put(tx, bytes(Util.normaliseTitle(title)), BigInteger.valueOf(id).toByteArray());
							nbToAdd++;
						} 
						
						//pageIdsByTitle.put(Util.normaliseTitle(title), id);
						if (reporter != null)
							reporter.progress() ;
						
					} catch (Exception e) {
						Logger.getLogger(Util.class).error("Caught exception while gathering page from '" + line + "' in '" + pageFile + "'", e) ;
					}
				}
				
				fis.close() ;
			}
			tx.commit();
		} catch(Exception e) {
			Logger.getLogger(Util.class).error("Caught exception while gathering page", e) ;
		} finally {
			if (tx != null)
				tx.close();
		}
		
		long memAfter = r.totalMemory() ;
		Logger.getLogger(getClass()).info("Memory Used: " + (memAfter - memBefore) / (1024*1024) + "Mb") ;
		
		if (pageType == 0)
			isLoadedArticles = true;
		else 
			isLoadedCategories = true;
	}
	
	public int getArticleId(String title) {
		String nTitle = Util.normaliseTitle(title) ;
		byte[] res = dbArticles.get(bytes(nTitle));
		if (res == null)
			return -1;
		else 
			return new BigInteger(res).intValue();
	}

	public int getCategoryId(String title) {
		String nTitle = Util.normaliseTitle(title) ;
		byte[] res = dbCategories.get(bytes(nTitle));
		if (res == null)
			return -1;
		else 
			return new BigInteger(res).intValue();
	}


	
}
