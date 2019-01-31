#!/bin/bash

# Fail if no simulation class if specified
if [ -z "${SIMULATION_CLASS}" ]; then echo "SIMULATION_CLASS must be non-blank" && exit -1; fi

set -e
set -x

JOB_DESCRIPTION="${SIMULATION_CLASS}"

# Create appropriate directories under workspace

mkdir -p ./workspace/data
mkdir -p ./workspace/simulations
mkdir -p ./workspace/configs

GATLING_NODES=$((NUM_GATLING_NODES + 0))

# 5 more nodes for solr cluster
ESTIMATED_NODES_1=$((GATLING_NODES))
ESTIMATED_NODES_2=$((GATLING_NODES + 1))

if [ "$IMPLICIT_CLUSTER" = true ] ; then
   # TODO: hardcoded need to provide the check better, possible parameter passing
   ESTIMATED_NODES_1=$((ESTIMATED_NODES_1 + 4))
   ESTIMATED_NODES_2=$((ESTIMATED_NODES_2 + 4))
   cp ./jenkins/k8s/cluster-internal.yaml ./jenkins/k8s/cluster.yaml
else
   cp ./jenkins/k8s/cluster-external.yaml ./jenkins/k8s/cluster.yaml
fi

CID=`docker container ls -aq -f "name=kubectl-support"`

# initialise the k8s cluster with zookeepers, solr clusters, gatling-solr image
sed -i "s/namespace_filler/${GCP_K8_CLUSTER_NAMESPACE}/" ./jenkins/k8s/cluster.yaml
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
  rm -rf ./CLUSTER_YAML_FILE
fi

# delete gatling-solr service and statefulsets, redundant step
docker exec kubectl-support kubectl delete statefulsets gatling-solr --namespace=${GCP_K8_CLUSTER_NAMESPACE} || echo "gatling statefulsets not available!!"
docker exec kubectl-support kubectl delete service gatling-solr --namespace=${GCP_K8_CLUSTER_NAMESPACE} || echo "gatling service not available!!"
sleep 10

docker exec kubectl-support kubectl create -f /opt/cluster.yaml || echo "gatling service already created!!"
# buffer sleep for 2 mins to get the pods ready, and then check
sleep 120

if [ "$IMPLICIT_CLUSTER" = true ] ; then
    # wait until all pods comes up running
    TOTAL_PODS=`docker exec kubectl-support kubectl get pods --field-selector=status.phase=Running --namespace=${GCP_K8_CLUSTER_NAMESPACE} | wc -l`
    # find better way to determine all pods running
    while [ "${TOTAL_PODS}" != "${ESTIMATED_NODES_1}" -a "${TOTAL_PODS}" != "${ESTIMATED_NODES_2}" ]
    do
       sleep 30
       TOTAL_PODS=`docker exec kubectl-support kubectl get pods --field-selector=status.phase=Running --namespace=${GCP_K8_CLUSTER_NAMESPACE} | wc -l`
    done
else
    # wait until all pods comes up running
    TOTAL_PODS=`docker exec kubectl-support kubectl get pods --field-selector status.phase=Running --field-selector metadata.name=gatling-solr-0 --namespace=${GCP_K8_CLUSTER_NAMESPACE} | wc -l`
    # find better way to determine all pods running
    while [ "${TOTAL_PODS}" != "2" ]
    do
       sleep 30
       TOTAL_PODS=`docker exec kubectl-support kubectl get pods --field-selector status.phase=Running --field-selector metadata.name=gatling-solr-0 --namespace=${GCP_K8_CLUSTER_NAMESPACE} | wc -l`
    done
fi

# TODO: remove executing commands within the solr cluster and utilise Collection Admin API
if [ "$IMPLICIT_CLUSTER" = true ] ; then
    # (re)create collection 'wiki'
    docker cp ./jenkins/collection-config ${CID}:/opt/collection-config
    docker exec kubectl-support kubectl cp /opt/collection-config ${GCP_K8_CLUSTER_NAMESPACE}/solr-dummy-cluster-0:/opt/solr/collection-config
    docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} solr-dummy-cluster-0 -- /opt/solr/bin/solr delete -c wiki || echo "create collection now"
    docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} solr-dummy-cluster-0 -- /opt/solr/bin/solr create -c wiki -s $((NUM_SHARDS)) -rf $((NUM_REPLICAS)) -d /opt/solr/collection-config/ || echo "collection already created"
