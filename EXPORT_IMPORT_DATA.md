
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

```
top(
  n=10,
  topic(cp2,
        ecommerce,
        q="id:9*",
        fl="id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier",
        id="topic_ecomm3",
        initialCheckpoint=0
  ),
  sort="id ASC"
)

```

list(
  topic(cp2,
        ecommerce,
        q="id:9*",
        fl="id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier",
        id="topic_ecomm3",
        initialCheckpoint=0
  )
)
