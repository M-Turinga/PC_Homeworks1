package Homework_5;

import Homework_5.task1.PathStatsAccumulator;
import Homework_5.task1.model.LogEntry;
import Homework_5.task1.model.PathStatistics;

import java.util.Map;
import java.util.TreeMap;

final class ParallelAccumulator implements Accumulator {
        private final Map<String, PathStatsAccumulator> map = new TreeMap<>();
        private long corruptedLines = 0;

        public synchronized void add(LogEntry entry) {
            PathStatsAccumulator acc = map.computeIfAbsent(entry.path(), k -> new PathStatsAccumulator());
            acc.add(entry);
        }

        public synchronized void incrementCorrupted() {
            corruptedLines++;
        }

        public synchronized Map<String, PathStatistics> snapshotByPath() {
            Map<String, PathStatistics> result = new TreeMap<>();
            for (Map.Entry<String, PathStatsAccumulator> e : map.entrySet()) {
                result.put(e.getKey(), e.getValue().toImmutable());
            }
            return result;
        }

        public synchronized long corruptedLines() {
            return corruptedLines;
        }
    }
