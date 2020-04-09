## Setting up Sample Solr Cluster

```
docker-compose up

docker exec solr1 solr create_collection -c books -p 8983

wget -O target/books.csv https://raw.githubusercontent.com/apache/lucene-solr/master/solr/example/exampledocs/books.csv

docker run --rm -v "$PWD/target:/target" --network=solr-streaming-expressions_solr solr:8 post -c books /target/books.csv -host solr1
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
}' http://localhost:8983/solr/gettingstarted/config
```

This only works in Solr 8.5
```
curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "dog",
    "class": "org.apache.solr.handler.CatStream"
  }
}' http://localhost:8983/solr/gettingstarted/config
```

To replace it do:
```
curl -X POST -H 'Content-type:application/json'  -d '{
  "update-expressible": {
    "name": "dog",
    "class": "org.apache.solr.handler.AnalyzeEvaluator"
  }
}' http://localhost:8983/solr/gettingstarted/config
```

To delete it do:
```
curl -X POST -H 'Content-type:application/json'  -d '{
  "delete-expressible": "dog"
}' http://localhost:8983/solr/gettingstarted/config
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
