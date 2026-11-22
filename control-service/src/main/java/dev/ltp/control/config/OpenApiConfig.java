package dev.ltp.control.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 document (US-17). Every public endpoint must carry {@code @Operation} and an
 * {@code @ExampleObject}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ltpOpenApi(@Value("${ltp.version:dev}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("LTP Control Service API")
                        .version(version)
                        .description("Distributed load testing orchestration platform: scenarios, runs, "
                                + "live metrics, regression verdicts and the CI quality gate."));
    }
}
