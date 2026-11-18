package dev.lydtech.dispatch.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaTopicInitializerTest {

    private static final String TOPIC_NAME = "test-topic";

    private MockedStatic<AdminClient> adminClientStatic;

    private AdminClient adminClient;

    private ContextRefreshedEvent event;

    private KafkaTopicInitializer topicInitializer;

    @BeforeEach
    void setUp() {
        adminClient = mock(AdminClient.class);
        adminClientStatic = mockStatic(AdminClient.class);
        adminClientStatic.when(() -> AdminClient.create(anyMap())).thenReturn(adminClient);

        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

        event = mock(ContextRefreshedEvent.class);
        NewTopic topic = new NewTopic(TOPIC_NAME, 1, (short) 1);
        topicInitializer = new KafkaTopicInitializer(kafkaAdmin, List.of(topic));
    }

    @AfterEach
    void tearDown() {
        adminClientStatic.close();
    }

    @Test
    void createsTopicsAndLogsTopicDetails() throws Exception {
        stubCreateTopics(KafkaFuture.completedFuture(null));
        stubTopicDetails();

        assertThatCode(() -> topicInitializer.onApplicationEvent(event)).doesNotThrowAnyException();

        verify(adminClient).createTopics(anyCollection());
        verify(adminClient, times(2)).close();
    }

    @Test
    void logsWarningWhenTopicAlreadyExists() throws Exception {
        stubCreateTopics(failedFuture(new ExecutionException(new TopicExistsException("topic exists"))));
        stubTopicDetails();

        assertThatCode(() -> topicInitializer.onApplicationEvent(event)).doesNotThrowAnyException();

        verify(adminClient).createTopics(anyCollection());
    }

    @Test
    void logsErrorWhenTopicCreationFails() throws Exception {
        stubCreateTopics(failedFuture(new ExecutionException(new RuntimeException("creation failed"))));
        stubTopicDetails();

        assertThatCode(() -> topicInitializer.onApplicationEvent(event)).doesNotThrowAnyException();

        verify(adminClient).createTopics(anyCollection());
    }

    @Test
    void waitsUntilTopicsAreAvailable() throws Exception {
        stubCreateTopics(KafkaFuture.completedFuture(null));

        DescribeTopicsResult incompleteResult = mock(DescribeTopicsResult.class);
        when(incompleteResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(Map.of()));
        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(descriptions()));
        when(adminClient.describeTopics(anyCollection())).thenThrow(new RuntimeException("not yet available"))
            .thenReturn(incompleteResult)
            .thenReturn(describeTopicsResult);
        stubTopicConfigs();

        assertThatCode(() -> topicInitializer.onApplicationEvent(event)).doesNotThrowAnyException();

        verify(adminClient, times(4)).describeTopics(anyCollection());
    }

    @Test
    void logsErrorWhenDescribingTopicsFails() throws Exception {
        stubCreateTopics(KafkaFuture.completedFuture(null));

        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(descriptions()));
        when(adminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult).thenAnswer(invocation -> {
            throw new ExecutionException("describe failed", new RuntimeException());
        });

        assertThatCode(() -> topicInitializer.onApplicationEvent(event)).doesNotThrowAnyException();

        verify(adminClient, times(2)).describeTopics(anyCollection());
    }

    @Test
    void logsErrorWhenDescribingTopicsTimesOut() throws Exception {
        stubCreateTopics(KafkaFuture.completedFuture(null));

        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(descriptions()));

        KafkaFuture<Map<String, TopicDescription>> timeoutFuture = mock(KafkaFuture.class);
        when(timeoutFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException("timeout"));
        DescribeTopicsResult timeoutDescribeTopicsResult = mock(DescribeTopicsResult.class);
        when(timeoutDescribeTopicsResult.allTopicNames()).thenReturn(timeoutFuture);

        when(adminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult)
            .thenReturn(timeoutDescribeTopicsResult);

        assertThatCode(() -> topicInitializer.onApplicationEvent(event)).doesNotThrowAnyException();

        verify(adminClient, times(2)).describeTopics(anyCollection());
    }

    private void stubCreateTopics(KafkaFuture<Void> future) {
        CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
        when(createTopicsResult.all()).thenReturn(future);
        when(adminClient.createTopics(anyCollection())).thenReturn(createTopicsResult);
    }

    private void stubTopicDetails() {
        DescribeTopicsResult describeTopicsResult = mock(DescribeTopicsResult.class);
        when(describeTopicsResult.allTopicNames()).thenReturn(KafkaFuture.completedFuture(descriptions()));
        when(adminClient.describeTopics(anyCollection())).thenReturn(describeTopicsResult);
        stubTopicConfigs();
    }

    private void stubTopicConfigs() {
        ConfigEntry appliedEntry = mock(ConfigEntry.class);
        when(appliedEntry.source()).thenReturn(ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG);
        when(appliedEntry.name()).thenReturn("retention.ms");
        when(appliedEntry.value()).thenReturn("3600000");

        ConfigEntry defaultEntry = mock(ConfigEntry.class);
        when(defaultEntry.source()).thenReturn(ConfigEntry.ConfigSource.DEFAULT_CONFIG);

        Config config = mock(Config.class);
        when(config.entries()).thenReturn(List.of(appliedEntry, defaultEntry));

        DescribeConfigsResult describeConfigsResult = mock(DescribeConfigsResult.class);
        ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC_NAME);
        when(describeConfigsResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of(configResource, config)));
        when(adminClient.describeConfigs(anyCollection())).thenReturn(describeConfigsResult);
    }

    private Map<String, TopicDescription> descriptions() {
        return Map.of(TOPIC_NAME, mock(TopicDescription.class));
    }

    @SuppressWarnings("unchecked")
    private KafkaFuture<Void> failedFuture(ExecutionException exception) throws Exception {
        KafkaFuture<Void> future = mock(KafkaFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(exception);
        return future;
    }

}
