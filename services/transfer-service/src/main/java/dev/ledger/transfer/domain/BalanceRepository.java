package dev.ledger.transfer.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BalanceRepository extends JpaRepository<Balance, UUID> {

    /** Creates a zero balance row if none exists. Safe when two requests race to create it. */
    @Modifying
    @Query(value = """
            INSERT INTO balances (account_id, currency, amount, updated_at)
            VALUES (:accountId, :currency, 0, now())
            ON CONFLICT (account_id) DO NOTHING
            """, nativeQuery = true)
    void createIfMissing(UUID accountId, String currency);

    /** SELECT ... FOR UPDATE: blocks other writers to this account until our transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.accountId = :accountId")
    Optional<Balance> lockById(UUID accountId);
}
