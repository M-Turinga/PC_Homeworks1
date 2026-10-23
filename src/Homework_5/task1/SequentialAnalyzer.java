package Homework_5.task1;

import Homework_5.task1.model.LogEntry;
import Homework_5.task1.model.PathStatistics;
import Homework_5.task1.model.Report;
import Homework_5.task1.model.SequentialResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class SequentialAnalyzer {
    private SequentialAnalyzer() {}
    
    public static SequentialResult analyze(List<Path> files) {
        Map<String, PathStatsAccumulator> accs = new TreeMap<>();
        List<Path> failed = new ArrayList<>();
        long corrupted = 0;
        
        for (Path file : files) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LogEntry entry;
                    try {
                        entry = LogParser.parseLine(line);
                    } catch (IllegalArgumentException e) {
                        corrupted++;
                        continue;
                    }
                    accs.computeIfAbsent(entry.path(), k -> new PathStatsAccumulator()).add(entry);
                }
            } catch (IOException e) {
                failed.add(file);
            }
        }
        failed.sort(null);
        
        Map<String, PathStatistics> byPath = new TreeMap<>();
        for (var e : accs.entrySet()) {
            byPath.put(e.getKey(), e.getValue().toImmutable());
        }
        return new SequentialResult(byPath, buildReport(byPath), failed, corrupted);
    }
    
    public static Report buildReport(Map<String, PathStatistics> byPath) {
        long total = 0, ok = 0, err = 0, bytes = 0, max = 0;
        for (PathStatistics s : byPath.values()) {
            total += s.requests();
            ok    += s.successful();
            err   += s.errors();
            bytes += s.responseBytes();
            max = Math.max(max, s.maxDurationMillis());
        }
        return new Report(total, ok, err, bytes, max);
    }
    
    public static List<Path> listLogFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".log"))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать каталог " + dir, e);
        }
    }
    
    public static void print(SequentialResult r) {
        System.out.println("=== Статистика по URL ===");
        r.byPath().forEach((path, s) -> System.out.printf(
            "%-20s requests=%d success=%d errors=%d bytes=%d totalMs=%d maxMs=%d%n",
            path, s.requests(), s.successful(), s.errors(),
            s.responseBytes(), s.totalDurationMillis(), s.maxDurationMillis()));
        Report rep = r.report();
        System.out.println("=== Итог ===");
        System.out.printf(
            "requests=%d success=%d errors=%d bytes=%d maxMs=%d%n",
            rep.totalRequests(), rep.successfulRequests(), rep.errorRequests(),
            rep.responseBytes(), rep.maxDurationMillis());
        System.out.println("Повреждённых строк: " + r.corruptedLines());
        System.out.println("Неудачных файлов:   " + r.failedFiles().size());
        r.failedFiles().forEach(p -> System.out.println("  - " + p.getFileName()));
    }
    
    public static boolean resultsEqual(SequentialResult a, SequentialResult b) {
        return a.byPath().equals(b.byPath())
            && a.report().equals(b.report())
            && a.corruptedLines() == b.corruptedLines()
            && new HashSet<>(a.failedFiles()).equals(new HashSet<>(b.failedFiles()));
    }
}
