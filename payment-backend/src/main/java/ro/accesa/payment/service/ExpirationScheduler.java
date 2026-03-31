package ro.accesa.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.accesa.payment.domain.PaymentTransaction;
import ro.accesa.payment.domain.TransactionStatus;
import ro.accesa.payment.repository.PaymentTransactionRepository;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpirationScheduler {

    private final PaymentTransactionRepository repository;

    @Scheduled(fixedRate = 10000)
    public void expireStaleTransactions() {
        List<PaymentTransaction> stale = repository.findByStatusInAndExpiresAtBefore(
                List.of(
                        TransactionStatus.INITIATED,
                        TransactionStatus.QR_GENERATED,
                        TransactionStatus.CUSTOMER_OPENED,
                        TransactionStatus.CUSTOMER_CONFIRMED
                ),
                Instant.now()
        );

        for (PaymentTransaction txn : stale) {
            TransactionStatus previous = txn.getStatus();
            txn.setStatus(TransactionStatus.EXPIRED);
            txn.addStatusHistory(previous, TransactionStatus.EXPIRED, "TTL expired", "SYSTEM");
            txn.setUpdatedAt(Instant.now());
            txn.setCompletedAt(Instant.now());
            repository.save(txn);
            log.info("Expired transaction: {}", txn.getTransactionRef());
        }
    }
}