else
    # (re)create collection 'wiki'
    docker cp ./jenkins/collection-config ${CID}:/opt/collection-config
    docker exec kubectl-support kubectl cp /opt/collection-config ${GCP_K8_CLUSTER_NAMESPACE}/${EXT_SOLR_NODE_POD_NAME}:/opt/solr/collection-config
    docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} ${EXT_SOLR_NODE_POD_NAME} -- /opt/solr/bin/solr delete -c wiki || echo "create collection now"
    docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} ${EXT_SOLR_NODE_POD_NAME} -- /opt/solr/bin/solr create -c wiki -s $((NUM_SHARDS)) -rf $((NUM_REPLICAS)) -d /opt/solr/collection-config/ || echo "collection already created"
fi

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
    docker exec kubectl-support kubectl cp /opt/index.config.properties ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/opt/gatling/user-files/configs/index.config.properties
  done
else
  rm -rf ./INDEX_PROP_FILE
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
    docker exec kubectl-support kubectl cp /opt/query.config.properties ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/opt/gatling/user-files/configs/query.config.properties
  done
else
  rm -rf ./QUERY_PROP_FILE
fi

# we're requiring INDEX_FEEDER_FILE (instead of actual file name) so bash can read the ENV var
if [ ! -z "${INDEX_FEEDER_FILE}" ]; then
  if  [ ! -f ./INDEX_FEEDER_FILE ]; then
    echo "Found ENV{INDEX_FEEDER_FILE}=${INDEX_FEEDER_FILE} -- but ./INDEX_FEEDER_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied patch to workspace/data/${INDEX_FEEDER_FILE}"
  cp ./INDEX_FEEDER_FILE ./workspace/data/${INDEX_FEEDER_FILE}

  # copy the data from local to dockers
  docker cp ./workspace/configs/${INDEX_FEEDER_FILE} ${CID}:/opt/${INDEX_FEEDER_FILE}
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
    docker exec kubectl-support kubectl cp /opt/${INDEX_FEEDER_FILE} ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/opt/gatling/user-files/data/${INDEX_FEEDER_FILE}
  done
else
  rm -rf ./INDEX_FEEDER_FILE
fi

# we're requiring QUERY_FEEDER_FILE (instead of actual file name) so bash can read the ENV var
if [ ! -z "${QUERY_FEEDER_FILE}" ]; then
  if  [ ! -f ./QUERY_FEEDER_FILE ]; then
    echo "Found ENV{QUERY_FEEDER_FILE}=${QUERY_FEEDER_FILE} -- but ./QUERY_FEEDER_FILE not found, jenkins bug?" && exit -1;
  fi
  echo "Copying user supplied patch to workspace/data/${QUERY_FEEDER_FILE}"
  cp ./INDEX_FEEDER_FILE ./workspace/data/${QUERY_FEEDER_FILE}

  # copy the data from local to dockers
  docker cp ./workspace/configs/${QUERY_FEEDER_FILE} ${CID}:/opt/${QUERY_FEEDER_FILE}
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
    docker exec kubectl-support kubectl cp /opt/${QUERY_FEEDER_FILE} ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/opt/gatling/user-files/data/${QUERY_FEEDER_FILE}
  done
else
  rm -rf ./INDEX_FEEDER_FILE
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
    docker exec kubectl-support kubectl cp /opt/${SIMULATION_FILE} ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/opt/gatling/user-files/simulations/${SIMULATION_FILE}
  done
else
  rm -rf ./SIMULATION_FILE
fi

