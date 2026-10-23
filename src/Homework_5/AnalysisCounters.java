package Homework_5;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Атомарные счётчики анализа логов для задания 6. */
public final class AnalysisCounters {
    private final AtomicLong processedFiles = new AtomicLong();
    private final AtomicLong readLines = new AtomicLong();
    private final AtomicLong validEntries = new AtomicLong();
    private final AtomicLong corruptedLines = new AtomicLong();
    private final AtomicLong readErrors = new AtomicLong();
    private final AtomicInteger activeFiles = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();

    public record CountersSnapshot(long processedFiles, long readLines, long validEntries,
            long corruptedLines, long readErrors, int activeFiles, int maxActiveFiles) {}
    
    public void fileStarted() {
        maxActive.accumulateAndGet(
            activeFiles.incrementAndGet(),
            Math::max
        );
    }
    public void fileFinished() { activeFiles.decrementAndGet(); }
    public void fileProcessed() { processedFiles.incrementAndGet(); }
    public void lineRead() { readLines.incrementAndGet(); }
    public void validEntry() { validEntries.incrementAndGet(); }
    public void corruptedLine() { corruptedLines.incrementAndGet(); }
    public void readError() { readErrors.incrementAndGet(); }
    
    public CountersSnapshot snapshot() {
        return new CountersSnapshot(processedFiles.get(), readLines.get(), validEntries.get(),
                corruptedLines.get(), readErrors.get(), activeFiles.get(), maxActive.get());
    }
}
