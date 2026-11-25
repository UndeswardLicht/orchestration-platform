package dev.ltp.control;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * The whole Control Service against a real PostgreSQL (Flyway runs on start). All integration tests use
 * this one annotation, so Spring reuses a single context and a single database container.
 *
 * <p>Docker is required: without it the tests fail instead of being skipped (ADR-0004).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {"ltp.admin.login=" + IntegrationTest.ADMIN_LOGIN,
    "ltp.admin.password=" + IntegrationTest.ADMIN_PASSWORD})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

    String ADMIN_LOGIN = "admin";
    String ADMIN_PASSWORD = "integration-test-password";
}
