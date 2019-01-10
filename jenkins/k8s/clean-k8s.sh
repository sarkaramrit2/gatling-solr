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