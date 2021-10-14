
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
bin/solr export -fields id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier -format javabin -limit -1 -url http://solr1:8983/solr/ecommerce  -out /var/solr/data/userfiles/ecom_dump_main.javabin -verbose

# now dump attributes
bin/solr export -fields id,attr_* -format javabin -limit -1 -url http://solr1:8983/solr/ecommerce  -out /var/solr/data/userfiles/ecom_dump_attributes.javabin -verbose
```

what about json.gz format?
```
# first dump main data
bin/solr export -fields id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier -format jsonl -limit -1 -url http://solr1:8983/solr/ecommerce  -out /var/solr/data/userfiles/ecom_dump_main.json.gz -verbose

# now dump attributes
bin/solr export -fields id,attr_* -format jsonl -limit -1 -url http://solr1:8983/solr/ecommerce  -out /var/solr/data/userfiles/ecom_dump_attributes.json.gz -verbose
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


# lets check ids
bin/solr export -fields id -format jsonl -limit -1 -url http://solr1:8983/solr/ecommerce  -out /var/solr/data/userfiles/dump_ids.json -verbose

# do it twice
bin/solr export -fields id -format jsonl -limit -1 -url http://solr1:8983/solr/ecommerce  -out /var/solr/data/userfiles/dump_ids2.json -verbose

Dangit, they aren't in order, because we have in the Export tool two producers but only one sink!!!!  So results are interleaved.


# Time to bring in the big guns.

Instead of cat, we'll have dog:

cat("authors.txt") --> dog("authors.json")



```
daemon(id="ecomm2",
       runInterval="1000",
       terminate="true",      
       dog("export.jsonl.gz",
              batchSize=100,
              topic(checkpointCollection,
                    cr_search,
                    q="id:9*",
                    fl="id,ean",
                    id="topic_export1",
                    initialCheckpoint=0
              )
      )
)
```

# Setup the DogStream

curl "http://localhost:8983/solr/worker/stream?action=plugins" | grep dog

curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "dog",
    "class": "com.o19s.solr.streaming.DogStream"
  }
}' http://localhost:8983/solr/worker/config



```
dog("output.json",
  echo("Hello world")
)
```

```
dog("output.jsonl.gz",
  search(ecommerce)
)
```

```
daemon(id="export5",
       runInterval="1000",
       terminate="true",      
       dog("ids.jsonl",
              batchSize=100,
              topic(checkpointCollection,
                    ecommerce,
                    q="*:*",
                    fl="id",
                    id="topic_export5",
                    initialCheckpoint=0
              )
      )
)
```

```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/ecommerce/stream?action=list"

```


# make a worker

Need to grab the first node and then use it.

```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/admin/collections?action=CREATE&name=checkpointCollection&numShards=1&collection.configName=_default&waitForFinalState=true"

curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/admin/collections?action=CREATE&name=worker&numShards=1&collection.configName=_default&waitForFinalState=true"

curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/admin/collections?action=CREATE&name=worker&numShards=1&createNodeSet=192.168.192.7:8983_solr&collection.configName=_default&waitForFinalState=true"

curl --user solr:SolrRocks -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "dog",
    "class": "com.o19s.solr.streaming.DogStream"
  }
}' http://localhost:8983/solr/worker/config

```

WE SHOULD UPDATE ABOUT USING WORKER SO YOU CAN HAVE PREDICTABLE PLACE FOR DAEMONS ETC!!!!


192.168.144.7:8983_solr


#  Do IDS get exported in predictable order?
Well, they are NOT in order, but they are repeateable.  I think this is becasue we are sorting by the underlying Lucene doc version, not the actual ID.  Just run this command twice (changing up the ids and the file name to see!)

```
daemon(id="export_ids_1",
       runInterval="1000",
       terminate="true",      
       dog("export_ids_1.jsonl",
              batchSize=100,
              topic(checkpointCollection,
                    ecommerce,
                    q="*:*",
                    fl="id",
                    id="export_ids_1",
                    initialCheckpoint=0
              )
      )
)
```


Okay, can we use a sort on ID?
NOPE!  No sort on topic() ;-()
```
daemon(id="export_ids_1",
       runInterval="1000",
       terminate="true",      
       dog("export_ids_1.jsonl",
              batchSize=100,
              topic(checkpointCollection,
                    ecommerce,
                    q="*:*",
                    fl="id",
                    sort="id ASC",
                    id="export_ids_1",
                    initialCheckpoint=0
              )
      )
)
```

Time to get crazy.  We topic stream out just the ids, feeding them through a sort....  yep, all ids in memory.

```
daemon(id="sorted_ids_1",
       runInterval="1000",
       terminate="true",      
       dog("sorted_ids_1.jsonl",
              batchSize=100,
              sort(
                topic(checkpointCollection,
                      ecommerce,
                      q="*:*",
                      fl="id",
                      sort="id ASC",
                      id="sorted_ids_1",
                      initialCheckpoint=0
                ),
                by="id asc"
              )
      )
)
```


Okay, can we add in a fetch?

```
daemon(id="sorted_ids_main_data_2",
       runInterval="1000",
       terminate="true",      
       dog("sorted_ids_main_data_2.jsonl",
              batchSize=100,
              fetch(ecommerce,
                sort(
                  topic(checkpointCollection,
                        ecommerce,
                        q="id:9*",
                        fl="id",
                        sort="id ASC",
                        id="sorted_ids_main_data_2",
                        initialCheckpoint=0
                  ),
                  by="id asc"
                ),
                fl="id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier",
                on="id=id"
              )
      )
)
```


```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/solr/worker/stream?action=list"
```


Fetching is producing this, due to how Chorus is setup we add dismax etc...  blowing it up.

q={! df=id q.op=OR cache=false } 900682 900696 901601 901826 902136 903638 904547 904584 904590 904631 904792 904802 905467 905473 907066 907270 908341 90841 91356 91424 915112 91578 916396 916547 916816 918393 918797 918807 918813 91949 919648 919660 919714 920624 92065 923852 925984 934080 93582 937166 938080 938296 941796 94372 9460 94945 94979 9514 95939 96613&distrib=false&fl=id,name,title,ean,price,short_description,img_high,img_low,img_500x500,img_thumb,date_released,supplier,_version_&sort=_version_ desc&rows=50&wt=json&version=2.2

```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/api/collections/ecommerce/get?id=900682"
```

Lets grab all of them!
```
curl --user solr:SolrRocks -X GET  -H 'Content-Type: application/json' "http://localhost:8983/api/collections/ecommerce/get?ids=900682,900696,901601,901826,902136,903638,904547,904584,904590,904631,904792,904802,905467,905473,907066,907270,908341,90841,91356,91424,915112,91578,916396,916547,916816,918393,918797,918807,918813,91949,919648,919660,919714,920624,92065,923852,925984,934080,93582,937166,938080,938296,941796,94372,9460,94945,94979,9514,95939,96613"
```
