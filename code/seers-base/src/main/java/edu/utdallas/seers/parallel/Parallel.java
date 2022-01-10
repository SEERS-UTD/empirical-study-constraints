package edu.utdallas.seers.parallel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public class Parallel {
    private Parallel() {
    }

    /**
     * Runs the task within a new {@link ForkJoinPool} with the specified number of threads. Used
     * to control the amount of threads used when running parallel streams. Normally useful when
     * an amount smaller than the default is needed. A parallel stream will use the number of
     * cores by default.
     *
     * @param task    Task to execute.
     * @param threads Number of threads.
     */
    public static void runWithThreads(Runnable task, int threads)
            throws ExecutionException, InterruptedException {
        new ForkJoinPool(threads)
                .submit(task)
                .get();
    }
}
