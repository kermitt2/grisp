package org.wikipedia.miner.extract.steps;

import com.scienceminer.nerd.mention.ProcessText;
import com.scienceminer.nerd.utilities.StringPos;
import com.scienceminer.nerd.utilities.mediaWiki.MediaWikiParser;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.util.Tool;
import org.apache.log4j.Logger;
import org.grobid.core.lang.Language;
import org.wikipedia.miner.extract.DumpExtractor;
import org.wikipedia.miner.extract.DumpExtractor.ExtractionStep;
import org.wikipedia.miner.extract.model.DumpPage;
import org.wikipedia.miner.extract.model.DumpPageParser;
import org.wikipedia.miner.extract.model.struct.ExLabel;
import org.wikipedia.miner.extract.model.struct.ExSenseForLabel;
import org.wikipedia.miner.extract.steps.LabelSensesStep.LabelOutputFormat;
import org.wikipedia.miner.extract.util.LabelCache;
import org.wikipedia.miner.extract.util.LanguageConfiguration;
import org.wikipedia.miner.extract.util.SiteInfo;
import org.wikipedia.miner.extract.util.XmlInputFormat;

import java.io.IOException;
import java.util.*;

/**
 * The (fourth) fifth step in the extraction process.
 * 
 * This produces the following sequence files (in <i>&lt;ouput_dir&gt;/step5/</i>)
 * <ul>
 * <li><b>tempLabel-xxxxx</b> - associates label text (String) with label (ExLabel) - includes term and doc counts, but link counts and senses missing.</li>
 * </ul>
 */
public class LabelOccurrencesStep extends Configured implements Tool {

	private static String labelDbFile = null;

	public LabelOccurrencesStep(String labelDbFile) {
		this.labelDbFile = labelDbFile;
	}
	
	public int run(String[] args) throws Exception {

		JobConf conf = new JobConf(LabelOccurrencesStep.class);
		DumpExtractor.configureJob(conf, args);
		
		conf.setJobName("WM: count label occurrences");

		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(ExLabel.class);

		conf.setMapperClass(LabelOccurrencesMapper.class);
		conf.setCombinerClass(LabelOccurrencesReducer.class);
		conf.setReducerClass(LabelOccurrencesReducer.class);

		// set up input
		conf.setInputFormat(XmlInputFormat.class);
		conf.set(XmlInputFormat.START_TAG_KEY, "<page>");
		conf.set(XmlInputFormat.END_TAG_KEY, "</page>");
		FileInputFormat.setInputPaths(conf, conf.get(DumpExtractor.KEY_INPUT_FILE));

		//set up output
		conf.setOutputFormat(LabelOutputFormat.class);
		FileOutputFormat.setOutputPath(conf, new Path(conf.get(DumpExtractor.KEY_OUTPUT_DIR) +"/" + DumpExtractor.getDirectoryName(ExtractionStep.labelOccurrence)));

		//set up distributed cache

		DistributedCache.addCacheFile(new Path(conf.get(DumpExtractor.KEY_OUTPUT_DIR) + "/" + DumpExtractor.OUTPUT_SITEINFO).toUri(), conf);
		DistributedCache.addCacheFile(new Path(conf.get(DumpExtractor.KEY_LANG_FILE)).toUri(), conf);

		//cache label files created in 3rd pass, so we know what vocabulary of labels we are interested in.
		Path labelSensePath = new Path(conf.get(DumpExtractor.KEY_OUTPUT_DIR) + "/" + DumpExtractor.getDirectoryName(ExtractionStep.labelSense));
		for (FileStatus fs:FileSystem.get(conf).listStatus(labelSensePath)) {

			if (fs.getPath().getName().startsWith(LabelSensesStep.Output.tempLabel.name())) {
				Logger.getLogger(LabelOccurrencesStep.class).info("Cached temporary label file " + fs.getPath());
				DistributedCache.addCacheFile(fs.getPath().toUri(), conf);
			}
		}
		
		JobClient.runJob(conf);
		return 0;
	}

