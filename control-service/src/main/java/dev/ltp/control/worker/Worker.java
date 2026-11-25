package dev.ltp.control.worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A registered load generator (US-06). A worker that registers again under the same name reuses this
 * record. Only a hash of its per-worker secret is stored.
 */
@Entity
@Table(name = "workers")
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkerStatus status;

    @Column(nullable = false)
    private double cpus;

    @Column(name = "mem_mb", nullable = false)
    private int memMb;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected Worker() {}

    public Worker(String name, double cpus, int memMb, String version, String secretHash) {
        this.name = Objects.requireNonNull(name);
        this.cpus = cpus;
        this.memMb = memMb;
        this.version = Objects.requireNonNull(version);
        this.secretHash = Objects.requireNonNull(secretHash);
        this.status = WorkerStatus.ONLINE;
        this.registeredAt = Instant.now();
        this.lastHeartbeatAt = registeredAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public double getCpus() {
        return cpus;
    }

    public int getMemMb() {
        return memMb;
    }

    public String getVersion() {
        return version;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
