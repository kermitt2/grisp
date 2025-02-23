#!/bin/bash

## usage: wikipedia-resources.sh [path to root install path of GRISP] [path where to store resource data]
## example: ./wikipedia-resources.sh /home/lopez/grisp/ /media/lopez/wikipedia/

## absolute path to grisp install
grisp_install=$(cd "$(dirname "$1")"; pwd)/$(basename "$1")
echo "$grisp_install"

## absolute path where to download and install data resources
data_path=$(cd "$(dirname "$2")"; pwd)/$(basename "$2")
echo "$data_path"

mvn=/opt/maven/bin/mvn

## Download all necessary resources from Wikidata/Wikipedia

## Download wikidata (around 74GB in Dec. 2022)
echo "Downloading latest Wikidata JSON dump"
cd $data_path
mkdir wikidata
cd wikidata
wget -c https://dumps.wikimedia.org/wikidatawiki/entities/latest-all.json.bz2

## Download all the Wikipedia resources by language (a bit less than 50GB, in Feb. 2022 for the 9 indicated languages)

## declare an array variable
declare -a languages=("en" "de" "fr" "it" "es" "ar" "zh" "ja" "ru" "pt" "fa" "uk" "sv" "hi" "bn" "nl")

## now loop through the language array to download resources
cd $data_path
for i in "${languages[@]}"
do
    echo "Downloading latest resources for language ${i}..."
    mkdir ${i}
    cd ${i}
    wget -c https://dumps.wikimedia.org/${i}wiki/latest/${i}wiki-latest-pages-articles-multistream.xml.bz2
    wget -c https://dumps.wikimedia.org/${i}wiki/latest/${i}wiki-latest-langlinks.sql.gz
    wget -c https://dumps.wikimedia.org/${i}wiki/latest/${i}wiki-latest-page_props.sql.gz
    cd ..
done

## loop through the language array to create Wikidata property files for each language
echo "Creating wikidata property name files for each language..."
cd $data_path
for i in "${languages[@]}"
do
    echo "Generate Wikidata property file for language $i"
    cd ${i}
    wget "https://query.wikidata.org/sparql?format=json&query=SELECT%20%3Fproperty%20%3FpropertyLabel%20WHERE%20%7B%0A%20%20%20%20%3Fproperty%20a%20wikibase%3AProperty%20.%0A%20%20%20%20SERVICE%20wikibase%3Alabel%20%7B%0A%20%20%20%20%20%20bd%3AserviceParam%20wikibase%3Alanguage%20%22${i}%22%20.%0A%20%20%20%7D%0A%20%7D%0A%0A" -O wikidata-properties.json
    cd ..
done

## get word and entity embeddings - for the moment only for French
cd $data_path
wget -c -O fr/embeddings.quantized.bz2 https://wikipedia2vec.s3.amazonaws.com/models/fr/2018-04-20/frwiki_20180420_300d.txt.bz2

## loop through the language array to create translation files for each target language
echo "Creating translation files for each language..."
cd $grisp_install
## next is for conservative checking, but useless
$mvn clean package
for i in "${languages[@]}"
do
    $mvn compile exec:exec -PbuildTranslation -Dlang="${i}" -Dinput="${data_path}/${i}/${i}wiki-latest-langlinks.sql.gz" -Doutput="${data_path}/${i}/"
done

## creating Wikidata knowledge base backbone and language-specific mapping, this is a one time process for every supported languages
echo "Creating Wikidata knowledge base backbone and language-specific mapping..."
cd $grisp_install
$mvn compile exec:exec -PbuildWikidata -Dinput="${data_path}/wikidata/latest-all.json.bz2" -Doutput="${data_path}/"
