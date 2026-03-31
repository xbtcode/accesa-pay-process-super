package ro.accesa.payment.bankmock.service;

import org.springframework.stereotype.Service;
import ro.accesa.payment.bankmock.dto.BankResponse;
import ro.accesa.payment.bankmock.dto.Pain001Request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SepaInstantSimulatorService {

    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("15000");

    public BankResponse processPayment(Pain001Request request) {
        // Simulate network/processing delay of 200-800ms
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(200, 801));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BankResponse response = new BankResponse();
        response.setMessageId(request.getMessageId());

        String debtorIban = request.getDebtor().getIban();

        if (debtorIban != null && debtorIban.endsWith("9999")) {
            response.setStatus("RJCT");
            response.setStatusReason("INSUFFICIENT_FUNDS");
        } else if (debtorIban != null && debtorIban.endsWith("8888")) {
            response.setStatus("RJCT");
            response.setStatusReason("ACCOUNT_BLOCKED");
        } else if (request.getAmount() != null
                && request.getAmount().getValue() != null
                && request.getAmount().getValue().compareTo(AMOUNT_LIMIT) > 0) {
            response.setStatus("RJCT");
            response.setStatusReason("AMOUNT_EXCEEDS_LIMIT");
        } else {
            response.setStatus("ACCP");
            response.setBankReference(UUID.randomUUID().toString());
            response.setExecutionTimestamp(Instant.now().toString());
        }

        return response;
    }
}
