package ro.accesa.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ro.accesa.payment.domain.BankIntegrationInfo;
import ro.accesa.payment.domain.PaymentTransaction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class BankIntegrationService {

    private final RestClient restClient;

    public BankIntegrationService(@Value("${app.bank-mock.base-url}") String bankMockBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(bankMockBaseUrl)
                .build();
    }

    public BankIntegrationInfo submitPayment(PaymentTransaction txn) {
        String painMessageId = "MSG-" + txn.getTransactionRef() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> request = Map.of(
                "messageId", painMessageId,
                "creationDateTime", Instant.now().toString(),
                "debtor", Map.of(
                        "name", txn.getDebtor().getName() != null ? txn.getDebtor().getName() : "Unknown",
                        "iban", txn.getDebtor().getIban(),
                        "bic", txn.getDebtor().getBic() != null ? txn.getDebtor().getBic() : ""
                ),
                "creditor", Map.of(
                        "name", txn.getCreditor().getName(),
                        "iban", txn.getCreditor().getIban(),
                        "bic", txn.getCreditor().getBic()
                ),
                "amount", Map.of(
                        "value", txn.getAmount(),
                        "currency", txn.getCurrency()
                ),
                "remittanceInformation", txn.getDescription()
        );

        Instant submittedAt = Instant.now();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/bank-mock/sepa-instant/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        Instant respondedAt = Instant.now();

        log.info("Bank response for {}: {}", txn.getTransactionRef(), response);

        return BankIntegrationInfo.builder()
                .painMessageId(painMessageId)
                .bankReference(response != null ? (String) response.get("bankReference") : null)
                .bankResponseCode(response != null ? (String) response.get("status") : "UNKNOWN")
                .bankResponseReason(response != null ? (String) response.get("statusReason") : null)
                .submittedAt(submittedAt)
                .respondedAt(respondedAt)
                .build();
    }
}
