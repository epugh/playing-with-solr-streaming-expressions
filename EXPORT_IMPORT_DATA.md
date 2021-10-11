
Carefully pick your fields, there is no need to copy the ones that are then copyField'ed...

```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/admin/collections?action=REINDEXCOLLECTION&cmd=start&name=ecommerce&q=*:*&fl=id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier,attr_*&target=ecommerce2&async=a2"
```

Check status
```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/admin/collections?action=REQUESTSTATUS&requestid=a2"

```

List Daemons
```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/ecomm2/stream?action=list"
```

Kill Daemon
```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/ecomm2/stream?action=kill&id=ecomm2"
```


ABORT REINDEX!!!
```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/admin/collections?action=REINDEXCOLLECTION&name=ecommerce&target=ecommerce3&cmd=abort"
```


### Can we do the stream directly???  Instead of using the REINDEXCOLLECTION command?

* Make sure to create the `checkpointCollection` using `_default` before hand

```
daemon(id="ecomm2",
       runInterval="1000",
       terminate="true",
       commit(ecomm2,
           update(ecomm2,
                  batchSize=100,
                  topic(checkpointCollection,
                        ecommerce,
                        q="*:*",
                        fl="id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier",
                        id="topic_ecomm2",
                        initialCheckpoint=0
                  )
            )
      )
)
```


Okay, how do we load a topic????

```
topic(cp2,
      ecommerce,
      q="id:9*",
      fl="id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier",
      id="topic_ecomm3",
      initialCheckpoint=0
)
```

Try exporting

bin/solr export -fields id,name -format javabin -limit 100 -url http://localhost:8983/solr/ecommerce -verbose

```
# first dump main data
bin/solr export -fields id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier -format javabin -limit -1 -url http://solr1:8983/solr/ecommerce  -out /tmp/fake_shared_fs/ecom_dump_main.javabin -verbose

# now dump attributes
bin/solr export -fields id,attr_* -format javabin -limit -1 -url http://solr1:8983/solr/ecommerce  -out /tmp/fake_shared_fs/ecom_dump_attributes.javabin -verbose
```

what about json.gz format?
```
# first dump main data
bin/solr export -fields id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier -format jsonl -limit -1 -url http://solr1:8983/solr/ecommerce  -out /tmp/fake_shared_fs/ecom_dump_main.json.gz -verbose

# now dump attributes
bin/solr export -fields id,attr_* -format jsonl -limit -1 -url http://solr1:8983/solr/ecommerce  -out /tmp/fake_shared_fs/ecom_dump_attributes.json.gz -verbose
```

Smallest!

curl -X POST -d @/Users/epugh/Documents/projects/chorus/volumes/fake_shared_fs/ecom_dump_main.jsonl http://localhost:8983/solr/test_load/update/json/docs?commit=true

Pukes on jsonl!

curl -X POST --header "Content-Type: application/javabin" --data-binary @/Users/epugh/Documents/projects/chorus/volumes/fake_shared_fs/ecom_dump_main.javabin http://localhost:8983/solr/test_load/update?commit=true




Okay, lets try JSONLStream!

curl "http://localhost:8983/solr/ecomm3/stream?action=plugins" | grep jsonl

curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "jsonl",
    "class": "com.o19s.solr.streaming.JSONLStream"
  }
}' http://localhost:8983/solr/ecomm3/config



Holy smokes!
```
jsonl(
  cat("ecom_dump_main.jsonl")
)
```


Holy smokes!
```
select(
  jsonl(
    cat("ecom_dump_main.jsonl")
  ),
  id,
  name
)
```

```
select(
  jsonl(
    cat("ecom_dump_attributes.jsonl")
  ),
  id
)
```

Not in same order...   

```
hashJoin(
  select(
    jsonl(
      cat("ecom_dump_main.jsonl")
    ),
    id,
    name
  ),
  hashed=select(
    jsonl(
      cat("ecom_dump_attributes.jsonl")
    ),
    id
  ),
  on="id"
)
```

OMG WORKING!
```
hashJoin(
  jsonl(
    cat("ecom_dump_main.jsonl")
  ),
  hashed=jsonl(
      cat("ecom_dump_attributes.jsonl")
  ),
  on="id"
)
```


Okay, save it!

```
commit(ecomm3,
  update(ecomm3,
    hashJoin(
      jsonl(
        cat("ecom_dump_main.jsonl")
      ),
      hashed=jsonl(
          cat("ecom_dump_attributes.jsonl")
      ),
      on="id"
    )
  )
)
```

how fast can we do it

```
time curl http://localhost:8983/solr/ecomm3/stream --data-urlencode 'expr=
update(ecomm4,
  hashJoin(
    jsonl(
      cat("ecom_dump_main.json.gz")
    ),
    hashed=jsonl(
        cat("ecom_dump_attributes.json.gz")
    ),
    on="id"
  )
)
'
```

takes 11 or 12 seconds.

```
time curl http://localhost:8983/solr/ecomm3/stream --data-urlencode 'expr=
update(ecomm4,
  hashJoin(
    jsonl(
      cat("ecom_dump_main.jsonl")
    ),
    hashed=jsonl(
        cat("ecom_dump_attributes.jsonl")
    ),
    on="id"
  )
)
'
```

takes 13 or 14 seconds
