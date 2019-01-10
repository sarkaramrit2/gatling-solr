FROM openjdk:11-jre

MAINTAINER Amrit Sarkar <sarkaramrit2@gmail.com>

# working directory for jenkins load testing
WORKDIR /opt

ENV BRANCH=master

# Install Scala
RUN \
    export CLOUD_SDK_REPO="cloud-sdk" && \
    echo "deb http://packages.cloud.google.com/apt $CLOUD_SDK_REPO main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add - && \
    apt-get update -y && apt-get install google-cloud-sdk -y && \
    apt-get install kubectl -y && \
    cd /opt/ && wget -o ........ && \
    cd /opt/ && wget -o ........ && cd /
    
CMD tail -f /dev/null