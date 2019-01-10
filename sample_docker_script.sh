#!/usr/bin/env bash

# set env properties for the perf test
PATH_TO_REPORT=/Users/apple/Work/gatling-solr/results
DATE=`date '+%Y-%m-%d-%H-%M-%S'`
GATLING_HOME=/Users/apple/Work/GATLING

# run the gatling solr docker image
docker run -d --name gatling-solr sarkaramrit2/gatling-solr
#optional docker run command with copying local files and simulation files to docker
#docker run -d -v configs:/opt/gatling/user-files/configs/ \ --name gatling-solr sarkaramrit2/gatling-solr

# create report directory
mkdir -p ${PATH_TO_REPORT}/report-${DATE}/results
# create results directory on the docker
docker exec gatling-solr mkdir -p /tmp/results
# run gatling test for a simulation and pass relevant params
docker exec gatling-solr gatling.sh -s QuerySimulation -rd "--query-simulation--" -rf /tmp/results -nr
#docker exec gatling-solr gatling.sh -s IndexV2Simulation -rd "--index-simulation--" -rf /tmp/results -nr

# maybe we need to run this in background, but then there was no way to check
# whether the run has been completed, works on single node

# set container id in which the docker is running
CID=`docker container ls -aq -f "name=gatling-solr"`
# copy the results from docker locally
docker cp ${CID}:/tmp/results/* ${PATH_TO_REPORT}/report-${DATE}/results/
# run gatling command to generate report from downloaded results
${GATLING_HOME}/bin/gatling.sh -ro ${PATH_TO_REPORT}/report-${DATE}/

#remove temporary folders and kill the container
docker exec gatling-solr rm -rf /tmp/results
docker container stop $(docker container ls -aq -f "name=gatling-solr")
docker container rm $(docker container ls -aq -f "name=gatling-solr")