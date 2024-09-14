#!/bin/bash

# This script runs through the basic setup tasks for the zipper example.

set -e

# Ansi color code variables
ERROR='\033[0;31m[PLAYING] '
MAJOR='\033[0;34m[PLAYING] '
MINOR='\033[0;37m[PLAYING]    '
RESET='\033[0m' # No Color

curl --user solr:SolrRocks -X POST http://localhost:8983/api/collections -H 'Content-Type: application/json' -d'
  {
    "create": {
      "name": "zipper",
      "config": "_default",
      "numShards": 2,
      "replicationFactor": 1,
      "waitForFinalState": true
    }
  }
'


if [ ! -f ./books.csv ]; then
    echo -e "${MAJOR}Downloading the books sample data set.${RESET}"
    curl --progress-bar -o books.csv https://raw.githubusercontent.com/apache/solr/main/solr/example/exampledocs/books.csv

fi
echo -e "${MAJOR}Populating docs, please give it a few minutes!${RESET}"

docker cp books.csv solr1:/var/solr/data/userfiles/books.csv

curl http://localhost:8983/solr/zipper/stream --data-urlencode 'expr=
commit(zipper,
  update(zipper,
    parseCSV(
      cat('books.csv',  maxLines=500)
    )
  )
)
'


#tar xzf icecat-products-150k-20200809.tar.gz --to-stdout | curl 'http://localhost:8983/solr/ecommerce/update?commit=true' --data-binary @- -H 'Content-type:application/json'
