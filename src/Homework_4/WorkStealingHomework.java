package Homework_4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class WorkStealingHomework {

    enum TaskDistribution {
        UNIFORM,
        PERIODIC,
        PARETO
    }

    interface Shutdownable {
        void shutdown();
    }

    public interface ShutdownableExecutor extends Shutdownable, Executor {
    }

    static class ThreadPerTaskExecutor implements ShutdownableExecutor {
        List<Thread> threads = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            var t = new Thread(command);
            threads.add(t);
            t.start();
        }

        public void shutdown() {
            threads.forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    static class RoundRobinExecutor implements ShutdownableExecutor {

        protected static final Runnable EXIT_TASK = () -> {
        };

        AtomicInteger counter = new AtomicInteger(0);

        List<Thread> threads;
        Vector<BlockingDeque<Runnable>> tasks;
        volatile boolean isShuttingDown = false;

        RoundRobinExecutor(int threads) {
            Supplier<IntStream> stream = () -> IntStream.iterate(0, x -> x < threads, x -> x + 1);
            tasks = new Vector<>(
                    stream.get()
                            .mapToObj(x -> new LinkedBlockingDeque<Runnable>())
                            .toList());
            this.threads = stream.get()
                    .mapToObj(this::spawnThread)
                    .toList();
            this.threads.forEach(Thread::start);
        }

        Thread spawnThread(int id) {
            return new Thread(() ->
            {
                while (true) {
                    Runnable task;
                    try {
                        task = tasks.get(id).takeFirst();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (task == EXIT_TASK)
                        return;
                    task.run();
                }
            }
            );
        }

        @Override
        public void shutdown() {
            synchronized (this) {
                if (!isShuttingDown) {
                    isShuttingDown = true;
                    tasks.forEach(queue -> queue.addLast(EXIT_TASK));
                }
            }
            threads.forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public synchronized void execute(Runnable command) {
            if (isShuttingDown)
                throw new RejectedExecutionException("Executor is shutting down");
            tasks.get(counter.addAndGet(1) % threads.size()).addLast(command);
        }
    }

    static class WorkStealingExecutor extends RoundRobinExecutor {

        WorkStealingExecutor(int threads) {
            super(threads);
        }

        @Override
        Thread spawnThread(int id) {
            return new Thread(() -> {
                while (true) {
                    Runnable task = tasks.get(id).pollFirst();
                    if (task == null)
                        task = stealTask(id);

                    if (task == null) {
                        try {
                            task = tasks.get(id).pollFirst(1, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        if (task == null)
                            continue;
                    }

                    if (task == EXIT_TASK) {
                        task = stealTask(id);
                        if (task == null)
                            return;
                        tasks.get(id).addLast(EXIT_TASK);
                    }

                    task.run();
                }
            });
        }

        private Runnable stealTask(int thiefId) {
            for (int offset = 1; offset < tasks.size(); offset++) {
                var queue = tasks.get((thiefId + offset) % tasks.size());
                var task = queue.pollLast();

                if (task == EXIT_TASK) {
                    task = queue.pollLast();
                    queue.addLast(EXIT_TASK);
                }

                if (task != null)
                    return task;
            }
            return null;
        }
    }

    static class FixedPoolExecutor implements ShutdownableExecutor {
        private final ExecutorService pool;

        FixedPoolExecutor(int threads) {
            this.pool = Executors.newFixedThreadPool(threads);
        }

        @Override
        public void execute(Runnable command) {
            pool.execute(command);
        }

        @Override
        public void shutdown() {
            pool.shutdown();

            try {
                if (!pool.awaitTermination(1, TimeUnit.HOURS)) {
                    pool.shutdownNow();
                    throw new RuntimeException(
                            "Executor did not terminate in time"
                    );
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    enum ExecutorKind { ROUND_ROBIN, WORK_STEALING, FIXED_POOL, THREAD_PER_TASK }
    static ShutdownableExecutor createExecutor(ExecutorKind kind, int threadCount) {
        return switch (kind) {
            case ROUND_ROBIN     -> new RoundRobinExecutor(threadCount);
            case WORK_STEALING   -> new WorkStealingExecutor(threadCount);
            case FIXED_POOL      -> new FixedPoolExecutor(threadCount);
            case THREAD_PER_TASK -> new ThreadPerTaskExecutor();
        };
    }

    private static volatile long blackHoleSink;

    static void blackHole(long difficulty) {
        long acc = 1;

        for (long i = 0; i < difficulty; i++) {
            acc = acc * 31 + i;
        }

        blackHoleSink = acc;
    }

    static long calibrateBlackHole() {
        for (int i = 0; i < 5; i++) {
            blackHole(10_000_000L);
        }
        long difficulty = 1_000_000L;
        while (true) {
            long start = System.nanoTime();
            blackHole(difficulty);
            long elapsed = System.nanoTime() - start;
            if (elapsed >= 10_000_000L) {
                return difficulty * 1_000_000L / elapsed;
            }
            difficulty *= 2;
        }
    }

    static final long BLACKHOLE_UNIT = calibrateBlackHole();

    static Runnable createTaskBlackHole(int durationMs) {
        long difficulty = durationMs * BLACKHOLE_UNIT;
        return () -> blackHole(difficulty);
    }

    // Это вещи, которые нам нужны
    static final int THREAD_NUMBER = 10;
    static final int TASK_NUMBER = 100_000;
    static final int TARGET_OPTIMAL_FULL_TIME = 10_000; //ms = 10 s

    // Это всякое вспомогательное побочное
    static final int MEAN_TASK_TIME = (int) Math.round((TARGET_OPTIMAL_FULL_TIME + 0d) / TASK_NUMBER * THREAD_NUMBER); //ms
    static final int LOWER_TASK_TIME_BOUND = 0;
    static final int HIGHER_TASK_TIME_BOUND = MEAN_TASK_TIME * 2 + 1;
    static final int ESTIMATED_OPTIMAL_TIME = MEAN_TASK_TIME * TASK_NUMBER / THREAD_NUMBER;
    static final long TARGET_TOTAL_TASK_TIME = (long) TARGET_OPTIMAL_FULL_TIME * THREAD_NUMBER;
    static final long RANDOM_SEED = 42;
    static final double PARETO_SHAPE = 1.5;
    static final int PARETO_RAW_MAX_TASK_TIME = MEAN_TASK_TIME * 100;

    static Runnable createTask(int duration) {
        return () -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    static int createTaskDuration(TaskDistribution distribution, int taskId, Random random) {
        return switch (distribution) {
            case UNIFORM -> random.nextInt(LOWER_TASK_TIME_BOUND, HIGHER_TASK_TIME_BOUND);
            case PERIODIC -> taskId % THREAD_NUMBER == 0
                    ? MEAN_TASK_TIME * THREAD_NUMBER
                    : 0;
            case PARETO -> {
                var scale = MEAN_TASK_TIME * (PARETO_SHAPE - 1) / PARETO_SHAPE;
                var duration = scale / Math.pow(1 - random.nextDouble(), 1 / PARETO_SHAPE);
                yield (int) Math.min(Math.round(duration), PARETO_RAW_MAX_TASK_TIME);
            }
        };
    }

    static int[] createTaskDurations(TaskDistribution distribution) {
        var random = new Random(RANDOM_SEED);
        var durations = new int[TASK_NUMBER];

        long totalDuration = 0;
        for (int taskId = 0; taskId < TASK_NUMBER; taskId++) {
            durations[taskId] = createTaskDuration(distribution, taskId, random);
            totalDuration += durations[taskId];
        }

        double scale = (double) TARGET_TOTAL_TASK_TIME / totalDuration;
        double remainder = 0;
        long normalizedTotal = 0;
        for (int taskId = 0; taskId < TASK_NUMBER; taskId++) {
            double scaledDuration = durations[taskId] * scale + remainder;
            durations[taskId] = (int) scaledDuration;
            remainder = scaledDuration - durations[taskId];
            normalizedTotal += durations[taskId];
        }

        for (int taskId = 0; normalizedTotal < TARGET_TOTAL_TASK_TIME; taskId++) {
            durations[taskId % TASK_NUMBER]++;
            normalizedTotal++;
        }
        for (int taskId = 0; normalizedTotal > TARGET_TOTAL_TASK_TIME; taskId++) {
            int index = taskId % TASK_NUMBER;
            if (durations[index] > 0) {
                durations[index]--;
                normalizedTotal--;
            }
        }

        return durations;
    }

    static List<Runnable> createTasks(TaskDistribution distribution) {
        var durations = createTaskDurations(distribution);
        var tasks = new ArrayList<Runnable>(TASK_NUMBER);
        for (int taskId = 0; taskId < TASK_NUMBER; taskId++) {
            tasks.add(createTask(durations[taskId]));
        }
        return tasks;
    }

    static List<Runnable> createTasksSleep(TaskDistribution distribution) {
        return createTasks(distribution);
    }

    static List<Runnable> createTasksBlackHole(TaskDistribution distribution) {
        var durations = createTaskDurations(distribution);
        var tasks = new ArrayList<Runnable>(TASK_NUMBER);
        for (int taskId = 0; taskId < TASK_NUMBER; taskId++) {
            tasks.add(createTaskBlackHole(durations[taskId]));
        }
        return tasks;
    }

    public record Pair<A, B>(A first, B second) {
    }

    enum Workload { SLEEP, BLACK_HOLE }

    static Pair<Long, Long> measureExecutor(
            ExecutorKind kind,
            TaskDistribution distribution,
            Workload workload
    ) {
        ShutdownableExecutor executor = createExecutor(kind, THREAD_NUMBER);
        List<Runnable> tasks = (workload == Workload.SLEEP)
                ? createTasksSleep(distribution)
                : createTasksBlackHole(distribution);

        long start = System.nanoTime();
        for (Runnable task : tasks) {
            executor.execute(task);
        }
        long submitEnd = System.nanoTime();

        executor.shutdown();
        long finish = System.nanoTime();

        return new Pair<>(submitEnd - start, finish - start);
    }

    public static void main(String[] args) {
        System.out.println("Target optimal time: " + TARGET_OPTIMAL_FULL_TIME + " ms");
        System.out.println("Estimated optimal time: " + ESTIMATED_OPTIMAL_TIME + " ms");
        System.out.println("BlackHole unit (iterations per ms): " + BLACKHOLE_UNIT);
        System.out.println();

        long probeStart = System.nanoTime();
        blackHole((long) MEAN_TASK_TIME * BLACKHOLE_UNIT);
        System.out.printf("One blackHole task: expected %d ms, got %.2f ms%n%n",
                MEAN_TASK_TIME, (System.nanoTime() - probeStart) / 1_000_000d);

        ExecutorKind[] kinds = {
                ExecutorKind.ROUND_ROBIN,
                ExecutorKind.WORK_STEALING,
                ExecutorKind.FIXED_POOL,
                ExecutorKind.THREAD_PER_TASK,
        };


        for (TaskDistribution distribution : TaskDistribution.values()) {
            System.out.println("=== " + distribution + " ===");

            for (Workload workload : Workload.values()) {
                System.out.println("-- workload: " + workload + " --");

                for (ExecutorKind kind : ExecutorKind.values()) {

                    if (kind == ExecutorKind.THREAD_PER_TASK) {
                        continue;
                    }

                    Pair<Long, Long> result =
                            measureExecutor(kind, distribution, workload);

                    System.out.printf(
                            "%-16s submit: %8.2f ms, exec: %8.2f ms%n",
                            kind,
                            result.first() / 1_000_000d,
                            result.second() / 1_000_000d
                    );
                }
            }

            Pair<Long, Long> result =
                    measureExecutor(
                            ExecutorKind.THREAD_PER_TASK,
                            TaskDistribution.UNIFORM,
                            Workload.SLEEP
                    );
            System.out.println("=== THREAD_PER_TASK for UNIFORM: " + result + " ===");
        }
    }
}