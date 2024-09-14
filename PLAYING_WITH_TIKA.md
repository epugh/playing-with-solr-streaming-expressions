> docker run -d -p 9998:9998 apache/tika:latest-full


> docker build . -t tika-to-the-max

> docker run -d -p 9998:9998 tika-to-the-max:latest
> docker run -p 9998:9998 --network host tika-to-the-max:latest


Lets get SpacY!

> docker run -p "127.0.0.1:8080:80" jgontrum/spacyapi:en_v2

docker run -p "8080:80" jgontrum/spacyapi:en_v2   

>curl -s http://localhost:8080/dep -d '{"text":"Pastafarians are smarter than people with Coca Cola bottles.", "model":"en"}'

Entity extraction:
> curl -s http://localhost:8080/ent -d '{"text":"Pastafarians are smarter than people with Coca Cola bottles.", "model":"en"}'

Get models:

> curl -s http://localhost:8080/en/schema


Without using spaCy, just OpenNLP and CoreNLP we discover:
Number of Entities Seen: 135
Number of Entity Types Discovered: 7,[NER_PERSON, NER_DATE, NER_MONEY, NER_ORGANIZATION, NER_PERCENT, NER_TIME, NER_LOCATION]


With spaCy and OpenNLP and CoreNLP we discover:
Number of Entities Seen: 246
Number of Entity Types Discovered: 13,[NER_PERSON, NER_DATE, NER_CARDINAL, NER_LOC, NER_PERCENT, NER_TIME, NER_LAW, NER_GPE, NER_MONEY, NER_ORG, NER_ORGANIZATION, NER_NORP, NER_LOCATION]

However:
NER_ORG = NER_ORGANIZATION
NER_NORP = NER_ORG?
NER_LOCATION = NER_LOC


More full Featured Example:

```
docker-compose up --build



mkdir temp
wget -O temp/books.csv https://raw.githubusercontent.com/apache/lucene-solr/master/solr/example/exampledocs/books.csv

docker exec solr1 solr create_collection -c books -p 8983 -shards 3
docker run --rm -v "$PWD/temp:/target" --network=playing-with-solr-streaming-expressions_solr solr:8 post -c books /target/books.csv -host solr1
docker exec solr1 solr create_collection -c books_ner -p 8983 -shards 3

curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"NER", "type":"string", "multiValued":false, "stored":true}}' http://localhost:8983/solr/books_ner/schema


curl -X POST -H 'Content-type:application/json'  -d '{
  "add-expressible": {
    "name": "spacy",
    "class": "com.o19s.solr.streaming.SpaCyStream"
  }
}' http://localhost:8983/solr/books_ner/config

curl "http://localhost:8983/solr/books_ner/stream?action=plugins" | grep spacy
```
commit(books_ner,
  update(books_ner,
    spacy(
      spacyUrl="http://spacy:80",
      fl="series_t",
      search(books,
           q="*:*"
      )
    )
  )
)
