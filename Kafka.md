# Kafka installation

We install Kafka in WSL. 

See: https://github.com/lydtechconsulting/introduction-to-kafka-with-spring-boot/wiki/Installing-and-Running-Kafka-on-Windows
After successfull kafka installation you can export/import the kafka image.
```
wsl --export Ubuntu-Initial C:\development\data\wsl-distros\ubuntu-kafka.tar.gz
wsl --import Ubuntu-Kafka C:\development\data\wsl-distros\Ubuntu-Kafka C:\development\data\wsl-distros\ubuntu-kafka.tar.gz
```

## Kafka Installtion on WSL
- Upgrade
```
sudo apt update
sudo apt upgrade
sudp apt install openjdk-21-jdk
```

- copy kafka to /home/dboeckli/tools
```
mkdir kafka
cd kafka
tar -xvf ../kafka_2.13-3.9.0.tgz
```

- Setup Kafka
  - set cluster id
    ```
    cd ~/tools/kafka/kafka_2.13-3.9.0
    KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
    echo $KAFKA_CLUSTER_ID
    ```

  - set storage
    ```
    bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/kraft/server.properties
    ```
    logs are stored into a temp directory. this can be changed in the server.properties file if required

  - start kafka
    ```
    bin/kafka-server-start.sh config/kraft/server.properties
    ```
    See https://docs.conduktor.io/desktop/kafka-cluster-connection/setting-up-a-connection-to-kafka/connecting-to-kafka-running-on-windows-wsl-2/#two-ways-to-fix-this
    and https://stackoverflow.com/questions/46158296/kafka-broker-not-available-at-starting for connections issues
    I changed the config/kraft/server.properties file
    ```
    #listeners=PLAINTEXT://:9092,CONTROLLER://:9093
    listeners=PLAINTEXT://[::1]:9092,CONTROLLER://:9093
    
    # Name of listener used for communication between brokers.
    inter.broker.listener.name=PLAINTEXT
    
    # Listener name, hostname and port the broker or the controller will advertise to clients.
    # If not set, it uses the value for "listeners".
    #advertised.listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
    advertised.listeners=PLAINTEXT://[::1]:9092
    ```
    Therefore, you need to change the bootstrap-server parameter in the commands below to ```--bootstrap-server [::1]:9092``` as well
  - stop kafka
    ```
    bin/kafka-server-stop.sh
    ```

- Kafka Commands
  - create consumer and topic
    
    open new console and navigate to kafka directory
    ``` 
    bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic my.first.topic
    ```
  - create producer and topic

    open new console and navigate to kafka directory
    ``` 
    bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic my.first.topic
    ``` 
  - list topics
    ``` 
    bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
    ``` 
  - create topic
    ```
    bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic my.new.topic
    ```
  - show details of topic
    ```
    bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic my.new.topic
    ```
  - change topic. set number of partitions
    ```
    bin/kafka-topics.sh --bootstrap-server localhost:9092 --alter --topic my.new.topic  --partitions 3
    ```
  - delete topic
    ```
    bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic my.new.topic
    ```

  - consumer groups

    - first we create a new topic:
    ``` 
    bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic cg.demo.topic --partitions 5
    ```
    - list consumer groups
    ``` 
    bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
    ``` 
    should be emtpy

    - create new consumer on that topic and create new group (use new terminal)
    ```
    bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic cg.demo.topic --group my.new.group
    ```
    
    - now when listing consumer group you get one
    ``` 
    bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
    ```

    - get details of a consumer group
    ```
    bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group my.new.group
    ```

    - get state of a consumer group
    ```
    bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group my.new.group --state
    ```

    - show member of a consumer group
    ```
    bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group my.new.group --members
    ```
