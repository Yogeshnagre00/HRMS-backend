package com.example.HRMS.csvimport.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180-style CSV parser (comma-delimited, UTF-8, double-quote
 * quoting with {@code ""} escaping; quoted fields may contain commas and
 * newlines). Deliberately small and dependency-free — the CSV need here is a
 * single well-defined employee template, so a full CSV framework is not
 * warranted (AGENTS §16).
 *
 * <p>The parser does not interpret the header or values. A record that is a
 * single empty field (a genuinely blank physical line) is dropped so a trailing
 * newline does not create a spurious empty row; all other records are preserved.
 */
public final class CsvParser {

    private CsvParser() {
    }

    /** Parse CSV text into a list of records, each a list of field values. */
    public static List<List<String>> parse(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = content.length();

        while (i < n) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < n && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i += 2;
                } else if (c == '"') {
                    inQuotes = false;
                    i++;
                } else {
                    field.append(c);
                    i++;
                }
            } else if (c == '"') {
                inQuotes = true;
                i++;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
                i++;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < n && content.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                addRecord(records, current);
                current = new ArrayList<>();
                i++;
            } else {
                field.append(c);
                i++;
            }
        }
        // Trailing field/record when the input does not end with a newline.
        if (!field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString());
            addRecord(records, current);
        }
        return records;
    }

    private static void addRecord(List<List<String>> records, List<String> record) {
        // Drop a genuinely blank physical line (a single empty field).
        if (record.size() == 1 && record.get(0).isEmpty()) {
            return;
        }
        records.add(record);
    }
}
