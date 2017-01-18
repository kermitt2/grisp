# GRISP

Knowledge Base stuff

## Create NERD data

### Processing a Wikipedia XML article dump file

Create hadoop job jar:

```
> mvn clean package
```

Then see instructions under ```nerd-data/doc/hadoop.txt``` for running the hadoop job and getting csv file results.

This processing is an adaptation of the WikipediaMiner 2.0 XML dump processing. Processing is considerably faster than with WikipediaMiner and a single server is enough for processing the lastest XML dumps in a reasonnable time: December 2016 English Wikipedia XML dump: ~7 hours 30 minutes, December 2016 French and German Wikipedia XML dump: 2 hours 30 minutes in pseudo distributed mode, one server Intel Core i7-4790K CPU 4.00GHz Haswell, 16GB memory, with 4 cores, 8 threads, SSD. 

We think it is possible to still improve significantly the processing time, lower memory consumption, and avoid completely Hadoop - simply by optimizing the processing. 

### Creating additional cvs translation files

Translation information are not available anymore in the Wikipedia XML dump, downloding the SQL langlink file is necessary. The file must be put together with the XML dump file. Then for each language, the translation cvs file can be generated with the command - here for English: 

```
> mvn compile exec:exec -PbuildTranslationEn
```

For other languages, replace the ending ```En```, but the appropriate lang code, e.g. for French:

```
> mvn compile exec:exec -PbuildTranslationFr
```

### Creating additional infobox csv files

For generating the complementary csv files capturing the infobox information, we use the DBpedia infobox tql file. The DBPedia project has already parsed the Wikipedia XML dumps to get the infobox information, so we simply reuse this work for importing in GRISP. However, in the future, we might use another source of input for the infobox, or parse ourself the Wikipedia XML files. 

Basically the generated csv file contains a list of properties and relations as available in the infoboxes. Use the following command:

```
> mvn compile exec:exec -Dexec.classpathScope=compile -PbuildInfoboxEn
```

For other languages, replace the ending ```En```, but the appropriate lang code, e.g. for French:

```
> mvn compile exec:exec -Dexec.classpathScope=compile -PbuildInfoboxFr
```

