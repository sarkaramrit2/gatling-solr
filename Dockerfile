FROM openjdk:11-jre

MAINTAINER Amrit Sarkar <sarkaramrit2@gmail.com>

# working directory for gatling
WORKDIR /opt

# gating version
ENV GATLING_VERSION=3.0.0 \
    SOLR_VERSION=7.5.0 \
    SCALA_VERSION=2.12.7 \
    SBT_VERSION=1.2.1 \
    GATLING_SOLR_BRANCH=variant_1

# Install Scala
RUN \
  apt-get update && apt-get install -y --no-install-recommends apt-util && \
  apt-get update; apt-get install curl && \
  curl -fsL https://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /root/ && \
  echo >> /root/.bashrc && \
  echo "export PATH=~/scala-$SCALA_VERSION/bin:$PATH" >> /root/.bashrc

# Install sbt
RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

#install git and create gatling-solr library
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y git && \
    mkdir -p /tmp/downloads/gatling-solr && \
    cd /tmp/downloads/gatling-solr && \
    git clone https://github.com/sarkaramrit2/gatling-solr.git && \
    cd /tmp/downloads/gatling-solr/gatling-solr && \
    git checkout $GATLING_SOLR_BRANCH && \
    sbt assembly && \
    # install ps
    apt-get install procps -y && \
    cd /

# install gatling
RUN apt-get install wget bash && \
  wget -q -O /tmp/downloads/gatling-$GATLING_VERSION.zip \
  https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/$GATLING_VERSION/gatling-charts-highcharts-bundle-$GATLING_VERSION-bundle.zip && \
  mkdir -p /tmp/archive && cd /tmp/archive && \
  unzip /tmp/downloads/gatling-$GATLING_VERSION.zip && \
  mkdir -p /opt/gatling/ && \
  mv /tmp/archive/gatling-charts-highcharts-bundle-$GATLING_VERSION/* /opt/gatling/

# copy libraries, simulations, config files and remove tmp directly
RUN mkdir -p /opt/gatling/user-files/simulations/ && \
    mkdir -p /opt/gatling/user-files/configs/ && \
    cp /tmp/downloads/gatling-solr/gatling-solr/target/scala-2.12/gatling-solr-*.jar /opt/gatling/lib/ && \
    cp /tmp/downloads/gatling-solr/gatling-solr/src/test/scala/* /opt/gatling/user-files/simulations/ && \
    rm -rf /opt/gatling/user-files/simulations/computerdatabase && \
    cp /tmp/downloads/gatling-solr/gatling-solr/src/test/resources/configs/* /opt/gatling/user-files/configs/ && \
    cp -rf /tmp/downloads/gatling-solr/gatling-solr/src/test/resources/data /opt/gatling/user-files/ && \
    cp /tmp/downloads/gatling-solr/gatling-solr/src/test/resources/gatling.conf /opt/gatling/conf/ && \
    cp /tmp/downloads/gatling-solr/gatling-solr/src/test/resources/logback.xml /opt/gatling/conf/ && \
    cp /tmp/downloads/gatling-solr/gatling-solr/src/test/resources/recorder.conf /opt/gatling/conf/ && \
    rm -rf /tmp/*

# change context to gatling directory
WORKDIR  /opt/gatling

# set directories below to be mountable from host
VOLUME ["/opt/gatling/conf", "/opt/gatling/results", "/opt/gatling/user-files"]

# set environment variables
ENV PATH /opt/gatling/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ENV GATLING_HOME /opt/gatling

CMD tail -f /dev/null