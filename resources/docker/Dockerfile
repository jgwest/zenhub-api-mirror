FROM ubuntu:18.04 as builder

RUN apt-get -y update && apt-get install -y openjdk-8-jdk maven git

ADD . /root

RUN cd /root && \
	git clone https://github.com/jgwest/zenhub-api-java-client && \
	cd zenhub-api-java-client && mvn install -DskipTests && \
	cd .. && mvn package -f pom.xml

RUN cp /root/ZenHubApiMirrorService/target/ZenHubApiMirrorService.war /root/ZenHubApiMirrorService.war && \
    cp /root/resources/docker/server.xml /root/server.xml

FROM open-liberty:kernel-java8-ibmsfj

RUN mkdir -p /home/default/data && \
	chown -R default /home/default/data

VOLUME /home/default/data

ENV RANDFILE /config/.rnd

COPY --chown=1001:0 --from=builder /root/ZenHubApiMirrorService.war /config/dropins/
COPY --chown=1001:0 --from=builder /root/server.xml /config/

ARG SSL=true

RUN configure.sh

