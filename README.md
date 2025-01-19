# GRISP

Pre-process the Language and Knowledge Base data for loading into [entity-fishing](https://github.com/kermitt2/entity-fishing).

## Create entity-fishing Wikipedia and Wikidata preprocessed data

The sub-module `nerd-data` pre-processes the Wikidata JSON and Wikipedia XML dumps to create compiled data to be used by [entity-fishing](https://github.com/kermitt2/entity-fishing), a machine learning tool for extracting and disambiguating Wikidata entities in text and PDF at scale. 

The pre-processing is an adaptation of the [WikipediaMiner 2.0](https://github.com/dnmilne/wikipediaminer) for the XML dump processing, which relies on Hadoop. The main Modifications include the usage of the [Sweble MediaWiki document parser](https://en.wikipedia.org/wiki/Sweble) for Wikipedia pages (the most comprehensive, reliable and fast MediaWiki parser following our tests, apart MediaWiki itself), a complete review of the compiled statistics, processing of Wikidata dump, the usage of LMDB to avoid distributed data, additional extraction related to multilinguality and various speed optimization.

The Wikipedia pre-processing supports current the Wikipedia dumps (2022) and was successfully tested with English, French, German, Italian, Spanish, Arabic, Mandarin, Russian, Japanese, Portuguese and Farsi XML dumps. The Wikipedia XML dumps and additional required files are available at the Wikimedia Downloads [page](https://dumps.wikimedia.org/), as well as the Wikidata JSON dump.

### Preliminary install of entity-fishing and GRISP

[entity-fishing](https://github.com/kermitt2/entity-fishing) needs to be installed first on the system and built, without the knowledge-base and language data:

```console
git clone https://github.com/kermitt2/entity-fishing
cd entity-fishing
./gradlew clean build -x test
```

The `-x test` when building is important to skip tests, because there is no knowledge-base and language resource data available for the tests yet. 

Then install and build GRISP:

```console
git clone https://github.com/kermitt2/grisp
cd grisp
mvn clean install 
```

**Note:** current latest versions of GRISP and [entity-fishing](https://github.com/kermitt2/entity-fishing) are `0.0.6`.

### Script for preparing the Wikidata and Wikipedia resources 

A script is available to:
* download the different resources needed from Wikidata and Wikipedia for a set of specified languages
* create cvs translation files between languages
* generate Wikidata property labels for each language
* creating Wikidata knowledge base backbone and language-specific mapping with Wikidata entities

The script has been tested on a Linux setup, but it is likely to work also on MacOS. To run the script:

```console
cd grisp/scripts/
./wikipedia-resources.sh [instal path of GRISP] [storage path of the data resources]
```

For example:

```console
./wikipedia-resources.sh /home/lopez/grisp/ /media/lopez/data/wikipedia/latest/
```

The steps mentioned above are realized successively by the scripts. 
By default, all the languages will be covered, but you can change to a subset of languages by modifying the script at the following line:

```bash
declare -a languages=("en" "de" "fr" "it" "es" "ar" "zh" "ja" "ru" "pt" "fa" "uk" "sv" "hi" "bn")
```

Note that English `"en"` at least is mandatory to further running [entity-fishing](https://github.com/kermitt2/entity-fishing). 

Be aware that the data path must have enough storage: as of April 2022, 74GB are needed for Wikidata dump and 70GB for all the language resources. To accomodate all resources, including the next Hadoop processing step, consider 200GB for all the languages. 

### Hadoop processing of Wikipedia XML article dump files

Once all the required resources have been downloaded via the provided script, see above, we can run the pre-processing of the Wikipedia dumps.

The parsing and processing of the Wikipedia XML article dump files is computationally expensive, it has to be parallelized and we are using an Hadoop process for this purpose. A pseudo distributed mode (just running the process on one machine with several CPU) is enough for reasonnable processing time. A "real" distributed mode has not been tested for the moment and is thus currently not supported. 

Create the hadoop job jar:

```console
cd grisp/nerd-data
> mvn clean package
```

Then see instructions under [nerd-data/doc/hadoop.md](nerd-data/doc/hadoop.md) for running the hadoop job and getting csv file results.

This processing is an adaptation and optimization of the [WikipediaMiner 2.0](https://github.com/dnmilne/wikipediaminer) XML dump processing. It enables the support of the latest Wikipedia dump files. The processing is considerably faster than with WikipediaMiner and a single server is enough for processing the lastest XML dumps in a reasonnable time. For December 2016 English Wikipedia XML dump: around 7 hours 30 minutes. For December 2016 French and German Wikipedia XML dump: around 2 hours 30 minutes (in pseudo distributed mode, one server Intel Core i7-4790K CPU 4.00GHz Haswell, 16GB memory, with 4 cores, 8 threads, SSD). 

We think that it is possible to still improve significantly the processing time, lower memory consumption, and avoid completely Hadoop - simply by optimizing the processing for a common single multi-thread machine. But given that the current state of the library gives satisfactory performance, we leave these improvements for the future if necessary. 

### Final hierarchy of files 

Here how the final data tree should look like from the root directory (for 3 languages, additional languages follow the same pattern), ready to be loaded and further optimized in embedded databases by [entity-fishing](https://github.com/kermitt2/entity-fishing): 

```
.
├── de
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── dewiki-latest-langlinks.sql.gz
│   ├── dewiki-latest-page_props.sql.gz
│   ├── dewiki-latest-pages-articles-multistream.xml.bz2
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata-properties.json
│   └── wikidata.txt
├── en
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── enwiki-latest-langlinks.sql.gz
│   ├── enwiki-latest-page_props.sql.gz
│   ├── enwiki-latest-pages-articles-multistream.xml.bz2
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata-properties.json
│   └── wikidata.txt
├── fr
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── frwiki-latest-langlinks.sql.gz
│   ├── frwiki-latest-page_props.sql.gz
│   ├── frwiki-latest-pages-articles-multistream.xml.bz2
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata-properties.json
│   └── wikidata.txt
├── wikidata
│   ├── wikidataIds.csv 
│   ├── latest-all.json.bz2

```

Note:

- it is expected to have 15 files in each language-specific directory, plus 3 Wikipedia dump files (the `.bz2` `.gz` files),

- the full Wikipedia article dump for each language must be present in the language-specific directories (e.g. `enwiki-latest-pages-articles-multistream.xml.bz2` or `enwiki-latest-pages-articles-multistream.xml.gz` or `enwiki-latest-pages-articles-multistream.xml`, they are required to generate definitions for entities, create training data, compute additional entity embeddings) ; the dump file can be compressed in `bz2`, `gzip` or uncompressed - all these variants should be loaded appropriately by entity-fishing,

- the wikidata identifiers csv file `wikidataIds.csv` and the full wikidata JSON dump file `latest-all.json.bz2` are under a `wikidata` sub-directory while the wikidata language-specific Wikidata mapping files `wikidata.txt` and `wikidata-properties.json` are installed in each language-specific sub-directory,

- in entity-fishing the loading of these files is automatic when building the project or starting the service (if not present), be sure to indicate the path to these above generated files in the entity-fishing config files.


### More to come

We considering generating more KB data to be mapped: geonames, geospecies, etc. and better exploiting Wikidata labels and statements.

## Credits

Many thanks to David Milne for the Wikipedia XML dump processing. The present pre-processing of the Wikipedia data is originally a fork of a part of his project. 

## License

GRISP is distributed under [GPL 3.0 license](https://www.gnu.org/licenses/gpl-3.0.html). 

Contact: Patrice Lopez (<patrice.lopez@science-miner.com>)