	private static class LabelOccurrencesMapper extends MapReduceBase implements Mapper<LongWritable, Text, Text, ExLabel> {

		private LanguageConfiguration lc;
		private SiteInfo si;

		private DumpPageParser pageParser;
		
		private LabelCache labelCache;

		private MediaWikiParser stripper = MediaWikiParser.getInstance();
		
		private int maxLabelLength = 15;

		@Override
		public void configure(JobConf job) {
			try {
				Path[] cacheFiles = DistributedCache.getLocalCacheFiles(job);

				for (Path cf:cacheFiles) {

					if (cf.getName().equals(new Path(DumpExtractor.OUTPUT_SITEINFO).getName())) {
						si = new SiteInfo(cf);
					}

					if (cf.getName().equals(new Path(job.get(DumpExtractor.KEY_LANG_FILE)).getName())) {
						lc = new LanguageConfiguration(job.get(DumpExtractor.KEY_LANG_CODE), cf);
					}
					
					/*if (cf.getName().startsWith(LabelSensesStep.Output.tempLabel.name())) {
						Logger.getLogger(LabelOccurrencesMapper.class).info("Located cached label file " + cf.toString());
						labelFiles.add(cf);
					}*/
				}
				
				if (si == null) 
					throw new Exception("Could not locate '" + DumpExtractor.OUTPUT_SITEINFO + "' in DistributedCache");

				if (lc == null) 
					throw new Exception("Could not locate '" + job.get(DumpExtractor.KEY_LANG_FILE) + "' in DistributedCache");

				pageParser = new DumpPageParser(lc, si);
				
				labelCache = new LabelCache(labelDbFile, job.get(DumpExtractor.KEY_LANG_CODE));
			} catch (Exception e) {
				Logger.getLogger(LabelOccurrencesMapper.class).error("Could not configure mapper", e);
				System.exit(1);
			}
		}

		@Override
		public void map(LongWritable key, Text value, OutputCollector<Text, ExLabel> output, Reporter reporter) throws IOException {		
			try {
				DumpPage page = pageParser.parsePage(value.toString());
				if (page != null) {

					String markup = page.getMarkup();
					markup = stripper.toTextOnly(markup, lc.getLangCode());

					final Language lang = new Language(lc.getLangCode());
					final List<StringPos> ngrams = ProcessText.ngrams(markup, maxLabelLength, lang);

					HashMap<String, ExLabel> labels = new HashMap<String, ExLabel>();
					for(StringPos sp : ngrams) {
						String ngram = sp.getString();

						if (labelCache.isKnown(ngram)) {

							ExLabel label = labels.get(ngram);

							if (label == null) {
								label = new ExLabel(0,0,1,1,new TreeMap<>());
							} else {
								label.setTextOccCount(label.getTextOccCount() + 1);
							}

							labels.put(ngram, label);
						}
					}

					// now emit all of the labels we have gathered
					for (Map.Entry<String,ExLabel> entry:labels.entrySet()) {
						output.collect(new Text(entry.getKey()), entry.getValue());
					}
				}

			} catch (Exception e) {
				Logger.getLogger(LabelOccurrencesMapper.class).error("Caught exception", e);
			}
		}

		@Override
		public void close() throws IOException {
			labelCache.close();
			super.close();
		}
	}

	public static class LabelOccurrencesReducer extends MapReduceBase implements Reducer<Text, ExLabel, Text, ExLabel> {

		public void reduce(Text key, Iterator<ExLabel> values, OutputCollector<Text, ExLabel> output, Reporter reporter) throws IOException {

			ExLabel label = new ExLabel(0,0,0,0,new TreeMap<Integer, ExSenseForLabel>());

			while (values.hasNext()) {
				ExLabel currLabel = values.next();
				
				label.setTextDocCount(label.getTextDocCount() + currLabel.getTextDocCount());
				label.setTextOccCount(label.getTextOccCount() + currLabel.getTextOccCount());
			}	

			output.collect(key, label);
		}
	}
}
