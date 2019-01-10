#!/usr/bin/env bash

# delete pods
kubectl delete --all pods --namespace=jenkins
# remove namespace
kubectl delete namespaces jenkins

# check status of the pods in every 30 seconds
PODS_STATUS=echo `kubectl get pods --namespace=jenkins`
while [ "${PODS_STATUS}" != "No resources found." ]
do
   sleep 30
   PODS_STATUS=echo `kubectl get pods --namespace=jenkins`
done