package Homework_5.task1.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record SequentialResult(
    Map<String, PathStatistics> byPath,
    Report report,
    List<Path> failedFiles,
    long corruptedLines
) {}