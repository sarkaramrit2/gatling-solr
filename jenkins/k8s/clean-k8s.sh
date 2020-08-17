#!/usr/bin/env bash

KUBECTL_SUPPORT=kubectl-support-${GCP_K8_CLUSTER_NAMESPACE}

if [ "$IMPLICIT_CLUSTER" = true ] ; then
    CID=`docker container ls -aq -f "name=${KUBECTL_SUPPORT}"`
    # extra layer of check to make sure namespace with jenkins gets deleted
    # TODO: remove this hardcoded check for future
    if [ ! -z "${CID}" -a "${GCP_K8_CLUSTER_NAMESPACE}" == "jenkins" ]; then
    # delete pods
        docker exec ${KUBECTL_SUPPORT} kubectl delete --all pods --namespace=${GCP_K8_CLUSTER_NAMESPACE}
    # remove namespace
        docker exec ${KUBECTL_SUPPORT} kubectl delete namespaces ${GCP_K8_CLUSTER_NAMESPACE}

    # check status of the pods in every 30 seconds
        PODS_STATUS=`docker exec ${KUBECTL_SUPPORT} kubectl get pods --namespace=${GCP_K8_CLUSTER_NAMESPACE}`
        while [ ! -z "${PODS_STATUS}" ]
        do
            sleep 30
            PODS_STATUS=`docker exec ${KUBECTL_SUPPORT} kubectl get pods --namespace=${GCP_K8_CLUSTER_NAMESPACE}`
        done
    fi
fi

# delete gatling-solr service and statefulsets, redundant step
docker exec ${KUBECTL_SUPPORT} kubectl delete statefulsets gatlingsolr --namespace=${GCP_K8_CLUSTER_NAMESPACE} || echo "gatling statefulsets not available!!"
docker exec ${KUBECTL_SUPPORT} kubectl delete service gatlingsolr --namespace=${GCP_K8_CLUSTER_NAMESPACE} || echo "gatling service not available!!"