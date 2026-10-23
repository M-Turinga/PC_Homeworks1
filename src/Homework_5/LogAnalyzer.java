package Homework_5;

import Homework_5.task1.GenerateLogs;
import Homework_5.task1.SequentialAnalyzer;
import Homework_5.task1.model.*;
import Homework_5.task1.LogParser;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.StandardOpenOption;

/**
 * Многопоточный анализатор логов (задание 9)
 */
public final class LogAnalyzer {

    private static volatile boolean stopRequested = false;
    
    /** Сбрасывает запрос перед запуском нового анализа. */
    public static void resetStopRequest() {
        stopRequested = false;
    }

    /**
     * Завершает приём задач, ждёт их окончания и гарантирует попытку остановки пула
     * при прерывании главного потока или превышении времени ожидания.
     */
    private static void awaitTasks(ExecutorService executor, CountDownLatch latch)
        throws InterruptedException {
        executor.shutdown();
        try {
            latch.await();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                        "Пул потоков не завершился после shutdownNow()"
                    );
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }
    }
    private LogAnalyzer() {}
    
    public static SequentialResult analyzeSequential(List<Path> files) {
        return SequentialAnalyzer.analyze(files);
    }
    
    public static SequentialResult analyzeParallel(
        List<Path> files,
        int threadCount,
        Accumulator accumulator
    ) throws InterruptedException {
        
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount должен быть > 0");
        }
        
        List<Path> failedFiles = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(files.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (Path file : files) {
            Runnable task = () -> {
                try {
                    Iterator<String> iterator = LogFileLines.iterator(file);
                    while (iterator.hasNext()) {
                        String line = iterator.next();
                        LogEntry entry;
                        try {
                            entry = LogParser.parseLine(line);
                        } catch (IllegalArgumentException e) {
                            accumulator.incrementCorrupted();
                            continue;
                        }
                        accumulator.add(entry);
                    }
                } catch (RuntimeException e) {
                    failedFiles.add(file);
                } finally {
                    latch.countDown();
                }
            };
            executor.submit(task);
        }
        
        awaitTasks(executor, latch);
        
        Map<String, PathStatistics> byPath = accumulator.snapshotByPath();
        
        Report report = SequentialAnalyzer.buildReport(byPath);
        
        List<Path> failedCopy;
        synchronized (failedFiles) {
            failedCopy = new ArrayList<>(failedFiles);
        }
        Collections.sort(failedCopy);
        
        return new SequentialResult(
            byPath, report, failedCopy, accumulator.corruptedLines()
        );
    }
    
    public static SequentialResult analyzeParallel(
        List<Path> files,
        int threadCount
    ) throws InterruptedException {
        return analyzeParallel(files, threadCount, new ParallelAccumulator());
    }
    
    public static List<Path> listLogFiles(Path directory) {
        return SequentialAnalyzer.listLogFiles(directory);
    }
    
    public static void printSequential(SequentialResult result) {
        SequentialAnalyzer.print(result);
    }
    
    public static boolean resultsEqual(SequentialResult a, SequentialResult b) {
        return SequentialAnalyzer.resultsEqual(a, b);
    }
    
    public static void generateManyUrls(
        Path outputDirectory,
        int fileCount,
        int linesPerFile,
        int uniquePathCount
    ) throws IOException {
        if (fileCount < 0 || linesPerFile < 0 || uniquePathCount <= 0) {
            throw new IllegalArgumentException("Counts must be valid");
        }
        
        Files.createDirectories(outputDirectory);
        Random random = new Random(42);
        
        String[] methods = { "GET", "POST", "PUT", "DELETE" };
        int[] statuses = { 200, 200, 200, 201, 204, 301, 400, 401, 404, 500 };
        
        for (int fileId = 0; fileId < fileCount; fileId++) {
            Path file = outputDirectory.resolve("access-%03d.log".formatted(fileId));
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (int line = 0; line < linesPerFile; line++) {
                    String method = methods[random.nextInt(methods.length)];
                    String path = "/resource/" + random.nextInt(uniquePathCount);
                    int status = statuses[random.nextInt(statuses.length)];
                    long responseBytes = 100L + random.nextInt(100_000);
                    long durationMillis = 1L + random.nextInt(500);
                    writer.write("%s %s %d %d %d%n".formatted(
                        method, path, status, responseBytes, durationMillis));
                }
            }
        }
    }
    
    public static SequentialResult analyzeParallelWithSemaphore(
        List<Path> files,
        int threadCount,
        Accumulator accumulator,
        int semaphoreLimit
    ) throws InterruptedException {
        
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount должен быть > 0");
        }
        if (semaphoreLimit <= 0) {
            throw new IllegalArgumentException("semaphoreLimit должен быть > 0");
        }
        
        Semaphore semaphore = new Semaphore(semaphoreLimit, true);
        
        ActiveFilesTracker tracker = new ActiveFilesTracker();
        List<Path> failedFiles = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(files.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        resetStopRequest();

        for (Path file : files) {
            Runnable task = () -> {
                boolean acquired = false;
                try {
                    semaphore.acquire();
                    acquired = true;

                    tracker.fileStarted();

                    Iterator<String> iterator = LogFileLines.iterator(file);
                    while (iterator.hasNext() && !stopRequested) {
                        String line = iterator.next();
                        LogEntry entry;

                        try {
                            entry = LogParser.parseLine(line);
                        } catch (IllegalArgumentException e) {
                            accumulator.incrementCorrupted();
                            continue;
                        }

                        accumulator.add(entry);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failedFiles.add(file);
                } finally {
                    if (acquired) {
                        tracker.fileFinished();
                        semaphore.release();
                    }
                    latch.countDown();
                }
            };
            executor.submit(task);
        }
        
        awaitTasks(executor, latch);
        
        Map<String, PathStatistics> byPath = accumulator.snapshotByPath();
        
        Report report = SequentialAnalyzer.buildReport(byPath);
        
        List<Path> failedCopy;
        synchronized (failedFiles) {
            failedCopy = new ArrayList<>(failedFiles);
        }
        Collections.sort(failedCopy);
        
        System.out.println("maxActiveFiles = " + tracker.maxActive()
            + " (лимит " + semaphoreLimit + ")");
        
        return new SequentialResult(
            byPath, report, failedCopy, accumulator.corruptedLines()
        );
    }
    
    public static SequentialResult analyzeParallelWithSemaphore(
        List<Path> files,
        int threadCount,
        int semaphoreLimit
    ) throws InterruptedException {
        return analyzeParallelWithSemaphore(
            files, threadCount, new ParallelAccumulator(), semaphoreLimit
        );
    }
    
    public static CountersResult analyzeParallelWithSemaphoreCounters(
        List<Path> files,
        int threadCount,
        Accumulator accumulator,
        int semaphoreLimit
    ) throws InterruptedException {
        
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount должен быть > 0");
        }
        if (semaphoreLimit <= 0) {
            throw new IllegalArgumentException("semaphoreLimit должен быть > 0");
        }
        
        Semaphore semaphore = new Semaphore(semaphoreLimit, true);
        AnalysisCounters counters = new AnalysisCounters();
        
        List<Path> failedFiles = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(files.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (Path file : files) {
            Runnable task = () -> {
                boolean acquired = false;
                boolean fileOk = false;
                try {
                    semaphore.acquire();
                    acquired = true;
                    
                    counters.fileStarted();
                    
                    Iterator<String> iterator = LogFileLines.iterator(file);
                    while (iterator.hasNext()) {
                        String line = iterator.next();
                        counters.lineRead();
                        
                        LogEntry entry;
                        try {
                            entry = LogParser.parseLine(line);
                        } catch (IllegalArgumentException e) {
                            counters.corruptedLine();
                            accumulator.incrementCorrupted();
                            continue;
                        }
                        
                        counters.validEntry();
                        accumulator.add(entry);
                    }
                    
                    fileOk = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    counters.readError();
                    failedFiles.add(file);
                } finally {
                    if (acquired) {
                        counters.fileFinished();
                        semaphore.release();
                    }
                    if (fileOk) {
                        counters.fileProcessed();
                    }
                    latch.countDown();
                }
            };
            executor.submit(task);
        }
        
        awaitTasks(executor, latch);
        
        Map<String, PathStatistics> byPath = accumulator.snapshotByPath();
        
        Report report = SequentialAnalyzer.buildReport(byPath);
        
        List<Path> failedCopy;
        synchronized (failedFiles) {
            failedCopy = new ArrayList<>(failedFiles);
        }
        Collections.sort(failedCopy);
        
        return new CountersResult(
            byPath, report, failedCopy, counters.snapshot()
        );
    }
    
    public static CountersResult analyzeParallelWithSemaphoreCounters(
        List<Path> files,
        int threadCount,
        int semaphoreLimit
    ) throws InterruptedException {
        return analyzeParallelWithSemaphoreCounters(
            files, threadCount, new ParallelAccumulator(), semaphoreLimit
        );
    }
    
    /**
     * Демонстрация частичного отчёта (задание 9)
     */
    public static void demonstratePartialReport(Path directory) throws Exception {
        System.out.println("=== ЧАСТИЧНЫЙ ОТЧЁТ: ДЕМОНСТРАЦИЯ (задание 9) ===");
        System.out.println();

        Files.createDirectories(directory);
        GenerateLogs.generate(directory, 5, 100);
        List<Path> cleanFiles = listLogFiles(directory);
        System.out.println("Сгенерировано файлов: " + cleanFiles.size());

        SequentialResult baseline = analyzeSequential(cleanFiles);
        System.out.println();
        System.out.println("--- Эталон (без повреждений) ---");
        System.out.println("URL в отчёте:        " + baseline.byPath().size());
        System.out.println("Запросов всего:      " + baseline.report().totalRequests());
        System.out.println("Повреждённых строк:  " + baseline.corruptedLines());
        System.out.println("Неудачных файлов:    " + baseline.failedFiles().size());
        
        // Портим один файл
        Path corruptedFile = directory.resolve("access-000.log");
        try (BufferedWriter writer = Files.newBufferedWriter(
            corruptedFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.APPEND)) {
            writer.write("это не лог-строка");
            writer.newLine();
            writer.write("GET /api/users NOT_A_NUMBER 123 4");
            writer.newLine();
            writer.write("GET /api/users 200 123 NOT_A_NUMBER");
            writer.newLine();
        }

        List<Path> dirtyFiles = new ArrayList<>(listLogFiles(directory));
        Path missingFile = directory.resolve("access-999.log");
        dirtyFiles.add(missingFile);
        Collections.sort(dirtyFiles);

        SequentialResult dirty = analyzeSequential(dirtyFiles);
        System.out.println();
        System.out.println("--- С повреждениями (3 битые строки + 1 отсутствующий файл) ---");
        System.out.println("URL в отчёте:        " + dirty.byPath().size());
        System.out.println("Запросов всего:      " + dirty.report().totalRequests());
        System.out.println("Повреждённых строк:  " + dirty.corruptedLines());
        System.out.println("Неудачных файлов:    " + dirty.failedFiles().size());
        for (Path p : dirty.failedFiles()) {
            System.out.println("  - " + p.getFileName());
        }

        CountersResult par = analyzeParallelWithSemaphoreCounters(dirtyFiles, 4, 2);
        System.out.println();
        System.out.println("--- Параллельный прогон (4 потока, лимит семафора 2) ---");
        System.out.println("processedFiles:      " + par.counters().processedFiles());
        System.out.println("readLines:           " + par.counters().readLines());
        System.out.println("validEntries:        " + par.counters().validEntries());
        System.out.println("corruptedLines:      " + par.counters().corruptedLines());
        System.out.println("readErrors:          " + par.counters().readErrors());
        System.out.println("activeFiles:         " + par.counters().activeFiles());
        System.out.println("maxActiveFiles:      " + par.counters().maxActiveFiles());
        System.out.println("failedFiles:         " + par.failedFiles().size());

        System.out.println();
        System.out.println("=== ПРОВЕРКИ ===");
        
        boolean noErrorsInBaseline = baseline.failedFiles().isEmpty()
            && baseline.corruptedLines() == 0;
        System.out.println((noErrorsInBaseline ? "  OK  " : "  FAIL")
            + " эталон без ошибок: failedFiles=0, corrupted=0");
        
        boolean missingReported = dirty.failedFiles().size() == 1
            && dirty.failedFiles().contains(missingFile);
        System.out.println((missingReported ? "  OK  " : "  FAIL")
            + " отсутствующий файл зафиксирован в failedFiles");
        
        boolean corruptedSkip = dirty.corruptedLines() == 3
            && !dirty.failedFiles().contains(corruptedFile);
        System.out.println((corruptedSkip ? "  OK  " : "  FAIL")
            + " 3 битые строки пропущены, access-000.log НЕ в failedFiles");
        
        boolean dataPreserved = dirty.report().totalRequests()
            == baseline.report().totalRequests();
        System.out.println((dataPreserved ? "  OK  " : "  FAIL")
            + " данные корректных строк сохранены (запросов столько же)");
        
        boolean invariant = true;
        for (PathStatistics s : dirty.byPath().values()) {
            if (s.requests() != s.successful() + s.errors()) {
                invariant = false;
                break;
            }
        }
        System.out.println((invariant ? "  OK  " : "  FAIL")
            + " инвариант requests = successful + errors для каждого URL");
        
        boolean parallelConsistent = par.failedFiles().size() == 1
            && par.counters().corruptedLines() == 3
            && par.counters().activeFiles() == 0;
        System.out.println((parallelConsistent ? "  OK  " : "  FAIL")
            + " параллельный прогон видит те же ошибки, activeFiles=0");
        
        boolean countersConsistent = par.counters().readLines()
            == par.counters().validEntries() + par.counters().corruptedLines();
        System.out.println((countersConsistent ? "  OK  " : "  FAIL")
            + " readLines = validEntries + corruptedLines (задание 6)");
        
        System.out.println();
        System.out.println("Политика:");
        System.out.println("  * битая строка -> пропуск, файл читается дальше;");
        System.out.println("  * битый файл   -> failedFiles, остальные обрабатываются;");
        System.out.println("  * отчёт строится по всем доступным данным (partial report).");
    }
}
