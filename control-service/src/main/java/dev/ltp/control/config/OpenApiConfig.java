package dev.ltp.control.config;

import dev.ltp.common.protocol.WorkerProtocol;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 document (US-17). Every public endpoint must carry {@code @Operation} and an
 * {@code @ExampleObject}. Endpoints need the session cookie unless they declare otherwise.
 */
@Configuration
public class OpenApiConfig {

    static final String SESSION_SCHEME = "session";

    /** Worker protocol (US-06): the shared token, sent only to register. */
    public static final String REGISTRATION_TOKEN_SCHEME = "registrationToken";

    /** Worker protocol (US-06): the per-worker secret from registration. */
    public static final String WORKER_SECRET_SCHEME = "workerSecret";

    @Bean
    OpenAPI ltpOpenApi(@Value("${ltp.version:dev}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("LTP Control Service API")
                        .version(version)
                        .description("Distributed load testing orchestration platform: scenarios, runs, "
                                + "live metrics, regression verdicts and the CI quality gate.\n\n"
                                + "Sign in with `POST /api/v1/auth/login`. Changes (POST, PATCH, PUT, DELETE) also "
                                + "need the value of the `" + SecurityConfig.CSRF_COOKIE + "` cookie in the `"
                                + SecurityConfig.CSRF_HEADER + "` header; Swagger UI sends it automatically."))
                .components(new Components()
                        .addSecuritySchemes(
                                SESSION_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name(SecurityConfig.SESSION_COOKIE)
                                        .description("HTTP-only session cookie set by POST /api/v1/auth/login"))
                        .addSecuritySchemes(
                                REGISTRATION_TOKEN_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name(WorkerProtocol.REGISTRATION_TOKEN_HEADER)
                                        .description("Shared worker registration token (LTP_WORKER_REGISTRATION_TOKEN)"))
                        .addSecuritySchemes(
                                WORKER_SECRET_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .description("Per-worker secret returned by POST /internal/v1/workers/register")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }
}
