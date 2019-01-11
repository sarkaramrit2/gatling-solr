#!/usr/bin/env bash

#!/usr/bin/env bash

# stop containers
CID=`docker container ls -aq -f "name=kubectl-support"`
if [ ! -z "${CID}" ]; then
    docker container stop ${CID}
fi

# stop containers
CID=`docker container ls -aq -f "name=kubectl_support"`
if [ ! -z "${CID}" ]; then
    docker container stop ${CID}
fi


#remove containers
CID=`docker container ls -aq -f "name=kubectl-support"`
if [ ! -z "${CID}" ]; then
    docker container rm ${CID}
fi

#remove containers
CID=`docker container ls -aq -f "name=kubectl_support"`
if [ ! -z "${CID}" ]; then
    docker container rm ${CID}
fi

# remove all gatling solr dockers
IMG_ID=`docker images -a | grep "kubectl-support" | awk '{print $3}'`
if [ ! -z "${IMG_ID}" ]; then
    docker rmi -f ${IMG_ID}
fi

# remove all gatling solr dockers
IMG_ID=`docker images -a | grep "kubectl_support" | awk '{print $3}'`
if [ ! -z "${IMG_ID}" ]; then
    docker rmi -f ${IMG_ID}
fi