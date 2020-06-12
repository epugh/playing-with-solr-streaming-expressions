## Setting up Sample Solr Cluster

```
docker-compose up --build

docker exec solr1 solr create_collection -c books -p 8983 -shards 3

mkdir temp
wget books.csv https://raw.githubusercontent.com/apache/lucene-solr/master/solr/example/exampledocs/books.csv

docker run --rm -v "$PWD/temp:/target" --network=playing-with-solr-streaming-expressions_solr solr:8 post -c books /target/books.csv -host solr1
```

## Configure Solr to use new Jar

Add to your `solrconfig.xml`:

```
<expressible name="bump" class="com.o19s.solr.streaming.BumpStream" />
```

or

```
curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "bump",
    "class": "com.o19s.solr.streaming.BumpStream"
  }
}' http://localhost:8983/solr/books/config
```

Confirm it's there via:

```
curl 'http://localhost:8983/solr/books/stream?action=PLUGINS' | grep bump
```


http://localhost:8983/solr/gettingstarted/stream?action=PLUGINS

To delete it do:
```
curl -X POST -H 'Content-type:application/json'  -d '{
  "delete-expressible": "bump"
}' http://localhost:8983/solr/books/config
```

Create the bump field using the API.

```
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"bump", "type":"pint", "multiValued":false, "stored":true}}' http://localhost:8983/solr/gettingstarted/schema
```

Then you can do:

```
curl http://localhost:8983/solr/gettingstarted/stream --data-urlencode 'expr=
commit(gettingstarted,
  bump(gettingstarted,
    id=id,
    batchSize=50,
    select(
      search(gettingstarted,qt="/export",q="*:*",fl="id", sort="id asc"),
      id as id
    )
  )
)
'
```




## Notes

* We should be using https://lucene.apache.org/solr/guide/8_4/package-manager.html#package-manager

Manual curl
curl http://localhost:8983/solr/documents/update -d '
[
 {"id"         : "alvarez20140715a.pdf_4",
  "bump"   : {"inc":3}
 }
]'

curl http://localhost:8983/solr/documents/get?id=GPRAreport2008-2009_frb.pdf_2


curl -X POST -H 'Content-type:application/json'  -d '{
  "add-requesthandler": {
    "name": "/mypath",
    "class": "solr.DumpRequestHandler",
    "defaults":{ "x": "y" ,"a": "b", "rows":10 },
    "useParams": "x"
  }
}' http://localhost:8983/solr/books/config


```
curl http://localhost:8983/solr/gettingstarted/stream?action=plugins
```


## Notes

* The `fq` parameter isn't documented for https://lucene.apache.org/solr/guide/8_5/stream-source-reference.html#search-parameters.   Wonder what others aren't, and how to convey them better?
