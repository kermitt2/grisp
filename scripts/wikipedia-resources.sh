#!/bin/bash

# Download all necessary resources from Wikidata/Wikipedia

# Download wikidata (around 70GB in Feb. 2022)
echo "Downloading latest Wikidata JSON dump"
wget -c https://dumps.wikimedia.org/wikidatawiki/entities/latest-all.json.bz2

# Donwload Wikipedia resources by language (around 44GB, in Feb. 2022 for the 7 indicated languages)

## declare an array variable
declare -a languages=("en" "de" "fr" "it" "es" "ar" "zh" "ja" "ru")

## now loop through the above array
for i in "${languages[@]}"
do
    echo "Downloading latest resources for language $i"
    wget -c https://dumps.wikimedia.org/${i}wiki/latest/${i}wiki-latest-pages-articles-multistream.xml.bz2
    wget -c https://dumps.wikimedia.org/${i}wiki/latest/${i}wiki-latest-langlinks.sql.gz
    wget -c https://dumps.wikimedia.org/${i}wiki/latest/${i}wiki-latest-page_props.sql.gz
done
