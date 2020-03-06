# Extend vert.x image
FROM vertx/vertx3

ENV VERTICLE_NAME dev.hekate.vectortiles.MainVerticle
ENV VERTICLE_FILE vectortiles-1.0.0-SNAPSHOT-fat.jar

ENV VERTICLE_HOME /usr/verticles

EXPOSE 8752

COPY target/$VERTICLE_FILE $VERTICLE_HOME/

WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $VERTICLE_FILE"]
