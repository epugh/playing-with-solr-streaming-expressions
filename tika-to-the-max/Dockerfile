FROM apache/tika:1.24.1-full

# OpenNLP Setup

ENV NER_RES="/tika/tika-ner-resources"
RUN mkdir -p $NER_RES
WORKDIR $NER_RES

ENV PATH_PREFIX="$NER_RES/org/apache/tika/parser/ner/opennlp"
ENV URL_PREFIX="http://opennlp.sourceforge.net/models-1.5"

RUN mkdir -p $PATH_PREFIX

RUN wget "$URL_PREFIX/en-ner-person.bin" -O $PATH_PREFIX/ner-person.bin
RUN wget "$URL_PREFIX/en-ner-location.bin" -O $PATH_PREFIX/ner-location.bin
RUN wget "$URL_PREFIX/en-ner-organization.bin" -O $PATH_PREFIX/ner-organization.bin
RUN wget "$URL_PREFIX/en-ner-date.bin" -O $PATH_PREFIX/ner-date.bin


# CoreNLP Setup

COPY tika-ner-corenlp-addon-1.0-SNAPSHOT-jar-with-dependencies.jar /tika/tika-ner-corenlp-addon-1.0-SNAPSHOT-jar-with-dependencies.jar

COPY tika-config.xml /etc/tika-config.xml

# spaCy Setup

COPY streaming-expressions-1.0.0-solr8.5.0-SNAPSHOT.jar /tika/streaming-expressions-1.0.0-solr8.5.0-SNAPSHOT.jar
RUN mkdir -p $NER_RES/com/o19s/tika/parser/spacy/
COPY SpacyServer.properties $NER_RES/com/o19s/tika/parser/spacy/

#ENTRYPOINT java -jar /tika-server-${TIKA_VERSION}.jar -h 0.0.0.0
# OpenNLP
#ENTRYPOINT java -Dner.impl.class=org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser,org.apache.tika.parser.ner.regex.RegexNERecogniser -classpath /tika/tika-ner-resources/:/tika-server-${TIKA_VERSION}.jar org.apache.tika.server.TikaServerCli --config /etc/tika-config.xml -enableUnsecureFeatures -h 0.0.0.0

ENTRYPOINT java -Dner.impl.class=org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser,org.apache.tika.parser.ner.opennlp.OpenNLPNERecogniser,com.o19s.tika.parser.spacy.SpacyNERecogniser -classpath /tika/tika-ner-resources/:/tika/streaming-expressions-1.0.0-solr8.5.0-SNAPSHOT.jar:/tika/tika-ner-corenlp-addon-1.0-SNAPSHOT-jar-with-dependencies.jar:/tika-server-${TIKA_VERSION}.jar org.apache.tika.server.TikaServerCli --config /etc/tika-config.xml -enableUnsecureFeatures -h 0.0.0.0
