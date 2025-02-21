package org.wikipedia.miner.extract.model;

import com.scienceminer.nerd.kb.model.Page.PageType;
import com.scienceminer.nerd.kb.model.hadoop.*;
import com.scienceminer.nerd.utilities.mediaWiki.MediaWikiParser;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.record.CsvRecordInput;
import org.junit.Before;
import org.junit.Test;
import org.wikipedia.miner.extract.util.PagesByTitleCache;
import org.wikipedia.miner.extract.util.SiteInfo;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class CategoryTest {

	private DumpLinkParser dumpLinkParser = null;
	private MediaWikiParser stripper = null;
	private PagesByTitleCache pagesByTitle = null;

	/**
	 * This test can be updated with the new language that has been added, to verify that everything will work in hadoop
	 *
	 */
	@Test
	public void testSetup() {
		MediaWikiParser parser = MediaWikiParser.getInstance();

		parser.toTextWithInternalLinksAndCategoriesOnly("page", "nl");
	}

//	@Before
	public void setUp() {
		try {
			//LanguageConfiguration lc = new LanguageConfiguration("en", new Path("src/test/resources/languages.xml"));
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
			dumpLinkParser = new DumpLinkParser(si);
			stripper = MediaWikiParser.getInstance();
			pagesByTitle = new PagesByTitleCache(null, "en");
			System.out.println("nb of article titles: " + pagesByTitle.getArticleDatabaseSize());
			System.out.println("nb of category titles: " + pagesByTitle.getArticleDatabaseSize());
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

//	@Test
	public void testCategoryCsvPage() {
		try {
			List<String> lines = Arrays.asList("10,'AccessibleComputing,2,0", 
				"12,'Anarchism,0,6", 
				"2633752,'Hereditary cancers,1,11", 
				"39003238,'Hereditary cancers,2,0");

			List<String> expected = Arrays.asList("redirect", "article", "category", "redirect");

			int i = 0;
			for(String line : lines) {
				try {
					CsvRecordInput cri = new CsvRecordInput(new ByteArrayInputStream((line + "\n").getBytes("UTF-8"))) ;
		
					int id = cri.readInt("id");
					DbPage page = new DbPage();
					page.deserialize(cri);
	
					String title = page.getTitle();
					PageType type = PageType.values()[page.getType()];

					//System.out.println(type);
					if (!type.toString().equals(expected.get(i))) {
						System.out.println("Unexpected type (" + type + ") for: " + line);
					}
				} catch (Exception e) {
					System.out.println("Could not deserialize line '" + line + "'");
					e.printStackTrace();
				}
				i++;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	
	//@Test
	/*public void testCategoryFromPageDump() {
		try {
			String markup = "bla bla [[Category:Hereditary cancers]] blo blo, but not [[Hereditary cancers]] bla.";
			Vector<int[]> linkRegions = stripper.gatherComplexRegions(markup, "\\[\\[", "\\]\\]") ;

			for(int[] linkRegion: linkRegions) {
				String linkMarkup = markup.substring(linkRegion[0]+2, linkRegion[1]-2) ;
				System.out.println("linkMarkup: " + linkMarkup);

				DumpLink link = null ;
				try {
					link = dumpLinkParser.parseLink(linkMarkup) ;
				} catch (Exception e) {
					System.out.println("Could not parse link markup '" + linkMarkup + "'") ;
				}

				if (link == null)
					continue ;

				if (link.getTargetNamespace() == SiteInfo.CATEGORY_KEY)  {
					int parentId = pagesByTitle.getCategoryId(link.getTargetTitle()) ;
					if (parentId != -1) {
						System.out.println("category: " + parentId);
					} else {
						System.out.println("Could not resolve category link '" + link.getTargetTitle() + "'") ;
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}*/

}
