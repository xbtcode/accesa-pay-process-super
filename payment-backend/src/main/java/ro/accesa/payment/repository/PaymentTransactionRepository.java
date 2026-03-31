package ro.accesa.payment.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ro.accesa.payment.domain.PaymentTransaction;
import ro.accesa.payment.domain.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);

    List<PaymentTransaction> findByStatusInAndExpiresAtBefore(List<TransactionStatus> statuses, Instant cutoff);
}
