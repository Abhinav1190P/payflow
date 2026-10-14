package com.payflow.wallet.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public enum ReportBucket {
    DAY {
        @Override
        public LocalDate startOf(Instant instant, ZoneId zone) {
            return LocalDate.ofInstant(instant, zone);
        }
    },
    MONTH {
        @Override
        public LocalDate startOf(Instant instant, ZoneId zone) {
            return LocalDate.ofInstant(instant, zone).withDayOfMonth(1);
        }
    };

    public abstract LocalDate startOf(Instant instant, ZoneId zone);
}
