package Homework_5.task1;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public final class GenerateLogs {
    private static final String[] METHODS = { "GET", "POST", "PUT", "DELETE" };
    private static final String[] PATHS = {
        "/", "/index.html", "/login", "/api/users",
        "/api/orders", "/api/products", "/search", "/health"
    };
    private static final int[] STATUSES = {
        200, 200, 200, 201, 204, 301, 400, 401, 404, 500
    };
    
    private GenerateLogs() {}
    
    public static void generate(Path outputDirectory, int fileCount, int linesPerFile)
        throws IOException {
        if (fileCount < 0 || linesPerFile < 0) {
            throw new IllegalArgumentException("Counts must be non-negative");
        }
        
        Files.createDirectories(outputDirectory);
        Random random = new Random(42);
        
        for (int fileId = 0; fileId < fileCount; fileId++) {
            Path file = outputDirectory.resolve("access-%03d.log".formatted(fileId));
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                for (int line = 0; line < linesPerFile; line++) {
                    String method = METHODS[random.nextInt(METHODS.length)];
                    String path = PATHS[random.nextInt(PATHS.length)];
                    int status = STATUSES[random.nextInt(STATUSES.length)];
                    long responseBytes = 100L + random.nextInt(100_000);
                    long durationMillis = 1L + random.nextInt(500);
                    writer.write("%s %s %d %d %d%n".formatted(
                        method, path, status, responseBytes, durationMillis));
                }
            }
        }
    }
}
