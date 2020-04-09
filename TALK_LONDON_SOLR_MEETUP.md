London Solr Meetup Talk
--------------------------


# Setting up

```
docker-compose up --build

docker exec solr1 solr create_collection -c books -p 8983 -shards 3

mkdir temp
wget -O temp/books.csv https://raw.githubusercontent.com/apache/lucene-solr/master/solr/example/exampledocs/books.csv

docker run --rm -v "$PWD/temp:/target" --network=playing-with-solr-streaming-expressions_solr solr:8 post -c books /target/books.csv -host solr1
```

# Let's do the Hello World

### Hello World

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

### Okay, lets make a new data set.

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

### How about a random sample set?

commit(books_instock,
  update(books_instock,
    random(books,
         q="inStock:true",
         rows="2"
    )
  )
)







# Useful Links
https://lucene.apache.org/solr/guide/8_5/streaming-expressions.html
https://github.com/apache/lucene-solr/blob/visual-guide/solr/solr-ref-guide/src/math-expressions.adoc#streaming-expressions-and-math-expressions
https://twitter.com/jbernste2
https://joelsolr.blogspot.com/2020/03/new-york-coronavirus-statistics-nytimes.html
