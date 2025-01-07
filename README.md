# Introduction to Kafka with Spring Boot

This repository contains the code to support the [Introduction to Kafka with Spring Boot](https://www.udemy.com/course/introduction-to-kafka-with-spring-boot/?referralCode=15118530CA63AD1AF16D) online course.

The application code is for a message driven service which utilises Kafka and Spring Boot 3.

This application can be tested in two way:
1. Setting up local Kafka in Wsl (See Kafka.md file) and use the IntelliJ runner
2. Use IntelliJ runner with docker profile which will start a docker Kafka instance via docker compose

Send Message:
For that you need a kafka cli environment which will be available when you have done the kafka wsl setup
```
bin/kafka-console-producer.sh --bootstrap-server [::1]:9092 --topic order.created
>{"orderId":"8ed0dc67-41a4-4468-81e1-960340d30c92","item":"first-item"} 
```
