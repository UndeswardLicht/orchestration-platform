package dev.ltp.worker.registration;

/**
 * What the Control Service gave this worker on registration. The secret is kept in memory only; after a
 * restart the worker registers again and gets a new one.
 */
public record Registration(long workerId, String secret) {

    @Override
    public String toString() {
        return "Registration[workerId=" + workerId + ", secret=***]";
    }
}
