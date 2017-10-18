# kafka-k8s
work through a kafka and zoo release using k8s and helm.

fire up minikube:
minikube start
minikube dashboard

helm is installed, clean up cluster:
helm ls
helm delete xxx

look for some good docker images:
https://hub.docker.com/_/zookeeper/

run on docker
docker run --name zoo --restart always -d zookeeper
docker ps
docker stop xxxx
docker rm xxxx
docker rmi -f $(docker images -q)

try on k8s:
kubectl run zoo --image=hub.docker.com/_/zookeeper:latest
kubectl logs <pod name>

seems to be running created a pod, a deployment, and a replica set
image says it is exposing or needs ports 2888, 3888, 2181
test by bashing into a pod and trying a command?
kubectl exec -it <pod> -- /bin/bash
bin/zkCli.sh

pull some configs to start with
kubectl get deployment/zoo -o yaml --export > deployment-zoo.yml

edit and add port 2182 as the container port, update
kubectl replace --save-config -f deployment-zoo.yml

kubectl expose deployment zoo --port=2181 --target-port=2181
kubectl get svc/zoo -o yaml --export > svc-zoo.yml

edit the file, get rid of the status field, cluserIp value, then
kubectl replace --save-config -f svc-zoo.yml


create a service to expose zoo




