FROM bigtruedata/sbt:0.13.15-2.12 AS sbt-build

WORKDIR /app

# fetch dependencies first in order to leverage build cache
COPY .sbt .sbt
COPY project project
COPY *.sbt ./
RUN sbt update

COPY src src
COPY .scalafmt.conf .

RUN sbt compile && \
  sbt test && \
  sbt universal:packageBin && \
  mv "target/universal/kafka-flightstream-streams-$(sbt -no-colors version | tail -1 | cut -d ' ' -f 2).zip" /app.zip

# end of build stage

FROM openjdk:8

WORKDIR /app

ENV JAVA_OPTS="-Xmx512m"

COPY --from=sbt-build /app.zip .
RUN unzip app.zip

COPY entrypoint.sh ./
RUN chmod u+x entrypoint.sh bin/kafka-flightstream-streams

ENTRYPOINT ["./entrypoint.sh"]