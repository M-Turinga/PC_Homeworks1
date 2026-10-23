package Homework_5;

final class ActiveFilesTracker {
        private int active = 0;
        private int maxActive = 0;

        synchronized void fileStarted() {
            active++;
            if (active > maxActive) {
                maxActive = active;
            }
        }

        synchronized void fileFinished() {
            active--;
        }

        synchronized int active() {
            return active;
        }

        synchronized int maxActive() {
            return maxActive;
        }
    }
