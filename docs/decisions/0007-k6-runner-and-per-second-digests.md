# ADR-0007: k6 runner and per-second digests (SPIKE-1 results)

- Status: Accepted
- Date: 2026-11-22
- Related: SPIKE-1, US-04, US-05, US-08, US-10, US-22, T-05, NFR-01, NFR-03, NFR-05

## Context
SPIKE-1 had to show, before any feature work, that the worker can run k6 v2 in a limited container on
one execution segment and turn its output into per-second t-digests (roadmap §1, exit demo of S0). It
also had to settle the `/k6-io` permissions and check the k6 v2 CLI behaviour that Control relies on.

Evidence: `worker-service/src/test/java/dev/ltp/worker/runner/K6ContainerRunnerTest.java`, and
manual `docker run` checks against `grafana/k6:2.4.0` on 2026-11-22.

## Findings

| Topic | Result |
|-------|--------|
| JSON output format | Unchanged from k6 v1: one `{"metric":…,"type":"Point","data":{"time","value","tags"}}` line per sample, plus `"type":"Metric"` declarations. |
| CLI overrides | `--vus 4 --duration 6s` overrides `options = {vus: 10, duration: '1m'}` in the script. |
| Execution segments | Segment `0:1/2` of `--vus 4` runs exactly 2 VUs (US-05). |
| `--rps` | Applies to each k6 instance and is **not** split by the segment. Control must send `limit / N` to each worker (US-22). |
| Removed flags | `--no-summary` no longer exists in v2. Use `--summary-mode=disabled` if needed. |
| Output volume | 13 lines per HTTP request. 3.2 KB per request with default tags, **1.9 KB** with `--system-tags status,expected_response`. At the 1 000 RPS cap for 30 min this is about 3.4 GB across the cluster, deleted after each segment. |
| Permissions | The k6 image runs as uid 12345, and the worker as `ltp` (uid 999), so k6 could not write to directories the worker created (`permission denied`). |
| Freshness | On a named volume (Compose), the newest line is 63–238 ms old: k6 flushes about every 200 ms. On a Windows Docker Desktop bind mount (local tests) it is 3–5 s. This only affects tests. |
| Sample order | Max disorder 0 ms at 400 RPS: k6 writes samples in time order. |
| Tailer speed | 260 000 lines/s on the dev laptop, about 20 000 RPS of k6 output, far above the 1 000 RPS cap. |
| Accuracy | Per-second p95 from the t-digest is within 1.3 % of the exact p95 (numpy "linear") for every second with ≥ 200 samples (NFR-03 allows 5 %). |
| Isolation (US-08, NFR-05) | `docker inspect`: `NanoCpus=1e9`, `Memory=MemorySwap=512 MiB`, `PidsLimit=256`, read-only rootfs + `/tmp` tmpfs, `CapDrop=[ALL]`, `no-new-privileges`, not privileged, own bridge network, labels `ltp.*`. |
| Stop (US-04) | `docker stop -t 3` ends k6 in about 2 s with exit code **105** (external abort). |
| Socket proxy | `CONTAINERS`, `IMAGES`, `POST`, `VOLUMES` are enough. `/info` and `/networks` stay blocked, because the network is set at container create. |

## Decision
1. **Files on a shared mount.** Each attempt gets `run-<run>/seg-<segment>-a<attempt>/` with
   `script.js` and `out.json`. In Compose the mount is the named volume `ltp-k6-io`; in tests it is a
   bind-mounted temp directory (an absolute path means a bind mount). The directory is deleted with the
   container.
2. **k6 runs as the owner of the attempt directory** (`--user uid:gid`, read from the directory). The
   worker can then read and delete everything k6 writes, without world-writable directories. On hosts
   without POSIX owners (Windows), no user is set.
3. **Trimmed system tags** (`status,expected_response`) roughly halve the output.
4. **Seconds close by data, not by the clock.** A second is emitted once a sample at least `grace` after
   its end has been read. The `vus` gauge advances this watermark even when no request finishes. The
   first version closed seconds by wall clock and dropped 40 297 samples when the tailer fell behind.
   The spike uses `grace = 1 s`. Because the measured disorder is 0 ms, T-05 can lower it (about
   500 ms) and make it configurable.
5. **Poll container state** (every 250 ms) instead of one long `wait` call, which the socket proxy or
   HTTP timeouts could cut.

## Alternatives considered
- **Stream JSON to stdout** (`--out json=-`, verified to work) and read it through `docker attach`: no
  disk use and no permissions problem. But it needs a hijacked attach connection through the socket
  proxy, and stdout also carries the summary. Keep it as a fallback if disk use becomes a problem.
- **World-writable directories** (`chmod 1777`): simpler, but any container could write there, and
  files owned by uid 12345 complicate cleanup.
- **A k6 metrics output** (Prometheus remote write, OpenTelemetry): these send pre-aggregated
  percentiles that cannot be merged, which breaks the "never average percentiles" rule.

## Consequences
- **Control, run planning (US-05, US-22):** pass `--rps <limit/N>` to every worker, and the CLI flags
  for the load profile.
- **Control, cancel (US-04):** exit code 105 on a cancelled segment is normal, not a failure.
- **Experiment mode (KPI-2, overview §10.5):** keep `out.json` (gzipped) instead of deleting it.
- **VM sizing:** worker disks need a few GB free for 30-minute runs at the cap.
- **NFR-01:** in production a second reaches the reporter about `1 s + grace + ~0.25 s` after it
  starts. NFR-01's 2 s budget starts at the worker report, so it is not affected. Measure the whole
  path in S6 anyway.
- **Local tests on Windows** see the 3–5 s bind-mount delay. Assertions must not depend on freshness.
