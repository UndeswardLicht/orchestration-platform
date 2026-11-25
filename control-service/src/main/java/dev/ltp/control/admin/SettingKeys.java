package dev.ltp.control.admin;

/** Keys of the {@code settings} table and where each default comes from. */
public final class SettingKeys {

    /** NFR-06: maximum request rate of one run (default 1 000). */
    public static final String MAX_RPS = "max_rps";
    /** NFR-06: maximum duration of one run in seconds (default 1 800 = 30 min). */
    public static final String MAX_DURATION_SEC = "max_duration_sec";
    /** NFR-05: CPUs of one k6 container (default 1.0). */
    public static final String K6_CPU = "k6_cpu";
    /** NFR-05: memory of one k6 container in MB (default 512). */
    public static final String K6_MEM_MB = "k6_mem_mb";
    /** Evaluation plan: seconds excluded from summaries at the start of a run (default 10). */
    public static final String WARMUP_SEC = "warmup_sec";

    private SettingKeys() {}
}
