package Homework_5.task1.model;

public record LogEntry(
    String method,
    String path,
    int status,
    long responseBytes,
    long durationMillis
) {}
