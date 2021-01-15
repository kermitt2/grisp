# GRISP

Pre-process the Language and Knowledge Base data for loading into [entity-fishing](https://github.com/kermitt2/entity-fishing).

## Create entity-fishing Wikipedia and Wikidata preprocessed data

The sub-module `nerd-data` pre-processes the Wikipedia XML dumps and creates compiled data to be used by [entity-fishing](https://github.com/kermitt2/entity-fishing), a machine learning tool for extracting and disambiguating Wikidata entities in text and PDF at scale. 

The pre-processing is an adaptation of the [WikipediaMiner 2.0](https://github.com/dnmilne/wikipediaminer) XML dump processing, which relies on Hadoop. The main Modifications include the usage of the [Sweble MediaWiki document parser](https://en.wikipedia.org/wiki/Sweble) for Wikipedia pages (the most comprehensive, reliable and fast MediaWiki parser following our tests, apart MediaWiki itself), a complete review of the compiled statistics, the usage of LMDB to avoid distributed data, additional extraction related to multilinguality and various speed optimization.

The Wikipedia pre-processing supports current the Wikipedia dumps (May 2020) and was successfully tested with English, German, French, Spanish and Italian XML dumps. Japanese dump should also be well supported, see the branch `Japanese`. The Wikipedia XML dumps and additional required files are available at the Wikimedia Downloads [page](https://dumps.wikimedia.org/), as well as the Wikidata JSON dump.

### Preliminary install

[entity-fishing](https://github.com/kermitt2/entity-fishing) needs to be installed first on the system and built, without the knowledge-base and language data. See the documentation. 

### Downloading the resource files from Wikidata and Wikipedia

The GRISP pre-processor uses the Wikidata dump file in JSON format. Then for each language to be supported, 3 files must be downloaded:

- `**wiki-********-pages-articles-multistream.xml.bz2`, which gives the full article content for the given language

- `**wiki-********-langlinks.sql.gz`, which gives the cross-language correspondences 

- `**wiki-********-page_props.sql.gz`, whick gives the association between the language-specific page ID and the wikidata entities

For instance for covering Wikidata in English, German, French, Spanish and Italian, downloading the resources will result in the following file list and size:


```
total 94G
drwxrwxr-x 2 lopez lopez 4.0K Jan 12 16:29 ./
drwxrwxr-x 5 lopez lopez 4.0K Jan 12 01:23 ../
-rw-rw-r-- 1 lopez lopez 232M Jan  1 20:30 dewiki-20210101-langlinks.sql.gz
-rw-rw-r-- 1 lopez lopez  74M Jan  1 21:02 dewiki-20210101-page_props.sql.gz
-rw-rw-r-- 1 lopez lopez 5.7G Jan  2 05:05 dewiki-20210101-pages-articles-multistream.xml.bz2
-rw-rw-r-- 1 lopez lopez 396M Jan  1 11:09 enwiki-20210101-langlinks.sql.gz
-rw-rw-r-- 1 lopez lopez 280M Jan  1 10:59 enwiki-20210101-page_props.sql.gz
-rw-rw-r-- 1 lopez lopez  18G Jan  3 07:28 enwiki-20210101-pages-articles-multistream.xml.bz2
-rw-rw-r-- 1 lopez lopez 236M Jan  1 19:26 eswiki-20210101-langlinks.sql.gz
-rw-rw-r-- 1 lopez lopez  43M Jan  1 19:15 eswiki-20210101-page_props.sql.gz
-rw-rw-r-- 1 lopez lopez 3.4G Jan  2 04:45 eswiki-20210101-pages-articles-multistream.xml.bz2
-rw-rw-r-- 1 lopez lopez 267M Jan  1 19:29 frwiki-20210101-langlinks.sql.gz
-rw-rw-r-- 1 lopez lopez  75M Jan  1 19:15 frwiki-20210101-page_props.sql.gz
-rw-rw-r-- 1 lopez lopez 4.8G Jan  2 05:03 frwiki-20210101-pages-articles-multistream.xml.bz2
-rw-rw-r-- 1 lopez lopez 228M Jan  1 19:55 itwiki-20210101-langlinks.sql.gz
-rw-rw-r-- 1 lopez lopez  44M Jan  1 19:40 itwiki-20210101-page_props.sql.gz
-rw-rw-r-- 1 lopez lopez 3.1G Jan  2 04:51 itwiki-20210101-pages-articles-multistream.xml.bz2
-rw-rw-r-- 1 lopez lopez  58G Jan  6 17:12 latest-all.json.bz2
```

### Pre-processing a Wikipedia XML article dump file

Create the hadoop job jar:

```
> cd nerd-data

> mvn clean package
```

Then see instructions under [nerd-data/doc/hadoop.md](nerd-data/doc/hadoop.md) for running the hadoop job and getting csv file results.

This processing is an adaptation and optimization of the [WikipediaMiner 2.0](https://github.com/dnmilne/wikipediaminer) XML dump processing. It enables the support of the latest Wikipedia dump files. The processing is considerably faster than with WikipediaMiner and a single server is enough for processing the lastest XML dumps in a reasonnable time. For December 2016 English Wikipedia XML dump: around 7 hours 30 minutes. For December 2016 French and German Wikipedia XML dump: around 2 hours 30 minutes (in pseudo distributed mode, one server Intel Core i7-4790K CPU 4.00GHz Haswell, 16GB memory, with 4 cores, 8 threads, SSD). 

We think that it is possible to still improve significantly the processing time, lower memory consumption, and avoid completely Hadoop - simply by optimizing the processing for a common single multi-thread machine. But given that the current state of the library gives satisfactory performance, we leave these improvements for the future if necessary. 

### Creating additional cvs translation files

Translation information are not available anymore in the Wikipedia XML dump, so downloading the SQL langlink file is necessary (e.g. `enwiki-latest-langlinks.sql.gz`). This file must be put together with the XML dump file. Then for each language, the translation cvs file can be generated with the command - here for English: 

```
> mvn compile exec:exec -PbuildTranslationEn
```

For other languages, replace the ending ```En```, with the appropriate lang codes (among `De`, `Fr`, `Es`, `It`, `Jp`), e.g. for French language:

```
> mvn compile exec:exec -PbuildTranslationFr
```

Check the correct paths given in argument in the `nerd-data/pom.xml` for the tasks `buildTranslation**`. 

### Creating Wikidata knowledge base backbone and language-specific mapping

Wikidata is a multilingual knowledge base that can be used on top the existing language-specific wikipedia. It provides conceptual information such as properties and semantic relations built in a controled way. 

The following language specific files must be first downloaded: ``**wiki-latest-page_props.sql.gz`` for each target languages (`en`, `fr`, `de`, `it`, `es`), with `en` at least being mandatory and put in the same subdirectory.

The JSON Wikidata dump file ``latest-all.json.bz2`` must be downloaded. 

For  importing Wikidata resources in GRISP, then use the following command:

```
> mvn compile exec:exec -PbuildWikidata
```

The process uses the compressed JSON Wikidata ``latest-all.json.bz2`` and for each language the compressed ``**wiki-latest-page_props.sql.gz`` mapping information (where `**` is the language code, e.g. `en`, `fr`, `de`, ...), the correct paths to these files must currently be indicated manually in the `nerd-data/pom.xml` for the task `buildWikidata`. 

### Generating Wikidata property labels for each language

By default, Wikidata property identifiers are not easily readable (e.g. `P31`). In order to associate the property identifiers to a readable language-specific label, we need to generate the language-specific property labels (files `wikidata.txt`) as follow: 

- for English:

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22en%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O wikidata.txt
```

- for French: 

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22fr%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O wikidata.txt
```

- for German:

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22de%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O wikidata.txt
```

Just modify the language code in the url for other languages. Put all these language-specific Wikidata property naming into their corresponding language-specific data directory.

### Final hierarchy of files 

Here how the final data tree should look like from the root directory (for 3 languages, additional languages follow the same pattern), ready to be loaded and further optimized in embedded databases by [entity-fishing](https://github.com/kermitt2/entity-fishing): 

```
.
├── de
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── dewiki-latest-pages-articles.xml.bz2
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata.txt
├── en
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── enwiki-latest-pages-articles.xml.bz2
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata.txt
├── fr
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── frwiki-latest-pages-articles.xml.bz2
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata.txt
├── wikidata
│   ├── wikidataIds.csv 
│   ├── latest-all.json.bz2

```

Note:

- it is expected to have 15 files in each language-specific directory,

- the full Wikipedia article dump for each language must be present in the language-specific directories (e.g. `enwiki-latest-pages-articles.xml.bz2` or `enwiki-latest-pages-articles.xml.gz` or `enwiki-latest-pages-articles.xml`, they are required to generate definitions for entities, create training data, compute additional entity embeddings) ; the dump file can be compressed in `bz2`, `gzip` or uncompressed - all these variants should be loaded appropriately by entity-fishing,

- the wikidata identifiers csv file `wikidataIds.csv` and the full wikidata JSON dump file `latest-all.json.bz2` are under a `wikidata` sub-directory while the wikidata language-specific Wikidata mapping files `wikidata.txt` are installed in each language-specific sub-directory,

- in entity-fishing the loading of these files is automatic when building the project or starting the service (if not present), be sure to indicate the path to these above files in the entity-fishing config files.

## Just for History: Creating additional infobox csv files with DBPedia

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

We considering generating more KB data to be mapped: geonames, geospecies, etc.

## Credits

Many thanks to David Milne for the Wikipedia XML dump processing. The present pre-processing of the Wikipedia data is originally a fork of a part of his project. 

## License

GRISP is distributed under [GPL 3.0 license](https://www.gnu.org/licenses/gpl-3.0.html). 

Contact: Patrice Lopez (<patrice.lopez@science-miner.com>)
