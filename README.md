# Introduction to Kafka with Spring Boot

This repository contains the code to support the [Introduction to Kafka with Spring Boot](https://www.udemy.com/course/introduction-to-kafka-with-spring-boot/?referralCode=15118530CA63AD1AF16D) online course.

The application code is for a message driven service which utilises Kafka and Spring Boot 3.

This application can be tested in two way:
1. Setting up local Kafka in Wsl (See Kafka.md file) and use the IntelliJ runner
2. Use IntelliJ runner with docker profile which will start a docker Kafka instance via docker compose

Send Message:
For that you need a kafka cli environment which will be available when you have done the kafka wsl setup

use at home:
```
cd ~/tools/kafka/kafka_2.13-3.9.0
```
use at work:
```
cd /opt/development/tools/kafka/kafka_2.13-3.9.0
```

When started with docker profile use:
```
bin/kafka-topics.sh --bootstrap-server localhost:29092 --list
bin/kafka-topics.sh --bootstrap-server 127.0.0.1:29092 --list
bin/kafka-topics.sh --bootstrap-server [::1]:29092 --list
```

Send a message to the order.created topic
```
bin/kafka-console-producer.sh --bootstrap-server [::1]:9092 --topic order.created
>{"orderId":"8ed0dc67-41a4-4468-81e1-960340d30c92","item":"first-item"} 
```
When started with docker
```
bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order.created
>{"orderId":"8ed0dc67-41a4-4468-81e1-960340d30c92","item":"first-item"} 
```

Send message with key
```
bin/kafka-console-producer.sh --bootstrap-server localhost:29092 --topic order.created --property parse.key=true --property key.separator=:
>"123":{"orderId":"8ed0dc67-41a4-4468-81e1-960340d30c92","item":"first-item"}
```

When started with docker compose and kafka is up and running

open shell of kafka container
```
docker exec -it kafka /bin/bash
```

Terminal 1: start a consumer
```
./kafka-console-consumer --bootstrap-server localhost:9092 --topic dispatch.tracking --from-beginning --property print.headers=true
```

Terminal 2: send a DispatchPreparing-Message to topic dispatch.tracking
```
echo '__TypeId__:dev.lydtech.message.DispatchPreparing|{"orderId":"8ed0dc67-41a4-4468-81e1-960340d30c92"}' | /usr/bin/kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic dispatch.tracking \
  --property parse.headers=true \
  --property "headers.delimiter=|" \
  --property "headers.key.separator=:"
```

Terminal 2: send a DispatchCompleted-Message to topic dispatch.tracking
```
echo '__TypeId__:dev.lydtech.message.DispatchCompleted|{"orderId":"8ed0dc67-41a4-4468-81e1-960340d30c92"}' | /usr/bin/kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic dispatch.tracking \
  --property parse.headers=true \
  --property "headers.delimiter=|" \
  --property "headers.key.separator=:"
```
### Wiremock

WIREMOCK UI: http://localhost:30088/__admin/ or http://localhost:8888/__admin/
See: https://wiremock.org/docs/standalone/admin-api-reference/#tag/Stub-Mappings

### Deployment with Helm

Be aware that we are using a different namespace here (not default).

To run maven filtering for destination target/helm
```bash
mvn clean install -DskipTests 
```

Go to the directory where the tgz file has been created after 'mvn install'
```powershell
cd target/helm/repo
```

unpack
```powershell
$file = Get-ChildItem -Filter dispatch-v*.tgz | Select-Object -First 1
tar -xvf $file.Name
```

install
```powershell
$APPLICATION_NAME = Get-ChildItem -Directory | Where-Object { $_.LastWriteTime -ge $file.LastWriteTime } | Select-Object -ExpandProperty Name
helm upgrade --install $APPLICATION_NAME ./$APPLICATION_NAME --namespace dispatch --create-namespace --wait --timeout 8m --debug --render-subchart-notes
```

show logs
```powershell
kubectl get pods -l app.kubernetes.io/name=$APPLICATION_NAME -n dispatch
```
replace $POD with pods from the command above
```powershell
kubectl logs $POD -n dispatch --all-containers
```

test
```powershell
helm test $APPLICATION_NAME --namespace dispatch --logs
```

uninstall
```powershell
helm uninstall $APPLICATION_NAME --namespace dispatch
```

delete all
```powershell
kubectl delete all --all -n dispatch
```

create busybox sidecar
```powershell
kubectl run busybox-test --rm -it --image=busybox:1.36 --namespace=dispatch --command -- sh
```

and analyze kafka connections
```powershell
nslookup dispatch-kafka.dispatch.svc.cluster.local

nc -zv dispatch-kafka.dispatch.svc.cluster.local 29092
echo "Exit code for port 29092: $?"
```

create kafka sidecar
```powershell
kubectl run kafka-test --rm -it --image=bitnami/kafka:3.7.1 --namespace=dispatch --command -- sh
```

run kafka commands
```powershell
cd /opt/bitnami/kafka/bin
./kafka-topics.sh --bootstrap-server dispatch-kafka.dispatch.svc.cluster.local:29092 --list
```

You can use the actuator rest call to verify via port 30081

