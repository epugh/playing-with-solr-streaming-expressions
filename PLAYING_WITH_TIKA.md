> docker run -d -p 9998:9998 apache/tika:latest-full


> docker build . -t tika-to-the-max

> docker run -d -p 9998:9998 tika-to-the-max:latest
> docker run -p 9998:9998 tika-to-the-max:latest


Lets get SpacY!

> docker run -p "127.0.0.1:8080:80" jgontrum/spacyapi:en_v2

docker run -p "8080:80" jgontrum/spacyapi:en_v2   

>curl -s http://localhost:8080/dep -d '{"text":"Pastafarians are smarter than people with Coca Cola bottles.", "model":"en"}'

Entity extraction:
> curl -s http://localhost:8080/ent -d '{"text":"Pastafarians are smarter than people with Coca Cola bottles.", "model":"en"}'

Get models:

> curl -s http://localhost:8080/en/schema
