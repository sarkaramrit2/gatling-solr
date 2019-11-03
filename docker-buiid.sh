docker rm -vf $(docker ps -a -q)
docker rmi -f $(docker images -a -q)
docker build -t sarkaramrit2/gatling-solr:latest .
docker push sarkaramrit2/gatling-solr:latest
