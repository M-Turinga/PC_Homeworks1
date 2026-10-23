package Homework_5;

import Homework_5.task1.model.LogEntry;
import Homework_5.task1.model.PathStatistics;

import java.util.Map;

public interface Accumulator {
    void add(LogEntry entry);
    void incrementCorrupted();
    Map<String, PathStatistics> snapshotByPath();
    long corruptedLines();
}
