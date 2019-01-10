#!/usr/bin/env bash

#!/usr/bin/env bash


CID=`docker container ls -aq -f "name=kubectl_support"`
if [ ! -z "${CID}" ]; then
# delete pods
    docker exec kubectl_support kubectl delete --all pods --namespace=jenkins
# remove namespace
    docker exec kubectl_support kubectl delete namespaces jenkins

# check status of the pods in every 30 seconds
    PODS_STATUS=echo `docker exec kubectl_support kubectl get pods --namespace=jenkins`
    while [ "${PODS_STATUS}" != "No resources found." ]
    do
        sleep 30
        PODS_STATUS=echo `docker exec kubectl_support kubectl get pods --namespace=jenkins`
    done
fi


# stop containers
CID=`docker container ls -aq -f "name=kubectl_support"`
if [ ! -z "${CID}" ]; then
    docker container stop ${CID}
fi

#remove containers
CID=`docker container ls -aq -f "name=kubectl_support"`
if [ ! -z "${CID}" ]; then
    docker container rm ${CID}
fi

# remove all gatling solr dockers
IMG_ID=`docker images -a | grep "kubectl_support" | awk '{print $3}'`
if [ ! -z "${IMG_ID}" ]; then
    docker rmi -f ${IMG_ID}
fi