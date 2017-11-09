# kafka-k8s
work through a kafka and zoo release using k8s and helm.

## prep
fire up minikube:
```bash
minikube start
minikube dashboard
```

helm is installed, clean up cluster:
```bash
helm ls
helm delete xxx
```

look for some good docker images:
run on docker:
```bash
docker run --name zoo --restart always -d zookeeper
docker ps
docker stop xxxx
docker rm xxxx
docker rmi -f $(docker images -q)
```

try on k8s:
```bash
kubectl run zookeeper --image=zookeeper:latest
kubectl logs pod_name
kubectl expose deployment zookeeper --port=2181 --target-port=2181
```

seems to be running created a pod, a deployment, and a replica set. image says it is exposing or needs ports 2888, 3888, 2181. test by bashing into a pod and trying a command?
```bash
kubectl exec -it <pod> -- /bin/bash
bin/zkCli.sh
```

pull some configs to start with:
```bash
kubectl get deployment/zookeeper -o yaml --export > deployment-zookeeper.yml
kubectl get svc/zookeeper -o yaml --export > svc-zookeeper.yml
```

edit the file, get rid of the status field, make it a load balancer type for now, then:
```bash
kubectl replace --save-config -f svc-zookeeper.yml
```

## kafka
repeat the above for kafka, see if we can get kafka talking to zoo.

trying this, since it is kafka-only: https://hub.docker.com/r/ches/kafka/
```bash
kubectl run kafka --image=ches/kafka:latest
kubectl logs <kafka-xxxx.
```

can't see zoo running on 2181, different pod! need to decide on how to spread these things out.
* kafka will scale differently than zoo
* zoo will be a stateful set.
* zoo starts first, in an orderly fashion

so, kafka won't be in same pod as kafka, needs to know where zoo is so it can talk to it.
I can stop kafka and edit it's config files and restart it.
or, can I just expose the zoo services via dns name so kafka can just find it in the cluster?
let's see...

if the deployment has a "ContainerPort" it will be seen by any node in the cluster, by IP address.
then, expose as a service (declare the port), and make sure to label the service, create a selector, and lable the pods that will want to access the service!

edit the zoo deployment to make it expose a clusterIP, apply changes.
zoo deployment has a label: run: zoo, and a containerPort 2181.
zoo services has a label: run: zoo, and a selector run: zoo, and ports declared, so that should be good to go.

do we have dns running?
```bash
kubectl get services kube-dns --namespace=kube-system
```

let's see the kafka deployment:
```bash
kubectl get deployment/kafka -o yaml --export > deployment-kafka.yml
```

try re-applying, to restart the pod, and sshing into it and:
```bash
nslookup zoo
```

but... the kafka image wants things to be called "zookeeper", not "zoo".
edit the zoo files so it calls itself "zookeeper", then:
```bash
kubectl apply -f .
```

running, but are they working? testing...:
need a small program to test it out. won't run inside of kafka pod since the jmx stuff is in conflict.
need to create a service for kafka to expose it's service port.
```bash
kubectl expose deployment kafka --port=9092 --target-port=9092
```
edit (made it a NodePort type)
```bash
kubectl get svc/kafka -o yaml --export > svc-kafka.yml
minikube service kafka --> http://192.168.99.100:32183
nc -vz 192.168.100.99 32183
```

try connecting a client to this?
see this project: https://github.com/cf-platform-eng/kafka-service-broker

can't connect, too confusing to know which port to expose etc through the layers. Time to step back.
let's get zoo running locally, used the Confluent definitive guide book, got zoo and kafka singe nodes working locally no problem.

next step, try to get zoo running in docker, connect kafka locally...
```bash
docker run -d --name zookeeper -p 2181:2181 confluent/zookeeper
docker run -d --name kafka -p 9092:9092 --link zookeeper:zookeeper confluent/kafka
```

restarted kafka tested with the same commands:
```bash
kafka/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test
kafka/bin/kafka-topics.sh --zookeeper localhost:2181 --describe --topic test
kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test
kafka/bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test --from-beginning
```

need to mess with the kafka image here: https://github.com/confluentinc/docker-images.git

running into connectivity issues with kafka, try editing server.properties to add a listeners line:
```
listeners=PLAINTEXT://:9092
```

then added this line to the Dockerfile
```bash
ADD server.properties /etc/kafka/
```

```bash
docker build -t jg/kafka .
docker run -d --name jg-kafka -p 9092:9092 --link zookeeper:zookeeper jg/kafka
```

ok, that worked (tested via the two spring programs)!

## make this stuff work on k8s
let's start with imperative

```bash
kubectl run zookeeper --image=zookeeper:latest
```

need to pull the customized kafka image from somewhere... push to dockerhub

```bash
docker login
docker images
docker tag 197fda0c7673 jaredgordon/kafka:latest
docker push jaredgordon/kafka
```

worked, now try on k8s...
push zookeeper to k8s and create a service (LoadBalancer for now, fix later) as above
push local modified docker image
```bash
kubectl run jg-kafka --image=jaredgordon/kafka:latest
kubectl expose deployment jg-kafka --port=9092 --target-port=9092 --type=LoadBalancer
kubectl get deploy/jg-kafka -o yaml --export > deployment-kafka.yml
kubectl get svc/jg-kafka -o yaml --export > svc-kafka.yml
```

edit the service, make it a LoadBalancer, expose via minikube

ok, now getting access to kafka and zoo, but running into wierd kafka issues.

## stepping back again
let's look at an existing helm chart and work backwards (if it works)...

first off, switch over to gcp, since minikube is causing my laptop to overheat.

### gcp
https://cloud.google.com/container-engine/docs/tutorials/hello-app

### the chart
kubectl proxy
localhost:8001/ui to see the dashboard

then, see this: https://github.com/kubernetes/charts/tree/master/incubator/kafka

helm init
helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
helm install --name my-kafka incubator/kafka
helm status my-kafka

verified with their test pod
kubectl -n default exec -ti testclient -- ./bin/kafka-topics.sh --zookeeper my-kafka-zookeeper:2181 --list

all of the kafka parts are "ClusterIP", accessible only inside the cluster. Which should be "LoadBalance"?

need to modify the chart, so clone, edit and re-deploy. Added type: LoadBalancer to service-brokers.yaml

helm dep list
helm repo add incubator http://storage.googleapis.com/kubernetes-charts-incubator
helm dep update
helm dep build
helm lint .
helm upgrade my-kafka .
helm history my-kafka

cool!

I can nc the public ip/port, and when I run my consumer it created the topic. Can see this happening in the kafka logs.

ran this
kubectl -n default exec -ti testclient -- ./bin/kafka-topics.sh --zookeeper my-kafka-zookeeper:2181 --list

and saw that zoo says the topic exists.

but... producer times out with metadata issues. maybe ports need to be opened?

