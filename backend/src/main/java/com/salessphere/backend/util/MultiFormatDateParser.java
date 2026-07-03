package com.salessphere.backend.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class MultiFormatDateParser {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = new ArrayList<>();
    private static final List<DateTimeFormatter> DATE_ONLY_FORMATTERS = new ArrayList<>();

    static {
        // Timezone/Offset/Instant formatters
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ISO_INSTANT);

        // Standard DateTime formatters
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        DATE_TIME_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Date-only formatters
        DATE_ONLY_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DATE_ONLY_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        DATE_ONLY_FORMATTERS.add(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        DATE_ONLY_FORMATTERS.add(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        DATE_ONLY_FORMATTERS.add(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        DATE_ONLY_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static LocalDateTime parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Date string must not be null");
        }

        // Trim whitespace and ignore surrounding quotes
        String clean = input.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1).trim();
        } else if (clean.startsWith("'") && clean.endsWith("'")) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }

        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Date string must not be empty");
        }

        // Try DateTime formatters first (Offset, Zoned, Instant, LocalDateTime)
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_INSTANT) {
                    Instant instant = Instant.parse(clean);
                    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                }

                try {
                    // Try parsing as ZonedDateTime
                    ZonedDateTime zdt = ZonedDateTime.parse(clean, formatter);
                    return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    try {
                        // Try parsing as OffsetDateTime
                        OffsetDateTime odt = OffsetDateTime.parse(clean, formatter);
                        return odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                    } catch (DateTimeParseException ignored2) {
                        // Fall back to standard LocalDateTime
                        return LocalDateTime.parse(clean, formatter);
                    }
                }
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }

        // Try Date-only formatters (align to start of day)
        for (DateTimeFormatter formatter : DATE_ONLY_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(clean, formatter);
                return date.atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }

        // Fallback error messaging
        throw new IllegalArgumentException("Invalid date format. Expected one of: yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, dd-MM-yyyy, ISO-8601");
    }
}
