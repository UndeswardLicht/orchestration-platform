package dev.ltp.control.worker;

/**
 * Worker states (US-06, overview §5). ONLINE: free; BUSY: runs a segment; LOST: no heartbeat for
 * longer than the timeout (US-07); OFFLINE: deregistered or shut down cleanly.
 */
public enum WorkerStatus {
    ONLINE,
    BUSY,
    LOST,
    OFFLINE
}
