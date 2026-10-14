package com.payflow.wallet;

import com.payflow.common.security.AuthenticatedUser;
import com.payflow.common.api.ApiResponse;
import com.payflow.common.api.PageResponse;
import com.payflow.common.error.BusinessRuleException;
import com.payflow.idempotency.Replayable;
import com.payflow.wallet.dto.BalanceAtResponse;
import com.payflow.wallet.dto.ReportRow;
import com.payflow.wallet.dto.TopUpRequest;
import com.payflow.wallet.dto.TopUpResponse;
import com.payflow.wallet.dto.TransactionView;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.dto.WalletSummary;
import com.payflow.wallet.report.ReportBucket;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Concept: Spring MVC + REST. Resources are nouns under a versioned path (/api/v1/wallets/me/...),
// HTTP verbs carry the action (GET reads, POST creates a top-up), and status codes carry the result.
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    public static final String CSV = "text/csv";

    private final WalletService walletService;
    private final WalletQueryService queries;
    private final WalletSummaryService summaries;
    private final Clock clock;

    public WalletController(WalletService walletService, WalletQueryService queries, WalletSummaryService summaries, Clock clock) {
        this.walletService = walletService;
        this.queries = queries;
        this.summaries = summaries;
        this.clock = clock;
    }

    @GetMapping("/me")
    public ApiResponse<WalletResponse> myWallet(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.of(queries.myWallet(user.id()));
    }

    @GetMapping("/me/summary")
    public ApiResponse<WalletSummary> summary(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.of(summaries.summary(user.id()));
    }

    @PostMapping("/me/top-ups")
    public ResponseEntity<ApiResponse<TopUpResponse>> topUp(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                            @Valid @RequestBody TopUpRequest request) {
        return respond(walletService.topUp(user.id(), idempotencyKey, request));
    }

    @GetMapping("/me/transactions")
    public ApiResponse<PageResponse<TransactionView>> transactions(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(queries.transactions(user.id(), pageable));
    }

    // Concept: content negotiation. Same URL, two representations: Accept: text/csv returns a CSV file,
    // anything else (including */*) returns the JSON envelope.
    @GetMapping(value = "/me/statement", produces = {MediaType.APPLICATION_JSON_VALUE, CSV})
    public ResponseEntity<?> statement(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Range range = range(from, to);
        List<EntryView> lines = queries.statement(user.id(), range.from(), range.to());
        if (wantsCsv(accept)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(CSV))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement.csv")
                    .body(StatementCsvWriter.write(lines));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(ApiResponse.of(lines));
    }

    // Picks CSV only when the client prefers it: the highest quality value (q=) for text/csv must beat JSON's.
    static boolean wantsCsv(String accept) {
        if (accept == null || accept.isBlank()) {
            return false;
        }
        List<MediaType> accepted = MediaType.parseMediaTypes(accept);
        return quality(accepted, MediaType.parseMediaType(CSV)) > quality(accepted, MediaType.APPLICATION_JSON);
    }

    private static double quality(List<MediaType> accepted, MediaType type) {
        return accepted.stream().filter(m -> m.includes(type)).mapToDouble(MediaType::getQualityValue).max().orElse(0);
    }

    @GetMapping("/me/balance-at")
    public ApiResponse<BalanceAtResponse> balanceAt(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ApiResponse.of(queries.balanceAt(user.id(), at));
    }

    @GetMapping("/me/report")
    public ApiResponse<List<ReportRow>> report(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "DAY") ReportBucket bucket) {
        Range range = range(from, to);
        return ApiResponse.of(queries.report(user.id(), range.from(), range.to(), bucket));
    }

    static <T> ResponseEntity<ApiResponse<T>> respond(Replayable<T> result) {
        return ResponseEntity.status(result.status())
                .header(REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(ApiResponse.of(result.body()));
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("INVALID_RANGE", "from must be on or before to");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw new BusinessRuleException("RANGE_TOO_LARGE", "The range can be at most 366 days");
        }
        return new Range(start.atStartOfDay().toInstant(ZoneOffset.UTC), end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private record Range(Instant from, Instant to) {
    }
}
