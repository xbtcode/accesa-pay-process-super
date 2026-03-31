package ro.accesa.payment.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import ro.accesa.payment.domain.Merchant;

import java.util.Optional;

public interface MerchantRepository extends MongoRepository<Merchant, String> {

    Optional<Merchant> findByApiKeyHash(String apiKeyHash);

    Optional<Merchant> findByMerchantCode(String merchantCode);
}
