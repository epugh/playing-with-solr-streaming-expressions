cd streaming_expressions
mvn package -Dmaven.test.skip=true
cp target/stream*.jar ../solr/lib

cd ..
docker-compose down -v
docker-compose up -d --build
sleep 30 # takes a while to start everything.

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
