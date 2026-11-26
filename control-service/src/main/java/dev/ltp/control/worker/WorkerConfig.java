package dev.ltp.control.worker;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Worker settings, the clock for heartbeat times, and the schedule of the {@link HeartbeatMonitor}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(WorkerProperties.class)
class WorkerConfig {

    /** Heartbeat times come from the Control Service's clock, never from the worker's. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
