# GRISP

Knowledge Base stuff

## Create NERD data

The original and preprocessed files are described [here](nerd-data/data/preprocessed-wikipedia-files.md). 

### Processing a Wikipedia XML article dump file

Create hadoop job jar:

```
> mvn clean package
```

Then see instructions under ```nerd-data/doc/hadoop.txt``` for running the hadoop job and getting csv file results.

This processing is an adaptation of the WikipediaMiner 2.0 XML dump processing. Processing is considerably faster than with WikipediaMiner and a single server is enough for processing the lastest XML dumps in a reasonnable time: December 2016 English Wikipedia XML dump: ~7 hours 30 minutes, December 2016 French and German Wikipedia XML dump: 2 hours 30 minutes in pseudo distributed mode, one server Intel Core i7-4790K CPU 4.00GHz Haswell, 16GB memory, with 4 cores, 8 threads, SSD. 

We think it is possible to still improve significantly the processing time, lower memory consumption, and avoid completely Hadoop - simply by optimizing the processing for a common single multi-thread machine. 

### Creating additional cvs translation files

Translation information are not available anymore in the Wikipedia XML dump, downloding the SQL langlink file is necessary (e.g. `enwiki-latest-langlinks.sql.gz`). The file must be put together with the XML dump file. Then for each language, the translation cvs file can be generated with the command - here for English: 

```
> mvn compile exec:exec -PbuildTranslationEn
```

For other languages, replace the ending ```En```, but the appropriate lang code, e.g. for French:

```
> mvn compile exec:exec -PbuildTranslationFr
```

### Creating Wikidata knowledge base backbone and language-specific mapping

Wikidata is a multilingual knowledge base that can be used on top the existing language-specific wikipedia. It provides conceptual information such as properties and semantic relations built in a controled way. 

For  importing Wikidata resources in GRISP. Use the following command:

```
> mvn compile exec:exec -PbuildWikidata
```

The process uses the compressed JSON Wikidata ``latest-all.json.bz2`` and for each language the ``**wiki-latest-page_props.sql.gz`` mapping information (where ** is the language code, e.g. `en`, `fr`, `de`, ...). 


### Just for reference: Creating additional infobox csv files with DBPedia

For generating the complementary csv files capturing the infobox information, the DBpedia infobox tql file can be used. The DBPedia project has already parsed the Wikipedia XML dumps to get the infobox information, so we simply reuse this work for importing in GRISP. 

Note that given the very low quality of DBPedia, its usage is actually more harmful than useful and, after practical experiments, this resource is better be ignored. Wikidata is the right replacement both for data quality and soundness of the work. 

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
