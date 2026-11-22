package dev.ltp.worker.tailer;

import java.time.Instant;

/** One k6 metric point that the platform uses, e.g. one {@code http_req_duration} value in ms. */
public record K6Sample(String metric, Instant time, double value) {

    public static final String HTTP_REQ_DURATION = "http_req_duration";
    public static final String HTTP_REQS = "http_reqs";
    public static final String HTTP_REQ_FAILED = "http_req_failed";
    public static final String VUS = "vus";
}
