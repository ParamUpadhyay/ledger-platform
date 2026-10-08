package dev.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.account.api.AccountResponse;
import dev.ledger.account.api.OpenAccountRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the full app against a real PostgreSQL in Docker, with the real Flyway migrations. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AccountApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    TestRestTemplate http;

    @Test
    void opensAndFetchesAnAccount() {
        ResponseEntity<AccountResponse> created =
                http.postForEntity("/accounts", new OpenAccountRequest("Ada Lovelace", "usd"), AccountResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.currency()).isEqualTo("USD");
        assertThat(created.getHeaders().getLocation()).hasPath("/accounts/" + body.id());

        AccountResponse fetched = http.getForObject("/accounts/" + body.id(), AccountResponse.class);
        assertThat(fetched.ownerName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void rejectsInvalidCurrencyWithProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                http.postForEntity("/accounts", new OpenAccountRequest("Ada", "dollars"), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returns404ForUnknownAccount() {
        ResponseEntity<ProblemDetail> response =
                http.getForEntity("/accounts/" + UUID.randomUUID(), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
