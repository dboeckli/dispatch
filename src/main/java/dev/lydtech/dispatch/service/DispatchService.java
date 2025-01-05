package dev.lydtech.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DispatchService {

    public void process(String payload) {
        log.info("process payload {}", payload);
    }
    
}
