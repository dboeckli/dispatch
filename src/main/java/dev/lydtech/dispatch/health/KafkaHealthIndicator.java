package dev.lydtech.dispatch.health;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final String kafkaBootstrapServers;

    private boolean wasDownLastCheck = true;

    public KafkaHealthIndicator(KafkaTemplate<String, Object> kafkaTemplate,
                                KafkaAdmin kafkaAdmin,
                                @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    @Override
    public Health health() {
        try {
            // Check producer connection
            Future<SendResult<String, Object>> future = kafkaTemplate.send("health-check", "health-check-message");
            SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

            String responseInfo = String.format("Topic: %s, Partition: %d, Offset: %d",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

            // Check consumer groups
            Collection<ConsumerGroupListing> groups;
            DescribeClusterResult describeCluster;
            Collection<TopicListing> topics;
            try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
                ListConsumerGroupsResult consumerGroups = adminClient.listConsumerGroups();
                groups = consumerGroups.all().get(5, TimeUnit.SECONDS);
                describeCluster = adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(1000));
                ListTopicsResult topicsResult = adminClient.listTopics();
                topics = topicsResult.listings().get(5, TimeUnit.SECONDS);
            }

            if (wasDownLastCheck) {
                log.info("### Kafka Server connection successfully established.\n BootstrapServers: {}\n Response: {}\n Consumer groups: {}\n clusterId: {}\n topics: {}\n nodes: {}",
                    kafkaBootstrapServers,
                    responseInfo,
                    groups.stream().map(ConsumerGroupListing::groupId).toList(),
                    describeCluster.clusterId().get(),
                    topics.stream().map(TopicListing::name).toList(),
                    describeCluster.nodes().get().stream()
                        .map(node -> String.format("%s:%d", node.host(), node.port()))
                        .toList());
                wasDownLastCheck = false;
            }

            return Health.up()
                .withDetail("kafkaBootstrapServers", kafkaBootstrapServers)
                .withDetail("kafkaResponse", responseInfo)
                .withDetail("clusterId", describeCluster.clusterId().get())
                .withDetail("nodes", describeCluster.nodes().get().stream()
                    .map(node -> String.format("%s:%d", node.host(), node.port()))
                    .toList())
                .withDetail("consumerGroups", groups.stream().map(ConsumerGroupListing::groupId).toList())
                .withDetail("topics", topics.stream().map(TopicListing::name).toList())
                .build();
        } catch (Exception ex) {
            wasDownLastCheck = true;
            log.warn("### Kafka Server connection down to {}", kafkaBootstrapServers, ex);
            return Health.down(ex)
                .withDetail("kafkaBootstrapServers", kafkaBootstrapServers)
                .build();
        }
    }
}
