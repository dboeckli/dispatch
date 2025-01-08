package dev.lydtech.dispatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("docker")
class DispatchApplicationIT {

    @Test
    void contextLoads() {
    }

}
