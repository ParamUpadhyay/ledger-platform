package dev.ledger.transfer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads accounts from account-service, which owns account identity and currency. */
@Component
public class AccountClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountView(UUID id, String currency) {
    }

    private final RestClient http;

    public AccountClient(RestClient.Builder builder, @Value("${ledger.account-service.url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public Optional<AccountView> find(UUID accountId) {
        return http.get()
                .uri("/accounts/{id}", accountId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return Optional.empty();
                    }
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException(
                                "account-service returned " + response.getStatusCode() + " for " + accountId);
                    }
                    return Optional.ofNullable(response.bodyTo(AccountView.class));
                });
    }
}
