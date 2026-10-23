package Homework_5;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class LogFileLines {
        private LogFileLines() {}


    public static Iterator<String> iterator(Path path) {
        final BufferedReader reader;

        try {
            reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Cannot open " + path, exception);
        }

        return new Iterator<>() {
            private boolean closed = false;
            private String nextLine = readNext();

            private String readNext() {
                if (closed) {
                    return null;
                }

                try {
                    String line = reader.readLine();

                    if (line == null) {
                        close();
                    }

                    return line;
                } catch (IOException exception) {
                    close();
                    throw new RuntimeException("Cannot read " + path, exception);
                }
            }

            private void close() {
                if (closed) {
                    return;
                }

                closed = true;

                try {
                    reader.close();
                } catch (IOException exception) {
                    throw new RuntimeException(
                            "Cannot close " + path, exception
                    );
                }
            }

            @Override
            public boolean hasNext() {
                return nextLine != null;
            }

            @Override
            public String next() {
                if (nextLine == null) {
                    throw new NoSuchElementException();
                }

                String result = nextLine;
                nextLine = readNext();
                return result;
            }
        };
    }

}

