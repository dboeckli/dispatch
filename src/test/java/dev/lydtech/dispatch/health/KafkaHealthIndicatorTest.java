package dev.lydtech.dispatch.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListGroupsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaHealthIndicatorTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private MockedStatic<AdminClient> adminClientStatic;

    private AdminClient adminClient;

    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        adminClient = mock(AdminClient.class);
        adminClientStatic = mockStatic(AdminClient.class);
        adminClientStatic.when(() -> AdminClient.create(anyMap())).thenReturn(adminClient);

        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());

        kafkaTemplate = mock(KafkaTemplate.class);
        healthIndicator = new KafkaHealthIndicator(kafkaTemplate, kafkaAdmin, BOOTSTRAP_SERVERS);
    }

    @AfterEach
    void tearDown() {
        adminClientStatic.close();
    }

    @Test
    void reportsUpWhenKafkaIsReachable() {
        stubKafkaUp();

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("kafkaBootstrapServers", BOOTSTRAP_SERVERS)
            .containsEntry("clusterId", "cluster-id")
            .containsEntry("kafkaResponse", "Topic: health-check, Partition: 0, Offset: 42");
        assertThat(health.getDetails().get("topics")).isEqualTo(List.of("health-check"));
        assertThat(health.getDetails().get("consumerGroups")).isEqualTo(List.of("dispatch-group"));
        assertThat(health.getDetails().get("nodes")).isEqualTo(List.of("localhost:9092"));
    }

    @Test
    void reportsUpOnRepeatedChecks() {
        stubKafkaUp();

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenKafkaIsUnreachable() {
        when(kafkaTemplate.send("health-check", "health-check-message"))
            .thenReturn(CompletableFuture.<SendResult<String, Object>>failedFuture(new RuntimeException("down")));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("kafkaBootstrapServers", BOOTSTRAP_SERVERS);
    }

    @Test
    void reportsDownAndReinterruptsWhenInterrupted() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(future.get(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));
        when(kafkaTemplate.send("health-check", "health-check-message")).thenReturn(future);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void shutdownSwallowsCloseFailures() {
        doThrow(new RuntimeException("close failed")).when(adminClient).close();

        assertThatCode(() -> healthIndicator.shutdown()).doesNotThrowAnyException();

        verify(adminClient).close();
    }

    private void stubKafkaUp() {
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.topic()).thenReturn("health-check");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(42L);

        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send("health-check", "health-check-message"))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        GroupListing groupListing = mock(GroupListing.class);
        when(groupListing.groupId()).thenReturn("dispatch-group");
        ListGroupsResult listGroupsResult = mock(ListGroupsResult.class);
        when(listGroupsResult.all())
            .thenReturn(KafkaFuture.<Collection<GroupListing>>completedFuture(List.of(groupListing)));
        when(adminClient.listGroups(any())).thenReturn(listGroupsResult);

        DescribeClusterResult describeClusterResult = mock(DescribeClusterResult.class);
        when(describeClusterResult.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster-id"));
        when(describeClusterResult.nodes())
            .thenReturn(KafkaFuture.<Collection<Node>>completedFuture(List.of(new Node(0, "localhost", 9092))));
        when(adminClient.describeCluster(any())).thenReturn(describeClusterResult);

        TopicListing topicListing = mock(TopicListing.class);
        when(topicListing.name()).thenReturn("health-check");
        ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
        when(listTopicsResult.listings())
            .thenReturn(KafkaFuture.<Collection<TopicListing>>completedFuture(List.of(topicListing)));
        when(adminClient.listTopics()).thenReturn(listTopicsResult);
    }

}
