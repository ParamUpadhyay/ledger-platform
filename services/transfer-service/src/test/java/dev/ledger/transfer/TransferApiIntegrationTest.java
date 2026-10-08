package dev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.ledger.transfer.api.BalanceResponse;
import dev.ledger.transfer.api.TransferResponse;
import dev.ledger.transfer.client.AccountClient;
import dev.ledger.transfer.client.AccountClient.AccountView;
import dev.ledger.transfer.domain.LedgerEntry;
import dev.ledger.transfer.domain.LedgerEntryRepository;
import dev.ledger.transfer.domain.OutboxRepository;
import dev.ledger.transfer.domain.TransferRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the full service against a real PostgreSQL; account-service is replaced by a mock. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TransferApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    TestRestTemplate http;

    @Autowired
    TransferRepository transfers;

    @Autowired
    LedgerEntryRepository entries;

    @Autowired
    OutboxRepository outbox;

    @MockitoBean
    AccountClient accountClient;

    UUID alice;
    UUID bob;

    @BeforeEach
    void accountsExistInUsd() {
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        when(accountClient.find(any())).thenAnswer(inv -> Optional.of(new AccountView(inv.getArgument(0), "USD")));
    }

    @Test
    void depositThenTransferMovesMoneyAndWritesBalancedLedgerAndOutbox() {
        deposit(alice, "100.00", key());

        ResponseEntity<TransferResponse> response = transfer(alice, bob, "30.25", key(), TransferResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balanceOf(alice)).isEqualByComparingTo("69.75");
        assertThat(balanceOf(bob)).isEqualByComparingTo("30.25");

        UUID transferId = response.getBody().id();
        List<LedgerEntry> legs = entries.findByTransferId(transferId);
        assertThat(legs).hasSize(2);
        assertThat(sum(legs, LedgerEntry.Direction.DEBIT)).isEqualByComparingTo(sum(legs, LedgerEntry.Direction.CREDIT));

        assertThat(outbox.findByAggregateId(transferId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("transfer.completed");
                    assertThat(event.getPayload()).contains(transferId.toString(), "30.2500");
                    assertThat(event.getPublishedAt()).isNull();
                });
    }

    @Test
    void retryWithSameKeyReturnsOriginalTransferAndMovesMoneyOnce() {
        deposit(alice, "50", key());
        String key = key();

        ResponseEntity<TransferResponse> first = transfer(alice, bob, "20", key, TransferResponse.class);
        ResponseEntity<TransferResponse> retry = transfer(alice, bob, "20", key, TransferResponse.class);

        assertThat(retry.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(first.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        assertThat(retry.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(balanceOf(alice)).isEqualByComparingTo("30");
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        deposit(alice, "50", key());
        String key = key();
        transfer(alice, bob, "20", key, TransferResponse.class);

        ResponseEntity<ProblemDetail> reused = transfer(alice, bob, "25", key, ProblemDetail.class);

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(reused.getBody().getProperties()).containsEntry("reason", "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void insufficientFundsIsRejectedAndWritesNothing() {
        deposit(alice, "10", key());
        long transfersBefore = transfers.count();

        ResponseEntity<ProblemDetail> response = transfer(alice, bob, "10.0001", key(), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().getProperties()).containsEntry("reason", "INSUFFICIENT_FUNDS");
        assertThat(transfers.count()).isEqualTo(transfersBefore);
        assertThat(balanceOf(alice)).isEqualByComparingTo("10");
    }

    @Test
    void currencyMustMatchBothAccounts() {
        when(accountClient.find(bob)).thenReturn(Optional.of(new AccountView(bob, "EUR")));
        deposit(alice, "10", key());

        ResponseEntity<ProblemDetail> response = transfer(alice, bob, "5", key(), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().getProperties()).containsEntry("reason", "CURRENCY_MISMATCH");
    }

    @Test
    void missingIdempotencyKeyIsABadRequest() {
        ResponseEntity<ProblemDetail> response = http.postForEntity("/transfers",
                Map.of("sourceAccountId", alice, "destinationAccountId", bob, "amount", "1", "currency", "USD"),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void concurrentTransfersNeverOverdraw() throws Exception {
        deposit(alice, "100", key());
        int attempts = 20;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Callable<HttpStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tasks.add(() -> HttpStatus.valueOf(
                    transfer(alice, bob, "10", key(), String.class).getStatusCode().value()));
        }
        List<HttpStatus> outcomes = new ArrayList<>();
        for (Future<HttpStatus> f : pool.invokeAll(tasks)) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(s -> s == HttpStatus.CREATED).hasSize(10);
        assertThat(outcomes).filteredOn(s -> s == HttpStatus.UNPROCESSABLE_CONTENT).hasSize(10);
        assertThat(balanceOf(alice)).isEqualByComparingTo("0");
        assertThat(balanceOf(bob)).isEqualByComparingTo("100");
    }

    private void deposit(UUID account, String amount, String key) {
        ResponseEntity<TransferResponse> response = http.postForEntity("/deposits",
                new HttpEntity<>(Map.of("accountId", account, "amount", amount, "currency", "USD"), headers(key)),
                TransferResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private <T> ResponseEntity<T> transfer(UUID from, UUID to, String amount, String key, Class<T> type) {
        return http.postForEntity("/transfers",
                new HttpEntity<>(Map.of("sourceAccountId", from, "destinationAccountId", to, "amount", amount,
                        "currency", "USD"), headers(key)),
                type);
    }

    private BigDecimal balanceOf(UUID account) {
        return http.getForObject("/accounts/" + account + "/balance", BalanceResponse.class).amount();
    }

    private static BigDecimal sum(List<LedgerEntry> legs, LedgerEntry.Direction direction) {
        return legs.stream().filter(l -> l.getDirection() == direction)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
