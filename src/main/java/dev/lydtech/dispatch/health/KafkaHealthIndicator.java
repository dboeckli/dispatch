package dev.lydtech.dispatch.health;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
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
    private final String kafkaBootstrapServers;

    private final AdminClient adminClient;

    private boolean wasDownLastCheck = true;

    public KafkaHealthIndicator(KafkaTemplate<String, Object> kafkaTemplate,
                                KafkaAdmin kafkaAdmin,
                                @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @PreDestroy
    void shutdown() {
        try {
            adminClient.close();
        } catch (Exception e) {
            log.error("Failed to close Kafka AdminClient cleanly", e);
        }
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
            Collection<GroupListing> groups;
            DescribeClusterResult describeCluster;
            Collection<TopicListing> topics;

            ListGroupsOptions listGroupsOptions = new ListGroupsOptions().timeoutMs(1000);
            ListGroupsResult listGroupsResult = adminClient.listGroups(listGroupsOptions);
            groups = listGroupsResult.all().get(5, TimeUnit.SECONDS);

            describeCluster = adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(1000));
            ListTopicsResult topicsResult = adminClient.listTopics();
            topics = topicsResult.listings().get(5, TimeUnit.SECONDS);


            if (wasDownLastCheck) {
                log.info("### Kafka Server connection successfully established.\n BootstrapServers: {}\n Response: {}\n Consumer groups: {}\n clusterId: {}\n topics: {}\n nodes: {}",
                    kafkaBootstrapServers,
                    responseInfo,
                    groups.stream().map(GroupListing::groupId).toList(),
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
                .withDetail("consumerGroups", groups.stream().map(GroupListing::groupId).toList())
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
