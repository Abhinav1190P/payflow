package com.payflow.wallet.dto;

import java.time.LocalDate;

public record ReportRow(LocalDate bucketStart, long creditsMinor, long debitsMinor, long netMinor, long entries) {
}
