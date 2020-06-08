# Pre-processed files with Hadoop
 
In this section we provide datasetes already processed with the hadoop job.

## CSV files

These files contains the wikipedia dump files and the translations: 

- FR: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/fr-20170620.zip
- DE: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/de-20170620.zip 
- EN: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/en-20170620.zip

To use them, just unzip them in a directory, the language specific information will be installed respectively in the `xx` (with xx the language name) directories.  

Wikipedia language specific content and ids: 

- Wikidata IDs: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/wikidataIds-20170629.zip

- FR: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/fr-wikidata-20170629.zip
- DE: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/de-wikidata-20170629.zip
- EN: https://s3.eu-central-1.amazonaws.com/storagescienceminer/NERD/nerd-data-files/en-wikidata-20170629.zip

To install them, unzip them in the root directory where you previously unzipped the language specific wikipedia csv files.
The wikidata IDs text file will stay in the root directory while the wikidata language specific files will be installed in language sub directories. 

Here how the tree should look like from the root directory: 

```
.
├── de
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── sentenceSplits.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata.txt
├── en
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── sentenceSplits.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata.txt
├── fr
│   ├── articleParents.csv
│   ├── categoryParents.csv
│   ├── childArticles.csv
│   ├── childCategories.csv
│   ├── label.csv
│   ├── page.csv
│   ├── pageLabel.csv
│   ├── pageLinkIn.csv
│   ├── pageLinkOut.csv
│   ├── redirectSourcesByTarget.csv
│   ├── redirectTargetsBySource.csv
│   ├── sentenceSplits.csv
│   ├── stats.csv
│   ├── translations.csv
│   └── wikidata.txt
└── wikidataIds.csv  
```

# Original sources

## Wikipedia dumps
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/enwiki/20170620/enwiki-20170620-pages-articles.xml.bz2
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/dewiki/20170620/dewiki-20170620-pages-articles.xml.bz2
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/frwiki/20170620/frwiki-20170620-pages-articles.xml.bz2

## Wikipedia additional files

### Translation files
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/enwiki/20170620/enwiki-20170620-langlinks.sql.gz
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/dewiki/20170620/dewiki-20170620-langlinks.sql.gz
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/frwiki/20170620/frwiki-20170620-langlinks.sql.gz


## Wikidata dump

Wikipedia dump, updated every day
- https://dumps.wikimedia.org/wikidatawiki/entities/latest-all.json.bz2

Language specific resources: 
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/enwiki/20170620/enwiki-20170620-page_props.sql.gz
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/dewiki/20170620/dewiki-20170620-page_props.sql.gz
- http://ftp.acc.umu.se/mirror/wikimedia.org/dumps/frwiki/20170620/frwiki-20170620-page_props.sql.gz
