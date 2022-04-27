The script under `grisp/scripts/wikipedia-resources.sh` takes care of the download and some processing of the Wikidata and Wikipedia resources. After downloading, three data extractions are realized for every indicated languages. We document here the commands for running these processing individually. However you should normally not call these commands yourself as it is handled by the script. 

### Creating additional cvs translation files

Translation information are not available anymore in the Wikipedia XML dump, so downloading the SQL langlink file is necessary (e.g. `enwiki-latest-langlinks.sql.gz`). This file must be put together with the XML dump file. Then for each language, the translation cvs file can be generated with the command - here for English: 

```console
cd nerd-data
mvn compile exec:exec -PbuildTranslation -Dlang="en" -Dinput="/somewhere/en/enwiki-latest-langlinks.sql.gz" -Doutput"somewhere/en/" 
```

The command line takes 3 arguments: 

* `-Dlang` is the language code of the target language for which the translation are generated
* `-Dinput` is the path to the downloaded SQL langlink file gzipped (e.g. `enwiki-latest-langlinks.sql.gz` for english)
* `-Doutput` is the path to the directory where to write the result csv translation file 

For example:

```console
mvn compile exec:exec -PbuildTranslation -Dlang=en -Dinput=/media/lopez/data/wikipedia/latest/ja.old/jawiki-latest-langlinks.sql.gz -Doutput=/media/lopez/data/wikipedia/latest/ja
```

For other languages, replace the ```en```, with the appropriate lang codes (among supported ones `de`, `fr`, `es`, `it`, `jp`, etc.), e.g. for Japanese language:

```
mvn compile exec:exec -PbuildTranslation -Dlang=ja -Dinput=/media/lopez/data/wikipedia/latest/ja.old/jawiki-latest-langlinks.sql.gz -Doutput=/media/lopez/data/wikipedia/latest/ja
```

### Creating Wikidata knowledge base backbone and language-specific mapping

Wikidata is a multilingual knowledge base that can be used on top the existing language-specific wikipedia. It provides conceptual information such as properties and semantic relations built in a controled way. 

The following language specific files must be first downloaded: ``**wiki-latest-page_props.sql.gz`` for each target languages (`en`, `fr`, `de`, `it`, `es`, etc.), with `en` at least being mandatory and put in the same subdirectory.

The JSON Wikidata dump file ``latest-all.json.bz2`` must be downloaded. 

For  importing Wikidata resources in GRISP, then adapt the following command:

```
> cd nerd-data
> mvn compile exec:exec -PbuildWikidata -Dinput=/somewhere/wikidata/latest-all.json.bz2 -Doutput=/somewhere/ 
```

The command line takes 2 arguments: 

* `-Dinput` is the path to the downloaded full Wikidata JSON dump bzip2 file (e.g. `latest-all.json.bz2`)
* `-Doutput` is the path to the directory where to write the language-specific entity mapping file, it is expecting one subdirectory per language (`en/`, `fr/`, etc.) each one containing its specific ``**wiki-latest-page_props.sql.gz``

For example: 

```console
mvn compile exec:exec -PbuildWikidata -Dinput=/media/lopez/data/wikipedia/latest/wikidata/latest-all.json.bz2 -Doutput=/media/lopez/data/wikipedia/latest
```

with: 

```
ls /media/lopez/data/wikipedia/latest
ar  de  en  es  fr  it  ja  ru  wikidata  zh
```

The process uses the compressed JSON Wikidata ``latest-all.json.bz2`` and for each language the compressed ``**wiki-latest-page_props.sql.gz`` mapping information (where `**` is the language code, e.g. `en`, `fr`, `de`, ...). 

### Generating Wikidata property labels for each language

By default, Wikidata property identifiers are not easily readable (e.g. `P31`). In order to associate the property identifiers to a readable language-specific label, we need to generate the language-specific property labels (files `xx/wikidata-properties.json` where `xx` is the two letter language code) as follow: 

- for English:

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22en%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O en/wikidata-properties.json
```

- for French: 

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22fr%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O fr/wikidata-properties.json
```

- for German:

```bash
wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22de%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O de/wikidata-properties.json
```

Just modify the language code in the url for other languages. Put all these language-specific Wikidata property naming into their corresponding language-specific data directory.
