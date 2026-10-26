package com.asata.org.oro.context;

/**
 * The sizes of the database pools: given (ORO_DB_POOL_SIZE, ORO_WORKER_DB_POOL_SIZE), or from the CPUs Oro may use, as
 * the load test measured them (backend/perf, the pools): on 2 CPUs the CPUs are the limit and 10 and 5 are enough; on
 * 4 the pools were, and 20 and 10 halved the p95 at 3,000 people. Above 20 and 10 nothing was measured on a quiet
 * machine: the sizes stop there unless given.
 */
public final class PoolSizes {

    private PoolSizes() {
    }

    /** the API's: 5 a CPU, 10 to 20 */
    public static int api(int given, int cpus) {
        return given > 0 ? given : Math.clamp(5L * cpus, 10, 20);
    }

    /** the system's: 2.5 a CPU, 5 to 10 */
    public static int system(int given, int cpus) {
        return given > 0 ? given : Math.clamp((5L * cpus + 1) / 2, 5, 10);
    }
}
