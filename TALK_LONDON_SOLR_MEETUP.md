London Solr Meetup Talk
--------------------------

https://github.com/epugh/playing-with-solr-streaming-expressions/blob/master/TALK_LONDON_SOLR_MEETUP.md


# Setting up

```
docker-compose up --build

docker exec solr1 solr create_collection -c books -p 8983 -shards 3

mkdir temp
wget -O temp/books.csv https://raw.githubusercontent.com/apache/lucene-solr/master/solr/example/exampledocs/books.csv

docker run --rm -v "$PWD/temp:/target" --network=playing-with-solr-streaming-expressions_solr solr:8 post -c books /target/books.csv -host solr1
```

# Let's do the Hello World

## Hello World

echo("hello world")

select(
  echo("hello world"),
  echo as msg
)

search(books,
     q="*:*",
     rows="1"
)

_note, don't enable with explanation checkbox_
random(books,
     q="*:*",
     rows="1"
)

curl --data-urlencode 'expr=search(books, q="*:*")' http://localhost:8983/solr/books/stream  


# Okay, lets make a new data set.

Just books that are in stock!

docker exec solr1 solr create_collection -c books_instock -p 8983 -shards 3

commit(books_instock,
  update(books_instock,
    search(books,
         q="inStock:true"
    )
  )
)



commit(books_instock,
  delete(books_instock, batchSite=50,
    search(books_instock,
          q="*:*"
    )
  )
)

## How about a random sample set?

commit(books_instock,
  update(books_instock,
    random(books,
         q="inStock:true",
         rows="2"
    )
  )
)


## What about keeping one collection up to date with another?

docker exec solr1 solr create_collection -c books_copy -p 8983 -shards 3

daemon(
  id="12345",
  runInterval="15000",
  commit(books_copy,
    update(books_copy,
      random(books,
           q="*:*",
           rows="1"
      )
    )
  )
)

### Quick Detour into Lifecycle of a Daemon
curl 'http://localhost:8983/solr/books/stream?action=LIST'

curl 'http://localhost:8983/solr/books_copy/stream?action=LIST'

curl 'http://localhost:8983/solr/books/stream?action=STOP&id=12345'

curl 'http://localhost:8983/solr/books/stream?action=START&id=12345'

curl 'http://localhost:8983/solr/books/stream?action=KILL&id=12345'


# Okay, let's talk about Reindexing Your Data

We want to make Author names be case sensitive.  So a query for "card" doesn't match "Card" and vice versa.

## Setting up our Case Sensitive Text field

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field-type" : {
     "name":"case_sensitive_field",
     "class":"solr.TextField",
     "positionIncrementGap":"100",
     "analyzer" : {
        "charFilters":[{
           "class":"solr.PatternReplaceCharFilterFactory",
           "replacement":"$1$1",
           "pattern":"([a-zA-Z])\\\\1+" }],
        "tokenizer":{
           "class":"solr.WhitespaceTokenizerFactory" },
        "filters":[{
           "class":"solr.WordDelimiterFilterFactory",
           "preserveOriginal":"0" }]}}
}' http://localhost:8983/solr/books_copy/schema

curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-field":{
     "name":"author_case_sensitive",
     "type":"case_sensitive_field",
     "stored":true,
     "indexed":true }
}' http://localhost:8983/solr/books_copy/schema


curl -X POST -H 'Content-type:application/json' --data-binary '{
  "add-copy-field":{
     "source":"author",
     "dest":[ "author_case_sensitive"]}
}' http://localhost:8983/solr/books_copy/schema

## Setting up the Bump Command

curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"bump", "type":"pint", "multiValued":false, "stored":true}}' http://localhost:8983/solr/books_copy/schema


curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "bump",
    "class": "com.o19s.solr.streaming.BumpStream"
  }
}' http://localhost:8983/solr/books_copy/config

curl "http://localhost:8983/solr/books_copy/stream?action=plugins" | grep bump

## Now lets bump our Data

commit(books_copy,
  bump(books_copy,
    id=id,
    batchSize=2,
    select(
      search(books_copy,qt="/export",q="*:*",fl="id", sort="id asc"),
      id as id
    )
  )
)

author_case_sensitive:Card   <-- Matches
author_case_sensitive:card   <-- No Matches
bump field incremented!

# Lets look at a STreaming Expression!

convertTupleToSolrDocument Method
read Method

createBatchSummaryTuple Method

public BumpStream() method


# Useful Links
https://lucene.apache.org/solr/guide/8_5/streaming-expressions.html
https://github.com/apache/lucene-solr/blob/visual-guide/solr/solr-ref-guide/src/math-expressions.adoc#streaming-expressions-and-math-expressions
https://github.com/apache/lucene-solr/blob/visual-guide/solr/solr-ref-guide/src/logs.adoc#log-analytics
https://twitter.com/jbernste2
https://joelsolr.blogspot.com/2020/03/new-york-coronavirus-statistics-nytimes.html
https://rodrite.github.io/solr-topic-stream-function/
https://sematext.com/blog/solr-streaming-expressions-reindexing/
