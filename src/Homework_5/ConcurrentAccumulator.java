package Homework_5;

import Homework_5.task1.PathStatsAccumulator;
import Homework_5.task1.model.LogEntry;
import Homework_5.task1.model.PathStatistics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class ConcurrentAccumulator implements Accumulator {

        private final ConcurrentHashMap<String, PathStatsAccumulator> map =
                new ConcurrentHashMap<>();
        private final AtomicLong corruptedLines = new AtomicLong();
    
    
    public void add(LogEntry entry) {
        PathStatsAccumulator acc =
            map.computeIfAbsent(entry.path(),
                _ -> new PathStatsAccumulator()
            );
        synchronized (acc) {
            acc.add(entry);
        }
    }


        public void incrementCorrupted() {
            corruptedLines.incrementAndGet();
        }


        public long corruptedLines() {
            return corruptedLines.get();
        }


        public Map<String, PathStatistics> snapshotByPath() {
            Map<String, PathStatistics> result = new TreeMap<>();
            for (Map.Entry<String, PathStatsAccumulator> e : map.entrySet()) {
                PathStatsAccumulator acc = e.getValue();
                PathStatistics stats;
                synchronized (acc) {
                    stats = acc.toImmutable();
                }
                result.put(e.getKey(), stats);
            }
            return result;
        }
    }
