package Homework_5;

import Homework_5.task1.GenerateLogs;
import Homework_5.task1.model.SequentialResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static Homework_5.LogAnalyzer.*;

public class LogAnalysisAssignment {

    /** Проверяет генератор и чтение строк без участия студенческого анализатора. */
    private static void selfCheck() throws IOException {
        Path directory = Files.createTempDirectory("log-assignment-");
        GenerateLogs.generate(directory, 3, 7);

        int files = 0;
        int lines = 0;

        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                files++;
                Iterator<String> iterator = LogFileLines.iterator(path);

                while (iterator.hasNext()) {
                    String line = iterator.next();
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length != 5) {
                        throw new AssertionError(
                                "Unexpected line: " + line
                        );
                    }
                    Integer.parseInt(fields[2]);
                    Long.parseLong(fields[3]);
                    Long.parseLong(fields[4]);
                    lines++;
                }
            }
        }

        if (files != 3 || lines != 21) {
            throw new AssertionError(
                    "Expected 3 files and 21 lines, got "
                            + files + " files and " + lines + " lines"
            );
        }

        System.out.println(
                "OK: generated " + files
                        + " files and read " + lines + " lines"
        );
    }
    
    private static void printUsage() {
        System.out.println("Usage: java LogAnalysisAssignment generate <directory> <files> <lines>");
        System.out.println("       java LogAnalysisAssignment check");
        System.out.println("       java LogAnalysisAssignment sequential <directory>");
        System.out.println("       java LogAnalysisAssignment parallel <directory> <threads>");
        System.out.println("       java LogAnalysisAssignment compare <directory> <threads>");
        System.out.println("       java LogAnalysisAssignment compare-accumulators <directory> <threads> <rounds>");
        System.out.println("       java LogAnalysisAssignment generate-many <directory> <files> <lines> <uniquePaths>");
        System.out.println("       java LogAnalysisAssignment semaphore-test <directory> <threads> <limit>");
        System.out.println("       java LogAnalysisAssignment partial-test <directory>");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        switch (args[0]) {
            case "generate" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs");
                int fileCount = args.length > 2 ? Integer.parseInt(args[2]) : 20;
                int linesPerFile = args.length > 3 ? Integer.parseInt(args[3]) : 1_000;
                
                GenerateLogs.generate(directory, fileCount, linesPerFile);
                System.out.println("Generated " + fileCount + " files in " + directory);
            }
            
            case "check" -> selfCheck();
            
            case "sequential" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs");
                SequentialResult result = analyzeSequential(listLogFiles(directory));
                printSequential(result);
            }
            
            case "parallel" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs");
                int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;
                SequentialResult result = analyzeParallel(listLogFiles(directory), threads);
                printSequential(result);
            }
            
            case "compare" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs");
                int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;
                List<Path> files = listLogFiles(directory);
                
                SequentialResult seq = analyzeSequential(files);
                SequentialResult par = analyzeParallel(files, threads);
                
                System.out.println(resultsEqual(seq, par)
                    ? "OK: параллельная версия совпадает с последовательной"
                    : "FAIL: результаты различаются");
            }
            
            case "compare-accumulators" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs");
                int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;
                int rounds = args.length > 3 ? Integer.parseInt(args[3]) : 5;
                List<Path> files = listLogFiles(directory);
                
                SequentialResult seq = analyzeSequential(files);
                SequentialResult global = analyzeParallel(files, threads, new ParallelAccumulator());
                SequentialResult perUrl = analyzeParallel(files, threads, new ConcurrentAccumulator());
                
                System.out.println("seq == global : " + resultsEqual(seq, global));
                System.out.println("seq == perUrl : " + resultsEqual(seq, perUrl));
                
                long bestGlobal = Long.MAX_VALUE;
                long bestPerUrl = Long.MAX_VALUE;
                
                for (int i = 0; i < rounds; i++) {
                    long t0 = System.nanoTime();
                    analyzeParallel(files, threads, new ParallelAccumulator());
                    long t1 = System.nanoTime();
                    analyzeParallel(files, threads, new ConcurrentAccumulator());
                    long t2 = System.nanoTime();
                    
                    long g = (t1 - t0) / 1_000_000;
                    long u = (t2 - t1) / 1_000_000;
                    
                    if (g < bestGlobal) bestGlobal = g;
                    if (u < bestPerUrl) bestPerUrl = u;
                }
                
                System.out.println("global  : " + bestGlobal + " ms");
                System.out.println("per-url : " + bestPerUrl + " ms");
            }
            
            case "generate-many" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs-many");
                int fileCount = args.length > 2 ? Integer.parseInt(args[2]) : 20;
                int linesPerFile = args.length > 3 ? Integer.parseInt(args[3]) : 1_000;
                int uniquePaths = args.length > 4 ? Integer.parseInt(args[4]) : 1_000;
                generateManyUrls(directory, fileCount, linesPerFile, uniquePaths);
                System.out.println("Generated " + fileCount + " files with "
                    + uniquePaths + " unique URLs in " + directory);
            }
            
            case "semaphore-test" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs");
                int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;
                int limit = args.length > 3 ? Integer.parseInt(args[3]) : 2;
                List<Path> files = listLogFiles(directory);
                
                SequentialResult seq = analyzeSequential(files);
                SequentialResult par = analyzeParallelWithSemaphore(files, threads, limit);
                System.out.println("seq == par : " + resultsEqual(seq, par));
                
                long t0 = System.nanoTime();
                analyzeParallelWithSemaphore(files, threads, 1);
                long t1 = System.nanoTime();
                analyzeParallelWithSemaphore(files, threads, 4);
                long t2 = System.nanoTime();
                
                System.out.println("limit=1 : " + (t1 - t0) / 1_000_000 + " ms");
                System.out.println("limit=4 : " + (t2 - t1) / 1_000_000 + " ms");
            }
            
            case "counters-test" -> {
                Path directory = Path.of(
                    args.length > 1 ? args[1] : "logs"
                );
                int threads = args.length > 2
                    ? Integer.parseInt(args[2]) : 4;
                int limit = args.length > 3
                    ? Integer.parseInt(args[3]) : 2;
                
                List<Path> files = listLogFiles(directory);
                
                CountersResult result =
                    analyzeParallelWithSemaphoreCounters(
                        files, threads, limit
                    );
                
                AnalysisCounters.CountersSnapshot c = result.counters();
                
                System.out.println("=== Счётчики задания 6 ===");
                System.out.println("Обработано файлов: " + c.processedFiles());
                System.out.println("Прочитано строк: " + c.readLines());
                System.out.println("Корректных записей: " + c.validEntries());
                System.out.println("Повреждённых строк: " + c.corruptedLines());
                System.out.println("Ошибок чтения: " + c.readErrors());
                System.out.println("Активных файлов после завершения: " + c.activeFiles());
                System.out.println("Максимум активных файлов: " + c.maxActiveFiles());
                System.out.println("Лимит семафора: " + limit);
                System.out.println("Неудачных файлов: " + result.failedFiles().size());
                
                if (c.activeFiles() != 0) {
                    throw new AssertionError(
                        "После завершения остались активные файлы"
                    );
                }
                
                if (c.maxActiveFiles() > limit) {
                    throw new AssertionError(
                        "Превышен лимит активных файлов"
                    );
                }
                
                if (result.failedFiles().isEmpty()
                    && c.readErrors() == 0
                    && c.readLines() != c.validEntries() + c.corruptedLines()) {
                    throw new AssertionError(
                        "Сумма корректных и повреждённых записей не совпадает "
                            + "с количеством прочитанных строк"
                    );
                }
                
                System.out.println("OK: проверки счётчиков пройдены");
            }
            
            case "partial-test" -> {
                Path directory = Path.of(args.length > 1 ? args[1] : "logs-partial");
                LogAnalyzer.demonstratePartialReport(directory);
            }
            
            default -> printUsage();
        }
    }
}