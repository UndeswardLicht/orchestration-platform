package dev.ltp.worker.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import dev.ltp.common.protocol.WorkerProtocol.Assign;
import dev.ltp.worker.config.WorkerProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts k6 for one segment in a resource-limited, isolated container (US-08, NFR-05, risk R5).
 *
 * <p>Isolation: CPU and memory limits (swap = memory), PID limit, read-only root filesystem with a
 * small {@code /tmp} tmpfs, all capabilities dropped, {@code no-new-privileges}, never privileged,
 * own bridge network (never {@code host}). Labels allow a sweep of leftovers after a worker crash.
 *
 * <p>Files: the script and the JSON output live in a per-attempt directory on the shared mount
 * ({@code ioVolume}, see ADR-0007). k6 runs as the owner of that directory, so the worker can read
 * and delete everything k6 writes.
 */
public class K6ContainerRunner {

    public static final String LABEL_MANAGED = "ltp.managed";
    public static final String LABEL_RUN_ID = "ltp.run-id";
    public static final String LABEL_SEGMENT_ID = "ltp.segment-id";
    public static final String LABEL_ATTEMPT = "ltp.attempt";

    /** Where the shared mount appears inside k6 containers. */
    static final String CONTAINER_IO_DIR = "/k6-io";

    /**
     * Only the tags the platform uses. Halves the JSON output (about 1.9 KB instead of 3.2 KB per
     * request, ADR-0007).
     */
    static final String SYSTEM_TAGS = "status,expected_response";

    static final long PIDS_LIMIT = 256;
    static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private static final Logger log = LoggerFactory.getLogger(K6ContainerRunner.class);
    private static final Pattern WINDOWS_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");

    private final DockerClient docker;
    private final String image;
    private final String ioSource;
    private final Path ioDir;
    private final String network;
    private final long nanoCpus;
    private final long memoryBytes;

    public K6ContainerRunner(DockerClient docker, WorkerProperties properties) {
        this(
                docker,
                properties.k6Image(),
                properties.ioVolume(),
                Path.of(properties.ioDir()),
                properties.k6Network(),
                properties.k6Cpus(),
                properties.k6MemoryMb());
    }

    public K6ContainerRunner(
            DockerClient docker,
            String image,
            String ioSource,
            Path ioDir,
            String network,
            double cpus,
            long memoryMb) {
        this.docker = docker;
        this.image = image;
        this.ioSource = ioSource;
        this.ioDir = ioDir;
        this.network = network;
        this.nanoCpus = Math.round(cpus * 1_000_000_000L);
        this.memoryBytes = memoryMb * 1024 * 1024;
    }

    /** Writes the script, then creates and starts the k6 container. */
    public K6Segment start(Assign assign, String script) {
        String relative = "run-%d/seg-%d-a%d".formatted(assign.runId(), assign.segmentId(), assign.attempt());
        Path dir = ioDir.resolve(relative);
        String containerDir = CONTAINER_IO_DIR + "/" + relative;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("script.js"), script);
            ensureImage();

            HostConfig hostConfig =
                    HostConfig.newHostConfig()
                            .withNanoCPUs(nanoCpus)
                            .withMemory(memoryBytes)
                            .withMemorySwap(memoryBytes)
                            .withPidsLimit(PIDS_LIMIT)
                            .withReadonlyRootfs(true)
                            .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=64m"))
                            .withCapDrop(Capability.ALL)
                            .withSecurityOpts(List.of("no-new-privileges"))
                            .withPrivileged(false)
                            .withNetworkMode(network)
                            .withMounts(List.of(mount()));

            var create =
                    docker.createContainerCmd(image)
                            .withName("ltp-k6-r%d-s%d-a%d".formatted(assign.runId(), assign.segmentId(), assign.attempt()))
                            .withCmd(command(assign, containerDir))
                            .withEnv(assign.env().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().toList())
                            .withLabels(
                                    Map.of(
                                            LABEL_MANAGED, "true",
                                            LABEL_RUN_ID, Long.toString(assign.runId()),
                                            LABEL_SEGMENT_ID, Long.toString(assign.segmentId()),
                                            LABEL_ATTEMPT, Integer.toString(assign.attempt())))
                            .withHostConfig(hostConfig);
            ownerOf(dir).ifPresent(create::withUser);
            String containerId = create.exec().getId();
            docker.startContainerCmd(containerId).exec();
            log.info("Started k6 container {} for run {} segment {} ({})", containerId, assign.runId(), assign.segmentId(), assign.segment());
            return new K6Segment(assign.runId(), assign.segmentId(), assign.attempt(), containerId, dir, dir.resolve("out.json"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare " + dir, e);
        }
    }

