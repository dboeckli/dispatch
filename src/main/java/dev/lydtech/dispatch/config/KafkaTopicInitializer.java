package dev.lydtech.dispatch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.awaitility.Awaitility.await;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaTopicInitializer implements ApplicationListener<ContextRefreshedEvent> {

    private final KafkaAdmin kafkaAdmin;
    private final List<NewTopic> topics;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Initializing Kafka topics...");
        createTopics();
    }

    private void createTopics() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            for (NewTopic topic : topics) {
                log.info("Creating Topic: {}", topic.name());
                try {
                    adminClient.createTopics(List.of(topic)).all().get(30, TimeUnit.SECONDS);
                    log.info("Topic created successfully: {}", topic.name());
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    if (e.getCause() instanceof TopicExistsException) {
                        log.warn("### Topic '{}'", e.getMessage());
                    } else {
                        log.error("Failed to create topics", e);
                    }
                }
            }
        }
        try {
            showTopicDetails();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error checking Kafka topics", e);
        }
    }
    
    private void showTopicDetails() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            List<String> topicNames = topics.stream()
                .map(NewTopic::name)
                .toList();
            
            log.info("### Waiting for topics to be created: {}", topicNames);
            await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        DescribeTopicsResult resultCheck = adminClient.describeTopics(topicNames);
                        Map<String, TopicDescription> descriptions = resultCheck.all().get(5, TimeUnit.SECONDS);
                        return descriptions.size() == topicNames.size();
                    } catch (Exception e) {
                        log.warn("Topics not yet available: {}", e.getMessage());
                        return false;
                    }
                });
            log.info("### All topics are now available. Describing topic details...");
            DescribeTopicsResult result = adminClient.describeTopics(topicNames);
            try {
                Map<String, TopicDescription> topicDescriptions = result.allTopicNames().get(10, TimeUnit.SECONDS);
                for (String topicName : topicNames) {
                    TopicDescription description = topicDescriptions.get(topicName);
                    Map<ConfigResource, Config> configs = adminClient.describeConfigs(
                        Collections.singleton(new ConfigResource(ConfigResource.Type.TOPIC, topicName))
                    ).all().get();

                    log.info("----Topic Details -------");
                    log.info("Topic: {}", topicName);
                    log.info("Description: {}", description.toString());

                    Config config = configs.values().iterator().next();
                    config.entries().forEach(entry -> {
                        if (entry.source() != ConfigEntry.ConfigSource.DEFAULT_CONFIG) {
                            log.info("Config: {} = {}", entry.name(), entry.value());
                        }
                    });
                    log.info("-------------------------");
                }
            } catch (TimeoutException e) {
                log.error("Failed to describe topics {}", topicNames, e);
            }
        }
    }
}
