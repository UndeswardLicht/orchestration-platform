package dev.ltp.worker.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Docker client for starting k6 containers.
 *
 * <p>The host comes from {@code DOCKER_HOST}. In Compose it points at the socket proxy ({@code
 * tcp://socket-proxy:2375}), never at the raw socket (risk R5). Without it, docker-java uses the
 * platform default (Unix socket, or the Docker Desktop named pipe on Windows).
 */
@Configuration(proxyBeanMethods = false)
public class DockerConfig {

    @Bean(destroyMethod = "close")
    DockerClient dockerClient() {
        return createClient();
    }

    /** Also used by tests that talk to Docker directly. */
    public static DockerClient createClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient http =
                new ApacheDockerHttpClient.Builder()
                        .dockerHost(config.getDockerHost())
                        .sslConfig(config.getSSLConfig())
                        .connectionTimeout(Duration.ofSeconds(5))
                        // Calls are short: the runner polls container state instead of a long "wait"
                        .responseTimeout(Duration.ofMinutes(5))
                        .build();
        return DockerClientImpl.getInstance(config, http);
    }
}
