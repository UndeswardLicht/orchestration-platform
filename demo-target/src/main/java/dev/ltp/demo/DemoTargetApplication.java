package dev.ltp.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo Target — the only host experiments are run against.
 *
 * <p>T-04 adds endpoints with a configurable latency distribution (log-normal + slow tail), error
 * rate, an admin endpoint to inject +X % latency (KPI-1), and the ownership file token (US-21).
 */
@SpringBootApplication
public class DemoTargetApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoTargetApplication.class, args);
    }
}
