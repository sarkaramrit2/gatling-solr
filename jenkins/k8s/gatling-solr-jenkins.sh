#!/bin/bash

# Fail if no simulation class if specified
if [ -z "${SIMULATION_CLASS}" ]; then echo "SIMULATION_CLASS must be non-blank" && exit -1; fi

set -e
set -x

JOB_DESCRIPTION="${SIMULATION_CLASS}"

# Create appropriate directories under workspace
mkdir -p workspace/configs
mkdir -p workspace/data
mkdir -p workspace/simulations

CID=`docker container ls -aq -f "name=kubectl_support"`

# initialise the k8s cluster with zookeepers, solr clusters, gatling-solr image
docker exec kubectl_support cd /opt/ && wget https://raw.githubusercontent.com/sarkaramrit2/gatling-solr/master/jenkins/k8s/cluster.yaml
docker exec kubectl_support kubectl create -f /opt/cluster.yaml
# wait until all pods comes up running
TOTAL_PODS=`docker exec kubectl_support kubectl get pods --field-selector=status.phase=Running --namespace=jenkins | wc -l`
while [ "${TOTAL_PODS}" != "6" ]
do
   sleep 30
   TOTAL_PODS=`docker exec kubectl_support kubectl get pods --field-selector=status.phase=Running --namespace=jenkins | wc -l`
done

# (re)create collection 'wiki' with shards 2 replicas 2
docker cp ./jenkins/collection-config ${CID}:/opt/collection-config
docker exec kubectl_support kubectl cp /opt/collection-config jenkins/solr-dummy-cluster-0:/opt/solr/collection-config
docker exec kubectl_support kubectl exec -n jenkins solr-dummy-cluster-0 -- /opt/solr/bin/solr delete -c wiki || echo "create collection now"
docker exec kubectl_support kubectl exec -n jenkins solr-dummy-cluster-0 -- /opt/solr/bin/solr create -c wiki -s 2 -rf 2 -d /opt/solr/collection-config/

# optional property files a user may have uploaded to jenkins
# Note: Jenkins uses the same string for the file name, and the ENV var,
# so we're requiring INDEX_PROP_FILE (instead of index.config.properties) so bash can read the ENV var
if [ ! -z "${INDEX_PROP_FILE}" ]; then
  if  [ ! -f ./INDEX_PROP_FILE ]; then
    echo "Found ENV{INDEX_PROP_FILE}=${INDEX_PROP_FILE} -- but ./INDEX_PROP_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied index config to workspace/configs/index.config.properties"
  cp ./INDEX_PROP_FILE ./workspace/configs/index.config.properties

  # copy the configs from local to dockers
  docker cp ./workspace/configs/index.config.properties ${CID}:/opt/index.config.properties
  docker exec kubectl_support kubectl cp /opt/index.config.properties jenkins/gatling-solr:/opt/gatling/user-files/configs/index.config.properties
else
  rm -rf ./INDEX_PROP_FILE ./workspace/configs/index.config.properties
fi

# we're requiring QUERY_PROP_FILE (instead of query.config.properties) so bash can read the ENV var
if [ ! -z "${QUERY_PROP_FILE}" ]; then
  if  [ ! -f ./QUERY_PROP_FILE ]; then
    echo "Found ENV{QUERY_PROP_FILE}=${QUERY_PROP_FILE} -- but ./QUERY_PROP_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied query config to workspace/configs/query.config.properties"
  cp ./QUERY_PROP_FILE ./workspace/configs/query.config.properties

  # copy the configs from local to dockers
  docker cp ./workspace/configs/query.config.properties ${CID}:/opt/query.config.properties
  docker exec kubectl_support kubectl cp /opt/query.config.properties jenkins/gatling-solr:/opt/gatling/user-files/configs/query.config.properties
else
  rm -rf ./QUERY_PROP_FILE ./workspace/configs/query.config.properties
fi

if [ -z "${INDEX_PROP_FILE}" -a  -z "${QUERY_PROP_FILE}" ]; then
    rm -rf ./workspace/configs
fi

# we're requiring DATA_FILE (instead of actual file name) so bash can read the ENV var
if [ ! -z "${DATA_FILE}" ]; then
  if  [ ! -f ./DATA_FILE ]; then
    echo "Found ENV{DATA_FILE}=${DATA_FILE} -- but ./DATA_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied patch to workspace/data/${DATA_FILE}"
  cp ./DATA_FILE ./workspace/data/${DATA_FILE}

  # copy the data from local to dockers
  docker cp ./workspace/configs/${DATA_FILE} ${CID}:/opt/${DATA_FILE}
  docker exec kubectl_support kubectl cp /opt/${DATA_FILE} jenkins/gatling-solr:/opt/gatling/user-files/data/${DATA_FILE}
else
  rm -rf ./DATA_FILE ./workspace/data/${DATA_FILE}
fi

# we're requiring SIMULATION_FILE so bash can read the ENV var
if [ ! -z "${SIMULATION_FILE}" ]; then
  if  [ ! -f ./SIMULATION_FILE ]; then
    echo "Found ENV{SIMULATION_FILE}=${SIMULATION_FILE} -- but ./SIMULATION_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied patch to workspace/data/${SIMULATION_FILE}"
  cp ./SIMULATION_FILE ./workspace/simulations/${SIMULATION_FILE}

  # copy the simulation file from local to dockers
  docker cp ./workspace/simulations/${SIMULATION_FILE} ${CID}:/opt/${SIMULATION_FILE}
  docker exec kubectl_support kubectl cp /opt/${SIMULATION_FILE} jenkins/gatling-solr:/opt/gatling/user-files/simulations/${SIMULATION_FILE}
else
  rm -rf ./SIMULATION_FILE ./workspace/simulations/${SIMULATION_FILE}
fi

# execute the load test on docker
echo "JOB DESCRIPTION: running....."

# read each class and execute the tests
while read -r CLASS; do

    # create results directory on the docker
    docker exec kubectl_support kubectl exec -n jenkins gatling-solr -- mkdir -p /tmp/gatling-perf-tests-${CLASS}/results

    # run gatling test for a simulation and pass relevant params
    docker exec -d kubectl_support kubectl exec -n jenkins gatling-solr -- gatling.sh -s ${CLASS} -rd "--simulation--" -rf /tmp/gatling-perf-tests-${CLASS}/results -nr || echo "Current Simulation Ended!!"

    IF_CMD_EXEC=`docker exec kubectl_support kubectl exec -n jenkins gatling-solr -- ps | grep "gatling" | wc -l`
    while [ "${IF_CMD_EXEC}" != "0" ]
    do
        sleep 20
        IF_CMD_EXEC=`docker exec kubectl_support kubectl exec -n jenkins gatling-solr -- ps | grep "gatling" | wc -l`
    done

    # generate the reports
    docker exec kubectl_support kubectl exec -n jenkins gatling-solr -- gatling.sh -ro /tmp/gatling-perf-tests-${CLASS}/
    # copy the perf tests to the workspace
    mkdir -p workspace/reports-${CLASS}-${BUILD_NUMBER}
    docker exec kubectl_support mkdir -p /opt/reports-${CLASS}
    docker exec kubectl_support kubectl cp jenkins/gatling-solr:/tmp/gatling-perf-tests-${CLASS} /opt/reports-${CLASS}/
    docker cp ${CID}:/opt/reports-${CLASS} ./workspace/reports-${CLASS}-${BUILD_NUMBER}

done <<< "${SIMULATION_CLASS}"