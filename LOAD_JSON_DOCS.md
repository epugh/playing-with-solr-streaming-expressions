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

```
curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "parseJSONL",
    "class": "com.o19s.solr.streaming.JSONLStream"
  }
}' http://localhost:9981/solr/icecat/config
```

Confirm it's there via:

```
curl 'http://localhost:9981/solr/icecat/stream?action=PLUGINS' | grep JSONL
```

```
curl http://localhost:9981/solr/icecat/stream --data-urlencode 'expr=
parseJSONL(
  cat('two_docs.jsonl',  maxLines=500)
)
'
```

The whole thing:

```
curl http://localhost:9981/solr/icecat/stream --data-urlencode 'expr=
commit(icecat,
  update(icecat,
    parseJSONL(
      cat('two_docs.jsonl',  maxLines=500)
    )
  )
)
'
```
