Look at https://github.com/apache/lucene-solr/blob/visual-guide/solr/solr-ref-guide/src/logs.adoc


# Make datafile
used https://tools.knowledgewalls.com/online-multiline-to-single-line-converter

Grab second doc via:
> jq ".[2]" icecat-products-150k-20200607.json

# Setting up

```
docker-compose up --build

docker exec solr1 solr create_collection -c icecat -p 8983 -shards 3


docker cp two_docs.json solr1:/var/solr/data/userfiles/
docker cp two_docs.jsonl solr1:/var/solr/data/userfiles/
```

# Load the jsonl formatted data

```
cat('two_docs.jsonl',  maxLines=500)
```

# Register the expression.
