package ch.dboeckli.template;
// TODO: RENAME PACKAGE

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
// TODO: RENAME ME
public class Spring6TemplateApplication {

    public static void main(String[] args) {
        log.info("Starting Spring 6 Template Application...");
        SpringApplication.run(Spring6TemplateApplication.class, args);
    }

}
