package com.eduvision.concurrent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * Demonstrates multithreading &amp; synchronization by grading many submissions in parallel.
 *
 * <p>A fixed {@link ExecutorService} (size {@code min(4, cores)}) runs {@code n}
 * {@link Callable} grading tasks. Their results are aggregated three different ways:
 * a {@link ConcurrentHashMap}, an {@link AtomicInteger}, and an explicit {@code synchronized}
 * block guarding a shared total. A {@link CountDownLatch} coordinates a separate coordinator
 * thread that only wakes once every task has finished. The two totals are expected to be equal,
 * which is the observable proof that the locking is correct.
 */
@Service
public class ParallelGradingService {

    private final int threadsUsed;
    private final ExecutorService pool;

    public ParallelGradingService() {
        this.threadsUsed = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
        this.pool = Executors.newFixedThreadPool(threadsUsed, runnable -> {
            Thread thread = new Thread(runnable, "grading-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Runs {@code n} grading tasks in parallel and returns the aggregated outcome.
     *
     * @param n number of tasks (must be positive)
     * @return map with {@code threadsUsed, tasksRun, atomicTotal, syncTotal, elapsedMillis, resultsSample}
     */
    public Map<String, Object> runDemo(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be a positive number of grading tasks");
        }
        long startNanos = System.nanoTime();

        ConcurrentHashMap<String, Integer> scores = new ConcurrentHashMap<>();
        AtomicInteger atomicTotal = new AtomicInteger(0);
        long[] syncTotal = new long[]{0L};
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger coordinatorObserved = new AtomicInteger(-1);

        // A dedicated worker thread blocked on the latch; it is released only after the last task
        // has counted down, then it records how many results were aggregated.
        Thread coordinator = new Thread(() -> {
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            coordinatorObserved.set(scores.size());
        }, "grading-coordinator");
        coordinator.setDaemon(true);
        coordinator.start();

        List<Future<GradingResult>> futures = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            final int taskId = i;
            Callable<GradingResult> task = () -> {
                try {
                    int score = grade(taskId);
                    scores.put("task-" + taskId, score);
                    atomicTotal.addAndGet(score);
                    // Explicit synchronized block: the classic mutual-exclusion primitive.
                    synchronized (ParallelGradingService.this) {
                        syncTotal[0] += score;
                    }
                    return new GradingResult(taskId, score, "graded");
                } finally {
                    done.countDown();
                }
            };
            futures.add(pool.submit(task));
        }

        List<GradingResult> results = new ArrayList<>(n);
        try {
            for (Future<GradingResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            coordinator.join(30_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("grading run interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("a grading task failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("a grading task timed out", e);
        }

        List<Map<String, Object>> sample = new ArrayList<>();
        for (GradingResult r : results) {
            if (sample.size() >= 5) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", r.taskId());
            row.put("score", r.score());
            row.put("remark", r.remark());
            sample.add(row);
        }

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("threadsUsed", threadsUsed);
        out.put("tasksRun", results.size());
        out.put("atomicTotal", atomicTotal.get());
        out.put("syncTotal", syncTotal[0]);
        out.put("elapsedMillis", elapsedMillis);
        out.put("resultsSample", sample);
        out.put("distinctTasks", Set.copyOf(scores.keySet()).size());
        out.put("totalsAgree", atomicTotal.get() == syncTotal[0]);
        out.put("coordinatorObserved", coordinatorObserved.get());
        return out;
    }

    /** Deterministic, thread-safe pseudo-score in 0..100 so runs are reproducible. */
    private int grade(int taskId) {
        return Math.floorMod(taskId * 37 + 11, 101);
    }

    public int threadsUsed() {
        return threadsUsed;
    }

    /** Stops the pool and waits for in-flight tasks (called on Spring context shutdown). */
    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
