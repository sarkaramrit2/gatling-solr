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

GATLING_NODES=$((NUM_GATLING_NODES + 0))

# 5 more nodes for solr cluster
ESTIMATED_NODES_1=$((GATLING_NODES + 5))
ESTIMATED_NODES_2=$((GATLING_NODES + 6))

CID=`docker container ls -aq -f "name=kubectl-support"`

# initialise the k8s cluster with zookeepers, solr clusters, gatling-solr image
sed -i "s/gatling-nodes-replicas/${GATLING_NODES}/" ./jenkins/k8s/cluster.yaml
docker cp ./jenkins/k8s/cluster.yaml ${CID}:/opt/cluster.yaml
# optional property files a user may have uploaded to jenkins
# Note: Jenkins uses the same string for the file name, and the ENV var,
# so we're requiring CLUSTER_YAML_FILE (instead of cluster.yaml) so bash can read the ENV var
if [ ! -z "${CLUSTER_YAML_FILE}" ]; then
  if  [ ! -f ./CLUSTER_YAML_FILE ]; then
    echo "Found ENV{CLUSTER_YAML_FILE}=${CLUSTER_YAML_FILE} -- but ./CLUSTER_YAML_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied index config to workspace/configs/index.config.properties"
  cp ./CLUSTER_YAML_FILE ./workspace/configs/${CLUSTER_YAML_FILE}

  # copy the configs from local to dockers
  docker cp ./workspace/configs/${CLUSTER_YAML_FILE} ${CID}:/opt/cluster.yaml
else
  rm -rf ./CLUSTER_YAML_FILE ./workspace/configs/${CLUSTER_YAML_FILE}
fi

docker exec kubectl-support kubectl create -f /opt/cluster.yaml
# wait until all pods comes up running
TOTAL_PODS=`docker exec kubectl-support kubectl get pods --field-selector=status.phase=Running --namespace=jenkins | wc -l`
# find better way to determine all pods running
while [ "${TOTAL_PODS}" != "${ESTIMATED_NODES_1}" -a "${TOTAL_PODS}" != "${ESTIMATED_NODES_2}" ]
do
   sleep 30
   TOTAL_PODS=`docker exec kubectl-support kubectl get pods --field-selector=status.phase=Running --namespace=jenkins | wc -l`
done

# (re)create collection 'wiki' with shards 2 replicas 2
docker cp ./jenkins/collection-config ${CID}:/opt/collection-config
docker exec kubectl-support kubectl cp /opt/collection-config jenkins/solr-dummy-cluster-0:/opt/solr/collection-config
docker exec kubectl-support kubectl exec -n jenkins solr-dummy-cluster-0 -- /opt/solr/bin/solr delete -c wiki || echo "create collection now"
docker exec kubectl-support kubectl exec -n jenkins solr-dummy-cluster-0 -- /opt/solr/bin/solr create -c wiki -s 2 -rf 2 -d /opt/solr/collection-config/

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
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
    docker exec kubectl-support kubectl cp /opt/index.config.properties jenkins/gatling-solr-${c}:/opt/gatling/user-files/configs/index.config.properties
  done
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
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
    docker exec kubectl-support kubectl cp /opt/query.config.properties jenkins/gatling-solr-${c}:/opt/gatling/user-files/configs/query.config.properties
  done
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
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
    docker exec kubectl-support kubectl cp /opt/${DATA_FILE} jenkins/gatling-solr-${c}:/opt/gatling/user-files/data/${DATA_FILE}
  done
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
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
    docker exec kubectl-support kubectl cp /opt/${SIMULATION_FILE} jenkins/gatling-solr-${c}:/opt/gatling/user-files/simulations/${SIMULATION_FILE}
  done
else
  rm -rf ./SIMULATION_FILE ./workspace/simulations/${SIMULATION_FILE}
fi

# execute the load test on docker
echo "JOB DESCRIPTION: running....."

# read each class and execute the tests
while read -r CLASS; do

    # create results directory on the docker
    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
      docker exec kubectl-support kubectl exec -n jenkins gatling-solr-${c} -- mkdir -p /tmp/gatling-perf-tests-${c}-${CLASS}/results
    done

    # run gatling test for a simulation and pass relevant params
    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
      docker exec -d kubectl-support kubectl exec -n jenkins gatling-solr-${c} -- gatling.sh -s ${CLASS} -rd "--simulation--" -rf /tmp/gatling-perf-tests-${c}-${CLASS}/results -nr || echo "Current Simulation Ended!!"
    done

    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
        IF_CMD_EXEC=`docker exec kubectl-support kubectl exec -n jenkins gatling-solr-${c} -- ps | grep "gatling" | wc -l`
        while [ "${IF_CMD_EXEC}" != "0" ]
        do
            sleep 20
            IF_CMD_EXEC=`docker exec kubectl-support kubectl exec -n jenkins gatling-solr-${c} -- ps | grep "gatling" | wc -l`
        done
    done

    # generate the reports
    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
        docker exec kubectl-support mkdir -p /opt/results/reports-${c}-${CLASS}
        docker exec kubectl-support kubectl cp jenkins/gatling-solr-${c}:/tmp/gatling-perf-tests-${c}-${CLASS}/results/ /opt/results/reports-${c}-${CLASS}/
    done

    docker exec kubectl-support gatling.sh -ro /opt/results/
    # copy the perf tests to the workspace
    mkdir -p workspace/reports-${BUILD_NUMBER}/${CLASS}
    docker cp ${CID}:/opt/results ./workspace/reports-${BUILD_NUMBER}/${CLASS}
    docker exec kubectl-support rm -rf /opt/results/

done <<< "${SIMULATION_CLASS}"