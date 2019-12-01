#!/usr/bin/env bash

# Fail if no simulation class if specified
if [ -z "${GCP_KEY_FILE}" ]; then echo "GCP_KEY_FILE must be non-blank" && exit -1; fi

set -e
set -x

if [ "$GCP" = true ] ; then
  docker run -it -d --rm --name kubectl-support sarkaramrit2/kubectl-support:latest
else
  export AWS_ACCESS_KEY_ID=`cut -d ',' -f -1 ./GCP_KEY_FILE`
  export AWS_SECRET_ACCESS_KEY=`cut -d ',' -f -2 ./GCP_KEY_FILE`
  docker run -it -d --rm -e AWS_ACCESS_KEY_ID='${AWS_ACCESS_KEY_ID}' -e AWS_SECRET_ACCESS_KEY='${AWS_SECRET_ACCESS_KEY}' --name kubectl-support sarkaramrit2/kubectl-support:latest
fi

# set container id in which the docker is running
CID=`docker container ls -aq -f "name=kubectl-support"`

if [ ! -z "${GCP_KEY_FILE}" ]; then
  if  [ ! -f ./GCP_KEY_FILE ]; then
    echo "Found ENV{GCP_KEY_FILE}=${GCP_KEY_FILE} -- but ./GCP_KEY_FILE not found, jenkins bug?" && exit -1;
  fi
  # copy the configs from local to docker
  docker cp ./GCP_KEY_FILE ${CID}:/opt/${GCP_KEY_FILE}
else
  rm -rf ./GCP_KEY_FILE
fi

# delete the GCP file
rm -rf ./GCP_KEY_FILE

if [ "$GCP" = true ] ; then
  docker exec kubectl-support gcloud auth activate-service-account --key-file /opt/${GCP_KEY_FILE}
  docker exec kubectl-support gcloud config get-value core/account
  docker exec kubectl-support gcloud config set project ${GCP_K8_PROJECT}
  docker exec kubectl-support gcloud config list
  if [ "$SET_ZONE_UNSET_REGION" = true ] ; then
      docker exec kubectl-support gcloud container clusters get-credentials ${GCP_K8_CLUSTER_NAME} --zone "$ZONE_REGION"
  else
    docker exec kubectl-support gcloud container clusters get-credentials ${GCP_K8_CLUSTER_NAME} --region "$ZONE_REGION"
  fi
else
 docker exec kubectl-support eksctl utils write-kubeconfig ${GCP_K8_CLUSTER_NAME}
fi