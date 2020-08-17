#!/usr/bin/env bash

# stop containers
KUBECTL_SUPPORT=kubectl-support-${GCP_K8_CLUSTER_NAMESPACE}
CID=`docker container ls -aq -f "name=${KUBECTL_SUPPORT}"`
if [ ! -z "${CID}" ]; then
    docker container stop ${CID}
fi

#remove containers
CID=`docker container ls -aq -f "name=${KUBECTL_SUPPORT}"`
if [ ! -z "${CID}" ]; then
    docker container rm ${CID}
fi

# remove all gatling solr dockers
IMG_ID=`docker images -a | grep "${KUBECTL_SUPPORT}" | awk '{print $3}'`
if [ ! -z "${IMG_ID}" ]; then
    docker rmi -f ${IMG_ID}
fi