#!/bin/bash

# This script starts up Playing with Streaming Expressions and runs through the basic setup tasks.

set -e

# Ansi color code variables
ERROR='\033[0;31m[PLAYING] '
MAJOR='\033[0;34m[PLAYING] '
MINOR='\033[0;37m[PLAYING]    '
RESET='\033[0m' # No Color


if ! [ -x "$(command -v curl)" ]; then
  echo '${ERROR}Error: curl is not installed.${RESET}' >&2
  exit 1
fi
if ! [ -x "$(command -v docker-compose)" ]; then
  echo '${ERROR}Error: docker-compose is not installed.${RESET}' >&2
  exit 1
fi
if ! [ -x "$(command -v jq)" ]; then
  echo '${ERROR}Error: jq is not installed.${RESET}' >&2
  exit 1
fi
if ! [ -x "$(command -v zip)" ]; then
  echo 'Error: zip is not installed.' >&2
  exit 1
fi

shutdown=false

while [ ! $# -eq 0 ]
do
	case "$1" in
		--help | -h)
      echo -e "Use the option --shutdown | -s to shutdown and remove the Docker containers and data."
			exit
			;;
    --shutdown | -s)
			shutdown=true
      echo -e "${MAJOR}Shutting down Playing with Streaming Expressions${RESET}"
			;;
	esac
	shift
done

services="solr1 solr2 solr3"

docker-compose down -v
if $shutdown; then
  exit
fi

docker-compose up -d --build ${services}

echo -e "${MAJOR}Waiting for Solr cluster to start up and all three nodes to be online.${RESET}"
./solr/wait-for-solr-cluster.sh # Wait for all three Solr nodes to be online

echo -e "${MAJOR}Setting up security in solr${RESET}"
echo -e "${MINOR}copy security.json into image${RESET}"
docker cp ./solr/security.json solr1:/security.json
echo -e "${MINOR}upload security.json to zookeeper${RESET}"
docker exec solr1 solr zk cp /security.json zk:security.json -z zoo1:2181

echo -e "${MINOR}wait for security.json to be available to Solr${RESET}"
./solr/wait-for-zk-200.sh

#echo -e "${MAJOR}Package ecommerce configset.${RESET}"
#(cd solr/configsets/ecommerce/conf && zip -r - *) > ./solr/configsets/ecommerce.zip
#echo -e "${MINOR}post ecommerce.zip configset${RESET}"
#curl  --user solr:SolrRocks -X PUT --header "Content-Type:application/octet-stream" --data-binary @./solr/configsets/ecommerce.zip "http://localhost:8983/api/cluster/configs/ecommerce"
#echo -e "${MAJOR}Create ecommerce collection.${RESET}"

echo -e "${MAJOR}Welcome to Playing with Streaming Expressions!${RESET}"
