package Homework_5.task1;

import Homework_5.task1.model.LogEntry;

public final class LogParser {
    private LogParser() {}
    
    public static LogEntry parseLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Лог пустой");
        }
        
        String[] fields = line.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("В логе не 5 полей");
        }
        
        String method = fields[0];
        String path = fields[1];
        if (method.isEmpty()) {
            throw new IllegalArgumentException("Пустой HTTP-метод в строке: [" + line + "]");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Пустой путь запроса в строке: [" + line + "]");
        }
        
        int status;
        long responseBytes;
        long durationMillis;
        try {
            status = Integer.parseInt(fields[2]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный статус '" + fields[2] + "' в строке: [" + line + "]", e);
        }
        try {
            responseBytes = Long.parseLong(fields[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный размер '" + fields[3] + "' в строке: [" + line + "]", e);
        }
        try {
            durationMillis = Long.parseLong(fields[4]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректная длительность '" + fields[4] + "' в строке: [" + line + "]", e);
        }
        
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("Статус вне диапазона HTTP [100..599]: " + status);
        }
        if (responseBytes < 0) {
            throw new IllegalArgumentException("Отрицательный размер ответа: " + responseBytes);
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("Отрицательная длительность: " + durationMillis);
        }
        
        return new LogEntry(method, path, status, responseBytes, durationMillis);
    }
}