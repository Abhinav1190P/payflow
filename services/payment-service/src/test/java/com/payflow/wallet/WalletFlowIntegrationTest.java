package com.payflow.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.fraud.FraudAlertRepository;
import com.payflow.fraud.FraudMonitor;
import com.payflow.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class WalletFlowIntegrationTest extends IntegrationTest {

    @Autowired
    private FraudAlertRepository fraudAlerts;

    @Autowired
    private FraudMonitor fraudMonitor;

    private void awaitGraphEdges(int atLeast) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (fraudMonitor.edgeCount() < atLeast && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
    }

    private long balance(Account account) throws Exception {
        var result = mvc.perform(get("/api/v1/wallets/me").header(HttpHeaders.AUTHORIZATION, account.bearer()))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("data").get("balanceMinor").asLong();
    }

    private void topUp(Account account, long amount) throws Exception {
        mvc.perform(post("/api/v1/wallets/me/top-ups").header(HttpHeaders.AUTHORIZATION, account.bearer())
                        .header("Idempotency-Key", newKey()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions transfer(Account from, Account to, long amount, String key) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header(HttpHeaders.AUTHORIZATION, from.bearer())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toEmail\":\"" + to.email() + "\",\"amountMinor\":" + amount + ",\"note\":\"rent\"}"));
    }

    @Test
    void topUpIsIdempotent() throws Exception {
        Account alice = register("alice");
        String key = newKey();
        String body = "{\"amountMinor\":10000}";

        mvc.perform(post("/api/v1/wallets/me/top-ups").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.data.balanceAfterMinor").value(10000));

        mvc.perform(post("/api/v1/wallets/me/top-ups").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.balanceAfterMinor").value(10000));

        assertThat(balance(alice)).isEqualTo(10000);

        mvc.perform(post("/api/v1/wallets/me/top-ups").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"amountMinor\":999}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mvc.perform(post("/api/v1/wallets/me/top-ups").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferMovesMoneyAndRejectsOverdraft() throws Exception {
        Account alice = register("alice");
        Account bob = register("bob");
        topUp(alice, 10000);

        transfer(alice, bob, 2500, newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAfterMinor").value(7500));
        assertThat(balance(alice)).isEqualTo(7500);
        assertThat(balance(bob)).isEqualTo(2500);

        transfer(alice, bob, 999_999, newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balance(alice)).isEqualTo(7500);

        mvc.perform(get("/api/v1/wallets/me/transactions?sort=reference").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/wallets/me/statement").param("from", "2026-12-31").param("to", "2026-01-01")
                        .accept("text/csv").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));

        transfer(alice, alice, 100, newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));

        mvc.perform(get("/api/v1/wallets/me/transactions?size=1").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.data.items[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void statementSupportsJsonAndCsv() throws Exception {
        Account carol = register("carol");
        topUp(carol, 4200);

        mvc.perform(get("/api/v1/wallets/me/statement").header(HttpHeaders.AUTHORIZATION, carol.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].amountMinor").value(4200));

        var csv = mvc.perform(get("/api/v1/wallets/me/statement").header(HttpHeaders.AUTHORIZATION, carol.bearer())
                        .accept("text/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.lines().toList()).hasSize(2);
        assertThat(csv).startsWith("created_at,transaction_id,type");

        mvc.perform(get("/api/v1/wallets/me/balance-at").param("at", "2000-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, carol.bearer()))
                .andExpect(jsonPath("$.data.hadActivity").value(false));

        mvc.perform(get("/api/v1/wallets/me/summary").header(HttpHeaders.AUTHORIZATION, carol.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wallet.balanceMinor").value(4200))
                .andExpect(jsonPath("$.data.recent.length()").value(1))
                .andExpect(jsonPath("$.data.thisMonth[0].creditsMinor").value(4200));
    }

    @Test
    void paymentsWithWalletAndCard() throws Exception {
        Account dave = register("dave");
        topUp(dave, 3000);

        mvc.perform(post("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, dave.bearer()).header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"WALLET\",\"amountMinor\":1000,\"merchantReference\":\"order-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.balanceAfterMinor").value(2000));

        mvc.perform(post("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, dave.bearer()).header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"WALLET\",\"amountMinor\":5000,\"merchantReference\":\"order-2\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.data.declineCode").value("INSUFFICIENT_FUNDS"));

        mvc.perform(post("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, dave.bearer()).header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"amountMinor\":5000,\"merchantReference\":\"order-3\",\"cardNumber\":\"4000000000000002\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.data.declineCode").value("CARD_DECLINED"));

        mvc.perform(post("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, dave.bearer()).header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"CARD\",\"amountMinor\":5000,\"merchantReference\":\"order-4\",\"cardNumber\":\"4000000000000010\"}"))
                .andExpect(status().isBadGateway());

        assertThat(balance(dave)).isEqualTo(2000);
    }

    @Test
    void adminOnlyEndpointsAndOptimisticLockingWithEtag() throws Exception {
        Account user = register("user");
        Account admin = admin();

        mvc.perform(get("/api/v1/admin/stats").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/stats"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/stats").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerSumMinor").value(0));

        var walletJson = body(mvc.perform(get("/api/v1/wallets/me").header(HttpHeaders.AUTHORIZATION, user.bearer())).andReturn());
        String walletId = walletJson.get("data").get("id").asText();

        var getResult = mvc.perform(get("/api/v1/admin/wallets/" + walletId).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();
        String etag = getResult.getResponse().getHeader(HttpHeaders.ETAG);

        mvc.perform(post("/api/v1/admin/wallets/" + walletId + "/freeze").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .header(HttpHeaders.IF_MATCH, "\"9999\""))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(post("/api/v1/admin/wallets/" + walletId + "/freeze").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"));

        mvc.perform(post("/api/v1/admin/wallets/" + walletId + "/freeze").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .header(HttpHeaders.IF_MATCH, etag))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(post("/api/v1/wallets/me/top-ups").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .header("Idempotency-Key", newKey()).contentType(MediaType.APPLICATION_JSON).content("{\"amountMinor\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WALLET_FROZEN"));
    }

    @Test
    void circularTransfersRaiseFraudAlert() throws Exception {
        Account a = register("ring-a");
        Account b = register("ring-b");
        Account c = register("ring-c");
        topUp(a, 1000);
        long before = fraudAlerts.count();

        int edges = fraudMonitor.edgeCount();
        transfer(a, b, 300, newKey()).andExpect(status().isCreated());
        awaitGraphEdges(edges + 1);
        transfer(b, c, 200, newKey()).andExpect(status().isCreated());
        awaitGraphEdges(edges + 2);
        transfer(c, a, 100, newKey()).andExpect(status().isCreated());

        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (fraudAlerts.count() == before && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(fraudAlerts.count()).isGreaterThan(before);
    }

    @Test
    void frameworkErrorsKeepTheirStatus() throws Exception {
        Account visitor = register("visitor");
        mvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, visitor.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/wallets/me").header(HttpHeaders.AUTHORIZATION, visitor.bearer()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void transferToUnknownEmailIs404() throws Exception {
        Account alice = register("alice");
        Account ghost = new Account(java.util.UUID.randomUUID(), "ghost@payflow.dev", "unused");
        topUp(alice, 1000);
        transfer(alice, ghost, 100, newKey())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
