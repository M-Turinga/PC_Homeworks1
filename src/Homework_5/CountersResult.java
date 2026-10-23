package Homework_5;

import Homework_5.task1.model.PathStatistics;
import Homework_5.task1.model.Report;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public record CountersResult(
    Map<String, PathStatistics> byPath,
    Report report,
    List<Path> failedFiles,
    AnalysisCounters.CountersSnapshot counters
) {
}