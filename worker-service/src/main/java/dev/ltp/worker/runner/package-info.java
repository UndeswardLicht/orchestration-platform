/**
 * Starts k6 in a resource-limited container via docker-java: CPU/RAM limits, cap_drop ALL, no-new-privileges, read-only rootfs, own bridge network, label ltp.run-id, removed after the run (US-08, R5).
 */
package dev.ltp.worker.runner;
