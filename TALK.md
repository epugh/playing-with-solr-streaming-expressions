### Startup

./solr start

./start start -e cloud

Create a collection.

```
./bin/post -c demo example/exampledocs/*.xml
```

### Hello World

echo("hello world")

select(
  echo("hello world"),
  echo as msg
)

random(demo,
     q="*:*",
     rows="1"
)


### Okay, lets make a new data set.

commit(demo2,
  update(demo2,
    random(demo,
         q="*:*",
         rows="1"
    )
  )
)

```
curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"bump", "type":"pint", "multiValued":false, "stored":true}}' http://localhost:8983/solr/demo2/schema
```

```
curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "bump",
    "class": "com.o19s.solr.streaming.BumpStream"
  }
}' http://localhost:8983/solr/demo2/config
```

curl "http://localhost:8983/solr/demo2/stream?action=plugins" | grep bump


commit(demo2,
  bump(demo2,
    id=id,
    batchSize=50,
    select(
      search(demo2,qt="/export",q="*:*",fl="id", sort="id asc"),
      id as id
    )
  )
)

### Okay, lets stream some data over hear.

daemon(
  id="12345",
  runInterval="10000",
  commit(demo2,
    update(demo2,
      random(demo,
           q="*:*",
           rows="1"
      )
    )
  )
)


### Let's check this out.

http://localhost:8983/solr/demo/stream?action=LIST


curl --data-urlencode 'expr=search(demo, q="*:*")' http://localhost:8983/solr/demo/stream
