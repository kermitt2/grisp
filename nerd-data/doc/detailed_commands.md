


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



