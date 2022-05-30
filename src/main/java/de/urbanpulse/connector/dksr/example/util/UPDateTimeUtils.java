package de.urbanpulse.connector.dksr.example.util;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides timestamps with proper format for UrbanPulse.
 *
 * @author <a href="mailto:david.krueger@the-urban-institute.de">David Krüger</a>
 */
public final class UPDateTimeUtils {
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
    public static final DateTimeFormatter DATE_TIME_FORMATTER;

    static {
        DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(UPDateTimeUtils.DATE_TIME_PATTERN)
                .withZone(ZoneId.of("Z"));
    }

    private UPDateTimeUtils() {}

    public static String getUpTimestampNow() {
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.format(UPDateTimeUtils.DATE_TIME_FORMATTER);
    }
}
