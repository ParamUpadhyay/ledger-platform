package dev.ledger.transfer.api;

import dev.ledger.transfer.domain.Accounts;
import dev.ledger.transfer.domain.TransferType;
import dev.ledger.transfer.service.TransferCommand;
import dev.ledger.transfer.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TransferController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/deposits")
    public ResponseEntity<TransferResponse> deposit(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return respond(new TransferCommand(idempotencyKey, TransferType.DEPOSIT, Accounts.EXTERNAL_CASH,
                request.accountId(), request.amount(), request.currency()));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return respond(new TransferCommand(idempotencyKey, TransferType.TRANSFER, request.sourceAccountId(),
                request.destinationAccountId(), request.amount(), request.currency()));
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return transferService.find(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer " + id + " not found"));
    }

    @GetMapping("/accounts/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        return transferService.balance(id)
                .map(b -> new BalanceResponse(b.getAccountId(), b.getAmount(), b.getCurrency()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No ledger activity for account " + id));
    }

    private ResponseEntity<TransferResponse> respond(TransferCommand command) {
        TransferService.Result result = transferService.submit(command);
        return ResponseEntity.created(URI.create("/transfers/" + result.transfer().getId()))
                .header(REPLAYED, Boolean.toString(result.replayed()))
                .body(TransferResponse.from(result.transfer()));
    }
}
