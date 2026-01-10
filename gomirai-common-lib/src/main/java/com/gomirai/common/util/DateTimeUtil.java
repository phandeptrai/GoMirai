package com.gomirai.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Date and Time utility functions
 */
public class DateTimeUtil {

    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd";
    public static final String SIMPLE_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter ISO_FORMATTER = 
        DateTimeFormatter.ofPattern(ISO_DATETIME_FORMAT);
    
    private static final DateTimeFormatter SIMPLE_DATE_FORMATTER = 
        DateTimeFormatter.ofPattern(SIMPLE_DATE_FORMAT);
    
    private static final DateTimeFormatter SIMPLE_DATETIME_FORMATTER = 
        DateTimeFormatter.ofPattern(SIMPLE_DATETIME_FORMAT);

    /**
     * Format LocalDateTime to ISO string
     */
    public static String formatISO(LocalDateTime dateTime) {
        return dateTime.format(ISO_FORMATTER);
    }

    /**
     * Format LocalDateTime to simple date string
     */
    public static String formatSimpleDate(LocalDateTime dateTime) {
        return dateTime.format(SIMPLE_DATE_FORMATTER);
    }

    /**
     * Format LocalDateTime to simple datetime string
     */
    public static String formatSimpleDateTime(LocalDateTime dateTime) {
        return dateTime.format(SIMPLE_DATETIME_FORMATTER);
    }

    /**
     * Convert Date to LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    /**
     * Convert LocalDateTime to Date
     */
    public static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Get current LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Check if date is in the past
     */
    public static boolean isPast(LocalDateTime dateTime) {
        return dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * Check if date is in the future
     */
    public static boolean isFuture(LocalDateTime dateTime) {
        return dateTime.isAfter(LocalDateTime.now());
    }
}


