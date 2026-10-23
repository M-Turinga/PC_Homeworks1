package Homework_5.task1.model;

public record PathStatistics(
    long requests,
    long successful,
    long errors,
    long responseBytes,
    long totalDurationMillis,
    long maxDurationMillis
) {}