# so we're requiring REMOTE_INDEX_FILE_PATH so bash can read the ENV var
if [ ! -z "${REMOTE_INDEX_FILE_PATH}" ]; then
  # download the remote indexing file
  for (( c=0; c<${GATLING_NODES}; c++ ))
  do
     docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- mkdir -p /opt/gatling/user-files/data/
     docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- rm -rf /opt/gatling/user-files/data/external.data.txt
     docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- curl "${REMOTE_INDEX_FILE_PATH}" --output /opt/gatling/user-files/data/external.data.txt
     #docker exec -d kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- curl "${REMOTE_INDEX_FILE_PATH}" --output /opt/gatling/user-files/data/external.data.txt
  done

  # wait until index file copies to all gatling nodes
  for (( c=0; c<${GATLING_NODES}; c++ ))
    do
    IF_CMD_EXEC=`docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- ps | grep "curl" | wc -l`
    while [ "${IF_CMD_EXEC}" != "0" ]
    do
      sleep 10
      IF_CMD_EXEC=`docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- ps | grep "curl" | wc -l`
      done
  done
fi

# set gatling nodes heap settings
sed -i "s/replace-heap-settings/${GATLING_HEAP}/" ./jenkins/k8s/gatling.sh
docker cp ./jenkins/k8s/gatling.sh ${CID}:/opt/gatling.sh
# create results directory on the docker
for (( c=0; c<${GATLING_NODES}; c++ ))
do
    docker exec kubectl-support kubectl cp /opt/gatling.sh ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/opt/gatling/bin/gatling.sh
done


# execute the load test on docker
echo "JOB DESCRIPTION: running....."

# read each class and execute the tests
while read -r CLASS; do

    # create results directory on the docker
    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
      docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- mkdir -p /tmp/gatling-perf-tests-${c}-${CLASS}/results
    done

    # run gatling test for a simulation and pass relevant params
    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
      docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- gatling.sh -s ${CLASS} -rd "--simulation--" -rf /tmp/gatling-perf-tests-${c}-${CLASS}/results -nr || echo "Current Simulation Ended!!"
      # docker exec -d kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- gatling.sh -s ${CLASS} -rd "--simulation--" -rf /tmp/gatling-perf-tests-${c}-${CLASS}/results -nr || echo "Current Simulation Ended!!"
    done

    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
        IF_CMD_EXEC=`docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- ps | grep "gatling" | wc -l`
        while [ "${IF_CMD_EXEC}" != "0" ]
        do
            sleep 20
            IF_CMD_EXEC=`docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} gatling-solr-${c} -- ps | grep "gatling" | wc -l`
        done
    done

    # generate the reports
    for (( c=0; c<${GATLING_NODES}; c++ ))
    do
        docker exec kubectl-support mkdir -p /opt/results/reports-${c}-${CLASS}
        docker exec kubectl-support kubectl cp ${GCP_K8_CLUSTER_NAMESPACE}/gatling-solr-${c}:/tmp/gatling-perf-tests-${c}-${CLASS}/results/ /opt/results/reports-${c}-${CLASS}/
    done

    docker exec kubectl-support gatling.sh -ro /opt/results/
    # copy the perf tests to the workspace
    mkdir -p workspace/reports-${BUILD_NUMBER}/${CLASS}
    docker cp ${CID}:/opt/results ./workspace/reports-${BUILD_NUMBER}/${CLASS}
    docker exec kubectl-support rm -rf /opt/results/

done <<< "${SIMULATION_CLASS}"

if [ "$IMPLICIT_CLUSTER" = true ] ; then
    # copy the logs to the workspace
    docker exec kubectl-support kubectl cp ${GCP_K8_CLUSTER_NAMESPACE}/solr-dummy-cluster-0:/opt/solr/logs /opt/solr-logs
    docker cp ${CID}:/opt/solr-logs ./workspace/reports-${BUILD_NUMBER}/solr-logs
fi

# TODO: remove executing commands within the solr cluster and utilise Collection Admin API
#if [ "$IMPLICIT_CLUSTER" = true ] ; then
 #   docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} solr-dummy-cluster-0 -- /opt/solr/bin/solr delete -c wiki || echo "create collection now"
#else
 #   docker exec kubectl-support kubectl exec -n ${GCP_K8_CLUSTER_NAMESPACE} ${EXT_SOLR_NODE_POD_NAME} -- /opt/solr/bin/solr delete -c wiki || echo "create collection now"
#fi