    /** k6 arguments: CLI flags from Control override the script's options (checked in SPIKE-1). */
    static List<String> command(Assign assign, String containerDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add("run");
        cmd.add("--quiet");
        cmd.add("--execution-segment");
        cmd.add(assign.segment());
        cmd.add("--execution-segment-sequence");
        cmd.add(assign.sequence());
        cmd.add("--system-tags");
        cmd.add(SYSTEM_TAGS);
        cmd.add("--out");
        cmd.add("json=" + containerDir + "/out.json");
        cmd.addAll(assign.k6Args());
        cmd.add(containerDir + "/script.js");
        return cmd;
    }

    public boolean isRunning(K6Segment segment) {
        Boolean running = inspect(segment).getState().getRunning();
        return Boolean.TRUE.equals(running);
    }

    /**
     * Waits for k6 to exit and returns its exit code. Polls the container state, because a single long
     * "wait" call can be cut by the socket proxy or HTTP timeouts.
     */
    public int awaitExit(K6Segment segment, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            InspectContainerResponse.ContainerState state = inspect(segment).getState();
            if (!Boolean.TRUE.equals(state.getRunning())) {
                Long exitCode = state.getExitCodeLong();
                return exitCode == null ? -1 : exitCode.intValue();
            }
            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL.toMillis());
        }
        throw new IllegalStateException("k6 container " + segment.containerId() + " still running after " + timeout);
    }

    /** Stops k6 (SIGTERM, then SIGKILL after {@code grace}); k6 v2 has no pause/stop command (US-04). */
    public void stop(K6Segment segment, Duration grace) {
        try {
            docker.stopContainerCmd(segment.containerId()).withTimeout((int) grace.toSeconds()).exec();
        } catch (NotModifiedException | NotFoundException e) {
            // already stopped or removed
        }
    }

    /** Removes the container and the attempt directory. Safe to call more than once. */
    public void remove(K6Segment segment) {
        try {
            docker.removeContainerCmd(segment.containerId()).withForce(true).exec();
        } catch (NotFoundException e) {
            // already removed
        }
        deleteRecursively(segment.dir());
    }

    /** Removes k6 containers left over from a previous worker process. Returns how many were removed. */
    public int removeLeftovers() {
        List<Container> leftovers =
                docker.listContainersCmd().withShowAll(true).withLabelFilter(Map.of(LABEL_MANAGED, "true")).exec();
        for (Container container : leftovers) {
            try {
                docker.removeContainerCmd(container.getId()).withForce(true).exec();
            } catch (NotFoundException e) {
                // removed in the meantime
            }
        }
        return leftovers.size();
    }

    private InspectContainerResponse inspect(K6Segment segment) {
        return docker.inspectContainerCmd(segment.containerId()).exec();
    }

    private Mount mount() {
        MountType type = isHostPath(ioSource) ? MountType.BIND : MountType.VOLUME;
        return new Mount().withType(type).withSource(ioSource).withTarget(CONTAINER_IO_DIR);
    }

    /** An absolute host path means a bind mount (tests); anything else is a named volume (Compose). */
    static boolean isHostPath(String source) {
        return source.startsWith("/") || WINDOWS_PATH.matcher(source).matches();
    }

    /**
     * "uid:gid" of the directory owner, so k6 can write its output there and the worker can delete it.
     * Empty where POSIX owners don't exist (Windows hosts with Docker Desktop, where bind mounts are
     * writable for every user).
     */
    static Optional<String> ownerOf(Path dir) throws IOException {
        try {
            Object uid = Files.getAttribute(dir, "unix:uid");
            Object gid = Files.getAttribute(dir, "unix:gid");
            return Optional.of(uid + ":" + gid);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void ensureImage() {
        try {
            docker.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            int colon = image.lastIndexOf(':');
            boolean hasTag = colon > image.lastIndexOf('/');
            String repository = hasTag ? image.substring(0, colon) : image;
            String tag = hasTag ? image.substring(colon + 1) : "latest";
            log.info("Pulling {}", image);
            try {
                docker.pullImageCmd(repository).withTag(tag).start().awaitCompletion(5, TimeUnit.MINUTES);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while pulling " + image, ie);
            }
        }
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", dir, e.toString());
        }
    }
}
