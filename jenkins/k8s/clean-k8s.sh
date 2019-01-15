#!/usr/bin/env bash

CID=`docker container ls -aq -f "name=kubectl-support"`
if [ ! -z "${CID}" ]; then
# delete pods
    docker exec kubectl-support kubectl delete --all pods --namespace=${GCP_K8_CLUSTER_NAMESPACE}
# remove namespace
    docker exec kubectl-support kubectl delete namespaces ${GCP_K8_CLUSTER_NAMESPACE}

# check status of the pods in every 30 seconds
    PODS_STATUS=`docker exec kubectl-support kubectl get pods --namespace=${GCP_K8_CLUSTER_NAMESPACE}`
    while [ ! -z "${PODS_STATUS}" ]
    do
        sleep 30
        PODS_STATUS=`docker exec kubectl-support kubectl get pods --namespace=${GCP_K8_CLUSTER_NAMESPACE}`
    done
fi