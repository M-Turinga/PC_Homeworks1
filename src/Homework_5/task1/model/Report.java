package Homework_5.task1.model;

public record Report(
    long totalRequests,
    long successfulRequests,
    long errorRequests,
    long responseBytes,
    long maxDurationMillis
) {}