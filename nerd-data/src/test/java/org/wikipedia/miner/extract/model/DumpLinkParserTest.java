package org.wikipedia.miner.extract.model;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.*;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;
import org.wikipedia.miner.extract.util.*;

import com.scienceminer.nerd.utilities.mediaWiki.MediaWikiParser;

import org.apache.hadoop.fs.Path;

public class DumpLinkParserTest {

	private DumpLinkParser dumpLinkParser = null;
	private MediaWikiParser stripper = null;

	@Before
	public void setUp() {
		try {
			//LanguageConfiguration lc = new LanguageConfiguration("en", new Path("src/test/resources/languages.xml"));
			// default
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
			dumpLinkParser = new DumpLinkParser(si);
			stripper = MediaWikiParser.getInstance();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testLinkParser() {
		try {
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
			dumpLinkParser = new DumpLinkParser(si);

			List<String> links = Arrays.asList("Alexander II of Russia", 
				"Marie François Sadi Carnot|Sadi Carnot", 
				"Synthesist anarchism|synthesist", 
				"File:Manifestación CNT Bilbao.jpg|thumb|left|May day demonstration of Spanish [[anarcho-syndicalist]] trade union CNT in [[Bilbao]], Basque Country in 2010",
				"Issues in anarchism#Communism|communism", "Category:Hereditary cancers");

			for(String link : links) {
				try {
					DumpLink dumpLink = dumpLinkParser.parseLink(link);
					System.out.println(dumpLink.toString());
				} catch (Exception e) {
					System.out.println("Could not parse link markup '" + link + "'");
					e.printStackTrace();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testLinkParserInSentence() {
		try {
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
			dumpLinkParser = new DumpLinkParser(si);

			List<String> sentences = Arrays.asList("'''Anarchism''' is a [[political philosophy]] that advocates [[self-governance|self-governed]] societies based on voluntary institutions.",
				"These are often described as [[stateless society|stateless societies]].", 
				"Following his time in Judea, Albinus was chosen by Nero to be governor of [[Mauretania Caesariensis]]. The province of [[Mauretania Tingitana]] was added to Albinus's governor duties by the Emperor [[Galba]]. Following Galba's death, Albinus supported [[Otho]] in the [[Year of the Four Emperors|year of political anarchy]] (69), which followed Nero's death.&lt;ref name=&quot;Hist 2.58&quot;&gt;Tacitus, ''[[Histories (Tacitus)|The Histories]]'', Volume II, Section 58&lt;/ref&gt; Following Otho's death, Albinus was rumored to have styled himself as a king using the title &quot;Juba&quot;. Albinus and his wife were assassinated shortly afterwards.&lt;ref name=&quot;Hist 2.59&quot;&gt;[[Tacitus]], ''The Histories'', Volume II, Section 59&lt;/ref&gt;",
				"bla bla [[Category:Hereditary cancers]] blo blo");

			for(String sentence : sentences) {
				String markup = stripper.toTextWithInternalLinksAndCategoriesOnly(sentence, "en");

				List<int[]> linkRegions = Util.gatherComplexRegions(markup, null, "\\[\\[", "\\]\\]");
				for(int[] linkRegion: linkRegions) {

					String linkMarkup = markup.substring(linkRegion[0]+2, linkRegion[1]-2);
					try {
						DumpLink dumpLink = dumpLinkParser.parseLink(linkMarkup);
						System.out.print(dumpLink.toString());

						int ns = dumpLink.getTargetNamespace();
						if (ns == SiteInfo.CATEGORY_KEY) 
							System.out.print(" / category");
						System.out.println("\n");
					} catch (Exception e) {
						System.out.println("Could not parse link markup '" + linkMarkup + "'");
						e.printStackTrace();
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testLinkParserInDisambiguationText() {
		try {
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfo.xml"));
			dumpLinkParser = new DumpLinkParser(si);

			String sentence = "'''Nardelli''' is an [[Italian language|Italian]] surname, which means a descendant of \"Nardo\",\n" +
				"a pet form of the name [[Leonardo]] (\"lion-like\").<ref>''Dictionary of American Family Names''.\n" +
				"Oxford University Press, 2013. Retrieved on 10 January 2016.</ref> The name may refer to:\n" +
				"*[[Elania Nardelli]] (born 1987), Italian sport shooter\n" +
				"*[[Francesco Nardelli]] (born 1953), Italian naturalist\n" +
				"*[[Maria Nardelli]] (born 1954), Italian athlete \n" +
				"*[[Michael Nardelli]] (born 1983), American actor \n" +
				"*[[Robert Nardelli]] (born 1948), American businessman\n" +
				"*[[Stefano Nardelli]] (born 1993), Italian cyclist\n" +
				"*[[Steve Nardelli]] (born 1948), British musician\n" +
				"\n" +
				"==References==\n" +
				"{{reflist}}\n" +
				"\n" +
				"{{surname}}\n" +
				"[[Category:Italian-language surnames]]";

			String markup = sentence;//stripper.toTextWithInternalLinksAndCategoriesOnly(sentence, "en");
			List<int[]> linkRegions = Util.gatherComplexRegions(markup, "\\*\\s*", "\\[\\[", "\\]\\]");
			System.out.println("******************** " + linkRegions.size() + " found disamb. links");
			for(int[] linkRegion: linkRegions) {
				String linkMarkup = markup.substring(linkRegion[0]+2, linkRegion[1]-2);
				while(linkMarkup.startsWith("[") || linkMarkup.startsWith(" ")) {
					linkMarkup = linkMarkup.substring(1);
				}
				try {
					DumpLink dumpLink = dumpLinkParser.parseLink(linkMarkup);
					System.out.println(dumpLink.toString());
				} catch (Exception e) {
					System.out.println("Could not parse link markup '" + linkMarkup + "'");
					e.printStackTrace();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testLinkParserCategoryPt() {
		try {
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfoPt.xml"));
			dumpLinkParser = new DumpLinkParser(si);

			List<String> links = Arrays.asList("Categoria:Elementos arquitetónicos", "Categoria:Árbitros de futebol", "Categoria:Funções matemáticas");
			for(String link : links) {
				try {
					DumpLink dumpLink = dumpLinkParser.parseLink(link);
					System.out.println(dumpLink.toString());
				} catch (Exception e) {
					System.out.println("Could not parse link markup '" + link + "'");
					e.printStackTrace();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testLinkParserCategorySv() {
		try {
			SiteInfo si = new SiteInfo(new Path("src/test/resources/siteInfoSv.xml"));
			dumpLinkParser = new DumpLinkParser(si);

			List<String> links = Arrays.asList("Kategori:Topp", "Kategori:Amager", "Kategori:Öar i Region Hovedstaden");
			for(String link : links) {
				try {
					DumpLink dumpLink = dumpLinkParser.parseLink(link);
					System.out.println(dumpLink.toString());
				} catch (Exception e) {
					System.out.println("Could not parse link markup '" + link + "'");
					e.printStackTrace();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

}
