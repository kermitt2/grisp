package org.wikipedia.miner.extract.model;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.*;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.wikipedia.miner.extract.util.*;

import org.apache.hadoop.fs.Path;
import java.io.*;
import java.util.*;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.record.CsvRecordInput;
import org.wikipedia.miner.db.struct.DbPage;
import org.wikipedia.miner.model.Page.PageType;
import org.wikipedia.miner.util.MarkupStripper;

public class CategoryTest {

	private DumpLinkParser dumpLinkParser = null;
	private MarkupStripper stripper = null;
	private PagesByTitleCache pagesByTitle = null;

	//@Before
	public void setUp() {
		try {
			LanguageConfiguration lc = new LanguageConfiguration("en", new Path("src/test/resources/languages.xml"));
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
			dumpLinkParser = new DumpLinkParser(lc, si);
			stripper = new MarkupStripper();
			pagesByTitle = new PagesByTitleCache(null, "en");
			System.out.println("nb of article titles: " + pagesByTitle.getArticleDatabaseSize());
			System.out.println("nb of category titles: " + pagesByTitle.getArticleDatabaseSize());
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	//@Test
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
	public void testCategoryFromPageDump() {
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
	}

}
