package dev.ltp.worker.registration;

import dev.ltp.common.protocol.WorkerProtocol;
import dev.ltp.common.protocol.WorkerProtocol.HeartbeatRequest;
import dev.ltp.common.protocol.WorkerProtocol.HeartbeatResponse;
import dev.ltp.common.protocol.WorkerProtocol.RegisterRequest;
import dev.ltp.common.protocol.WorkerProtocol.RegisterResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls of the worker protocol on the Control Service (US-06, 05-tech-stack.md §4). The worker always
 * opens the connection, so it needs no inbound port. Errors surface as {@link RestClientException}
 * ({@code HttpClientErrorException.Unauthorized} for a refused token or secret).
 */
public class ControlClient {

    private static final String WORKERS = WorkerProtocol.BASE_PATH + "/workers";

    private final RestClient http;

    public ControlClient(RestClient http) {
        this.http = http;
    }

    public RegisterResponse register(RegisterRequest request, String registrationToken) {
        return http.post()
                .uri(WORKERS + "/register")
                .header(WorkerProtocol.REGISTRATION_TOKEN_HEADER, registrationToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RegisterResponse.class);
    }

    public HeartbeatResponse heartbeat(Registration registration, HeartbeatRequest request) {
        HeartbeatResponse response = http.post()
                .uri(WORKERS + "/{id}/heartbeat", registration.workerId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + registration.secret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HeartbeatResponse.class);
        return response == null ? new HeartbeatResponse(null) : response;
    }

    public void deregister(Registration registration) {
        http.post()
                .uri(WORKERS + "/{id}/deregister", registration.workerId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + registration.secret())
                .retrieve()
                .toBodilessEntity();
    }
}
