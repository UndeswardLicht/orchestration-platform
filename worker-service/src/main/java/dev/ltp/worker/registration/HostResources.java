package dev.ltp.worker.registration;

import java.lang.management.ManagementFactory;

/**
 * CPUs and memory this worker can use, as reported on registration (US-06). Inside a container the JVM
 * sees the container's limits, not the whole host.
 */
public record HostResources(int cpus, long memMb) {

    public static HostResources detect() {
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());
        long memMb = 1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            memMb = Math.max(1, os.getTotalMemorySize() / (1024 * 1024));
        }
        return new HostResources(cpus, Math.min(memMb, Integer.MAX_VALUE));
    }
}
