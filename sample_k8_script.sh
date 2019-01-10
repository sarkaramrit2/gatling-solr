#!/usr/bin/env bash

# set env properties for the perf test
PATH_TO_REPORT=/Users/apple/Work/gatling-solr/results
DATE=`date '+%Y-%m-%d-%H-%M-%S'`
GATLING_HOME=/Users/apple/Work/GATLING
PATH_TO_GATLING_SOLR_K8=/Users/apple/Work/gatling-solr/k8s

# constructing gatling-solrK8 pod
kubectl describe -f ${PATH_TO_GATLING_SOLR_K8}/pod.yaml
#OPTIONAL - kubectl command with copying local files and simulation files to docker
#kubectl cp configs default/gatling-solr:/opt/gatling/user-files/configs
#kubectl cp simulations default/gatling-solr:/opt/gatling/user-files/simulations
#kubectl cp data default/gatling-solr:/opt/gatling/user-files/data

# create report directory
mkdir -p ${PATH_TO_REPORT}/report-${DATE}/results
# create results directory on the k8 container
kubectl exec gatling-solr -- mkdir -p /tmp/results
# run gatling test for a simulation and pass relevant params
kubectl exec gatling-solr -- gatling.sh -s QuerySimulation -rd "--query-simulation--" -rf /tmp/results -nr
#kubectl exec gatling-solr -- gatling.sh -s IndexV2Simulation -rd "--index-simulation--" -rf /tmp/results -nr

# maybe we need to run this in background, but then there was no way to check
# whether the run has been completed, works on single node

# copy the results from k8 container locally
kubectl exec gatling-solr -- cp gatling-solr:/tmp/results/* ${PATH_TO_REPORT}/report-${DATE}/results/
# run gatling command to generate report from downloaded results
${GATLING_HOME}/bin/gatling.sh -ro ${PATH_TO_REPORT}/report-${DATE}/

#OPTIONAL - kill the container
kubectl delete pods gatling-solr