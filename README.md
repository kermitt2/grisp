# GRISP

Compile the Language and Knowledge Base data for [entity-fishing](https://github.com/kermitt2/entity-fishing).

## Create entity-fishing Wikipedia preprocessed data

The sub-module `nerd-data` processes the Wikipedia XML dumps and creates compiled data to be used by [entity-fishing](https://github.com/kermitt2/entity-fishing), a machine learning tool for extracting and disambiguating Wikidata entities in text and PDF at scale. 

The processing is an adaptation of the [WikipediaMiner 2.0](https://github.com/dnmilne/wikipediaminer) XML dump processing, which relies on Hadoop. For reference, the original and preprocessed files are described [here](nerd-data/data/preprocessed-wikipedia-files.md). 

The Wikipedia processing supports current Wikipedia dumps (May 2020) and was successfully tested with English, German, French, Spanish and Italian XML dumps. Japanese dump should also be well supported, see the branch `Japanese`. Wikipedia XML dumps are available at the Wikimedia Downloads [page](https://dumps.wikimedia.org/).

### Processing a Wikipedia XML article dump file

Create the hadoop job jar:

```
> mvn clean package
```

Then see instructions under [nerd-data/doc/hadoop.md](nerd-data/doc/hadoop.md) for running the hadoop job and getting csv file results.

This processing is an adaptation and optimization of the [WikipediaMiner 2.0](https://github.com/dnmilne/wikipediaminer) XML dump processing. It enables the support of the latest Wikipedia dump files. The processing is considerably faster than with WikipediaMiner and a single server is enough for processing the lastest XML dumps in a reasonnable time. For December 2016 English Wikipedia XML dump: around 7 hours 30 minutes. For December 2016 French and German Wikipedia XML dump: around 2 hours 30 minutes (in pseudo distributed mode, one server Intel Core i7-4790K CPU 4.00GHz Haswell, 16GB memory, with 4 cores, 8 threads, SSD). 

We think that it is possible to still improve significantly the processing time, lower memory consumption, and avoid completely Hadoop - simply by optimizing the processing for a common single multi-thread machine. 

### Creating additional cvs translation files

Translation information are not available anymore in the Wikipedia XML dump, downloading the SQL langlink file is necessary (e.g. `enwiki-latest-langlinks.sql.gz`). The file must be put together with the XML dump file. Then for each language, the translation cvs file can be generated with the command - here for English: 

```
> mvn compile exec:exec -PbuildTranslationEn
```

For other languages, replace the ending ```En```, but the appropriate lang code, e.g. for French:

```
> mvn compile exec:exec -PbuildTranslationFr
```

Check the correct paths given in argument in the `pom.xml` for the tasks `buildTranslation**`. 

### Creating Wikidata knowledge base backbone and language-specific mapping

Wikidata is a multilingual knowledge base that can be used on top the existing language-specific wikipedia. It provides conceptual information such as properties and semantic relations built in a controled way. 

The following language specific files must be first downloaded: ``**wiki-latest-page_props.sql.gz`` for each target languages (`en`, `fr`, `de`, `it`, `es`), with `en` at least being mandatory and put in the same subdirectory.

The JSON Wikidata dump file ``latest-all.json.bz2``, compressed must be downloaded. 

For  importing Wikidata resources in GRISP, then use the following command:

```
> mvn compile exec:exec -PbuildWikidata
```

The process uses the compressed JSON Wikidata ``latest-all.json.bz2`` and for each language the ``**wiki-latest-page_props.sql.gz`` mapping information (where ** is the language code, e.g. `en`, `fr`, `de`, ...), the correct paths to these files must currently be indicated manually in the `pom.xml` for the task `buildWikidata`. 


### Just for History: Creating additional infobox csv files with DBPedia

This part is deprecated as we are not using at all DBPedia due to too low quality. 

For generating the complementary csv files capturing the infobox information, the DBpedia infobox tql file can be used. The DBPedia project has already parsed the Wikipedia XML dumps to get the infobox information, so we simply reuse this work for importing in GRISP. 

Note that given the very low quality of DBPedia, its usage is actually more harmful than useful and, after practical experiments, this resource is better be ignored. Wikidata is the right replacement both for data quality and soundness of the approach. 

Basically the generated csv file contains a list of properties and relations as available in the infoboxes. Use the following command:

```
> mvn compile exec:exec -Dexec.classpathScope=compile -PbuildInfoboxEn
```

For other languages, replace the ending ```En```, but the appropriate lang code, e.g. for French:

```
> mvn compile exec:exec -Dexec.classpathScope=compile -PbuildInfoboxFr
```

### More to come

Next data to be mapped: geonames, geospecies

## Credits

Many thanks to David Milne for the Wikipedia XML dump processing. The present pre-processing of the Wikipedia data is originally a fork of his project. 

## License

GRISP is distributed under [GPL 3.0 license](https://www.gnu.org/licenses/gpl-3.0.html). 

Contact: Patrice Lopez (<patrice.lopez@science-miner.com>)
