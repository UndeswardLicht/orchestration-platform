package dev.ltp.control.scenario;

import dev.ltp.control.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Immutable;

/**
 * One exact script of a scenario (US-02). Never changed or deleted: Hibernate ignores changes to this
 * entity ({@link Immutable}), and a database trigger rejects any UPDATE, DELETE or TRUNCATE. Runs
 * reference a version, never the scenario, so every run can be reproduced exactly.
 */
@Entity
@Immutable
@Table(name = "scenario_versions")
public class ScenarioVersion {

    /** Scripts larger than this (in UTF-8 bytes) are rejected (US-01); the database has the same check. */
    public static final int MAX_SCRIPT_BYTES = 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "text")
    private String script;

    /** Lower-case hex SHA-256 of the script (UTF-8), used to skip saving an identical script. */
    @Column(nullable = false, length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ScenarioVersion() {}

    public ScenarioVersion(Scenario scenario, int version, String script, String sha256, User createdBy) {
        this.scenario = Objects.requireNonNull(scenario);
        this.version = version;
        this.script = Objects.requireNonNull(script);
        this.sha256 = Objects.requireNonNull(sha256);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public int getVersion() {
        return version;
    }

    public String getScript() {
        return script;
    }

    public String getSha256() {
        return sha256;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
