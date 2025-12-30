package dev.lydtech.dispatch.health;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("docker")
@Slf4j
public class ActuatorInfoIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Autowired
    BuildProperties buildProperties;
    @Autowired
    private MockMvc mockMvc;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void actuatorInfoTest() throws Exception {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isOk())
            .andDo(result -> log.info("Response (pretty):\n{}", pretty(result.getResponse().getContentAsString())))

            .andExpect(jsonPath("$.git.commit.id.abbrev").isString())

            .andExpect(jsonPath("$.build.artifact").value(buildProperties.getArtifact()))
            .andExpect(jsonPath("$.build.group").value(buildProperties.getGroup()))

            .andExpect(jsonPath("$.java.version").value(startsWith("21")));
    }

    @Test
    void actuatorHealthTest() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andDo(result -> log.info("Response (pretty):\n{}", pretty(result.getResponse().getContentAsString())))
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorPrometheusTest() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andDo(result -> log.info("Response:\n{}", result.getResponse().getContentAsString()));
    }

    @Test
    void kafkaHealthIndicatorTest() throws Exception {
        mockMvc.perform(get("/actuator/health/kafka"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.details.kafkaBootstrapServers").value(bootstrapServers))
            .andExpect(jsonPath("$.details.kafkaResponse").value("Topic: health-check, Partition: 0, Offset: 0"))
            .andExpect(jsonPath("$.details.clusterId").isNotEmpty())
            .andExpect(jsonPath("$.details.nodes").isArray())
            .andExpect(jsonPath("$.details.nodes[0]").value("localhost:9092"))
            .andExpect(jsonPath("$.details.consumerGroups").isArray())
            .andExpect(jsonPath("$.details.consumerGroups").isEmpty())
            .andExpect(jsonPath("$.details.topics").isArray())
            .andExpect(jsonPath("$.details.topics").value(containsInAnyOrder(
                "health-check",
                "drink.request.cool",
                "drink.prepared",
                "drink.request.cold",
                "drink.request.icecold",
                "order.placed"
            ))).andDo(result -> log.info("Response:\n{}", result.getResponse().getContentAsString()));
    }

    private String pretty(String body) {
        try {
            Object json = OBJECT_MAPPER.readValue(body, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            // Falls kein valides JSON: unverändert zurückgeben
            return body;
        }
    }
}
