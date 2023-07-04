package nl.ing.lovebird.clientproxy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class TestConfiguration {
    private final static LocalDate LOCAL_DATE = LocalDate.of(2020, 4, 13);
    public static final Clock FIXED_CLOCK = Clock.fixed(LOCAL_DATE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
}
