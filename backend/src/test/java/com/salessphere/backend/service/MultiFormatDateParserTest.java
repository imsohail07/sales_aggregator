package com.salessphere.backend.service;

import com.salessphere.backend.util.MultiFormatDateParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class MultiFormatDateParserTest {

    @Test
    public void testDateOnlyFormats() {
        // yyyy-MM-dd
        LocalDateTime dt1 = MultiFormatDateParser.parse("2026-07-03");
        assertEquals(2026, dt1.getYear());
        assertEquals(7, dt1.getMonthValue());
        assertEquals(3, dt1.getDayOfMonth());
        assertEquals(0, dt1.getHour());

        // yyyy/MM/dd
        LocalDateTime dt2 = MultiFormatDateParser.parse("2026/07/04");
        assertEquals(4, dt2.getDayOfMonth());

        // dd-MM-yyyy
        LocalDateTime dt3 = MultiFormatDateParser.parse("05-07-2026");
        assertEquals(5, dt3.getDayOfMonth());

        // dd/MM/yyyy
        LocalDateTime dt4 = MultiFormatDateParser.parse("06/07/2026");
        assertEquals(6, dt4.getDayOfMonth());

        // MM/dd/yyyy
        LocalDateTime dt5 = MultiFormatDateParser.parse("07/28/2026");
        assertEquals(28, dt5.getDayOfMonth());
        assertEquals(7, dt5.getMonthValue());
    }

    @Test
    public void testDateTimeFormats() {
        // yyyy-MM-dd HH:mm:ss
        LocalDateTime dt1 = MultiFormatDateParser.parse("2026-07-03 14:30:45");
        assertEquals(14, dt1.getHour());
        assertEquals(30, dt1.getMinute());
        assertEquals(45, dt1.getSecond());

        // yyyy/MM/dd HH:mm:ss
        LocalDateTime dt2 = MultiFormatDateParser.parse("2026/07/03 09:15:00");
        assertEquals(9, dt2.getHour());

        // yyyy-MM-dd'T'HH:mm:ss
        LocalDateTime dt3 = MultiFormatDateParser.parse("2026-07-03T18:45:30");
        assertEquals(18, dt3.getHour());

        // yyyy-MM-dd'T'HH:mm:ss.SSS
        LocalDateTime dt4 = MultiFormatDateParser.parse("2026-07-03T23:59:59.999");
        assertEquals(23, dt4.getHour());
        assertEquals(59, dt4.getMinute());
    }

    @Test
    public void testTimezoneAwareFormats() {
        // ISO_OFFSET_DATE_TIME
        LocalDateTime dt1 = MultiFormatDateParser.parse("2026-07-03T10:15:30+01:00");
        assertNotNull(dt1);

        // ISO_INSTANT
        LocalDateTime dt2 = MultiFormatDateParser.parse("2026-07-03T10:15:30Z");
        assertNotNull(dt2);
    }

    @Test
    public void testQuotesAndWhitespace() {
        // Surrounding double quotes
        LocalDateTime dt1 = MultiFormatDateParser.parse("\"2026-07-03 12:00:00\"");
        assertEquals(12, dt1.getHour());

        // Surrounding single quotes and spaces
        LocalDateTime dt2 = MultiFormatDateParser.parse("  '2026-07-03'  ");
        assertEquals(3, dt2.getDayOfMonth());
    }

    @Test
    public void testInvalidDateFormat_ThrowsDetailedMessage() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            MultiFormatDateParser.parse("invalid-date-string");
        });

        String expectedMessage = "Invalid date format. Expected one of: yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, dd-MM-yyyy, ISO-8601";
        assertEquals(expectedMessage, exception.getMessage());
    }
}
