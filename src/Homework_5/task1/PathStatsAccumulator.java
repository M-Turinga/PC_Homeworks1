package Homework_5.task1;

import Homework_5.task1.model.LogEntry;
import Homework_5.task1.model.PathStatistics;

public final class PathStatsAccumulator {
    long requests;
    long successful;
    long errors;
    long responseBytes;
    long totalDurationMillis;
    long maxDurationMillis;
    
    public void add(LogEntry entry) {
        requests++;
        int status = entry.status();
        if (status >= 200 && status <= 399) {
            successful++;
        } else {
            errors++;
        }
        responseBytes += entry.responseBytes();
        totalDurationMillis += entry.durationMillis();
        if (entry.durationMillis() > maxDurationMillis) {
            maxDurationMillis = entry.durationMillis();
        }
    }
    
    public PathStatistics toImmutable() {
        return new PathStatistics(
            requests, successful, errors,
            responseBytes, totalDurationMillis, maxDurationMillis
        );
    }
}