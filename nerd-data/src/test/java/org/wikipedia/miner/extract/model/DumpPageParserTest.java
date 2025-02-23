package org.wikipedia.miner.extract.model;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.*;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;
import org.wikipedia.miner.extract.util.*;

import com.scienceminer.nerd.utilities.mediaWiki.MediaWikiParser;

import org.apache.hadoop.fs.Path;

public class DumpPageParserTest {

    private DumpPageParser dumpPageParser = null;
    private MediaWikiParser stripper = null;

    public void setUp(String lang) {
        try {
            LanguageConfiguration lc = new LanguageConfiguration(lang, new Path("src/test/resources/languages.xml"));
            SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
            
            dumpPageParser = new DumpPageParser(lc, si);
            stripper = MediaWikiParser.getInstance();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPageParserZh() {
        try {
            this.setUp("zh");

            String markup = FileUtils.readFileToString(new File("src/test/resources/page-zh-01.xml"), "UTF-8");
            DumpPage page = dumpPageParser.parsePage(markup);
            // should be null because not an article page
            assertEquals(page, null);

            markup = FileUtils.readFileToString(new File("src/test/resources/page-zh-02.xml"), "UTF-8");
            page = dumpPageParser.parsePage(markup);
            // should be null because not an article page
            assertEquals(page, null);

            markup = FileUtils.readFileToString(new File("src/test/resources/page-zh-03.xml"), "UTF-8");
            page = dumpPageParser.parsePage(markup);
            assertNotNull(page);

            String localTitle = page.getTitle();
            assertNotNull(localTitle);
            System.out.println("page title: " + localTitle);
            String localMarkup = page.getMarkup();
            assertNotNull(localMarkup);

            // test capture of links 
            localMarkup = stripper.toTextWithInternalLinksAndCategoriesOnly(localMarkup, "zh");
            assertNotNull(localMarkup);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPageParserSv() {
        try {
            this.setUp("sv");

            String markup = FileUtils.readFileToString(new File("src/test/resources/Artiklar.xml"), "UTF-8");
            DumpPage page = dumpPageParser.parsePage(markup);

            // should not be null because category page
            assertNotNull(page);

            String localTitle = page.getTitle();
            assertNotNull(localTitle);
            System.out.println("page title: " + localTitle);

            // test capture of links 
            String localMarkup = page.getMarkup();

            System.out.println("page localMarkup: " + localMarkup);

            localMarkup = stripper.toTextWithInternalLinksAndCategoriesOnly(localMarkup, "sv");

            System.out.println("stripped localMarkup: " + localMarkup);

            assertNotNull(localMarkup);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPageParserNL() {
        try {
            this.setUp("nl");

            String markup = FileUtils.readFileToString(new File("src/test/resources/Artiklar.xml"), "UTF-8");
            DumpPage page = dumpPageParser.parsePage(markup);

            // should not be null because category page
            assertNotNull(page);

            String localTitle = page.getTitle();
            assertNotNull(localTitle);
            System.out.println("page title: " + localTitle);

            // test capture of links
            String localMarkup = page.getMarkup();

            System.out.println("page localMarkup: " + localMarkup);

            localMarkup = stripper.toTextWithInternalLinksAndCategoriesOnly(localMarkup, "sv");

            System.out.println("stripped localMarkup: " + localMarkup);

            assertNotNull(localMarkup);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
