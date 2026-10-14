package com.payflow.wallet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public final class StatementCsvWriter {

    private StatementCsvWriter() {
    }

    // Concept: try-with-resources. The PrintWriter is closed automatically when the block ends, even if an
    // exception is thrown, because it implements AutoCloseable.
    public static String write(List<EntryView> lines) {
        var buffer = new StringWriter();
        try (var out = new PrintWriter(buffer)) {
            out.println("created_at,transaction_id,type,direction,amount_minor,balance_after_minor,reference");
            for (EntryView line : lines) {
                out.printf("%s,%s,%s,%s,%d,%d,%s%n", line.createdAt(), line.transactionId(), line.type(), line.direction(),
                        line.amountMinor(), line.balanceAfterMinor(), escape(line.reference()));
            }
        }
        return buffer.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")
                ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
