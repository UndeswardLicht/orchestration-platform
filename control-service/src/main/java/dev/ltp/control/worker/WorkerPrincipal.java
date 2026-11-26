package dev.ltp.control.worker;

/** The worker a request on {@code /internal/**} was authenticated as, by its per-worker secret. */
public record WorkerPrincipal(long workerId, String name) {}
