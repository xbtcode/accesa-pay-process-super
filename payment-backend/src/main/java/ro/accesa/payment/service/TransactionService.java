package ro.accesa.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ro.accesa.payment.domain.*;
import ro.accesa.payment.dto.request.ConfirmPaymentRequest;
import ro.accesa.payment.dto.request.InitiateTransactionRequest;
import ro.accesa.payment.dto.response.*;
import ro.accesa.payment.exception.InvalidTransactionStateException;
import ro.accesa.payment.exception.TransactionExpiredException;
import ro.accesa.payment.exception.TransactionNotFoundException;
import ro.accesa.payment.repository.MerchantRepository;
import ro.accesa.payment.repository.PaymentTransactionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class TransactionService {

    private final PaymentTransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRefGenerator refGenerator;
    private final BankIntegrationService bankIntegrationService;
    private final String qrBaseUrl;
    private final int ttlMinutes;

    public TransactionService(
            PaymentTransactionRepository transactionRepository,
            MerchantRepository merchantRepository,
            TransactionRefGenerator refGenerator,
            BankIntegrationService bankIntegrationService,
            @Value("${app.qr.base-url}") String qrBaseUrl,
            @Value("${app.transaction.ttl-minutes}") int ttlMinutes) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.refGenerator = refGenerator;
        this.bankIntegrationService = bankIntegrationService;
        this.qrBaseUrl = qrBaseUrl;
        this.ttlMinutes = ttlMinutes;
    }

    public InitiateTransactionResponse initiateTransaction(String merchantId, InitiateTransactionRequest request) {
        Merchant merchant = merchantRepository.findByMerchantCode(merchantId)
                .orElseThrow(() -> new TransactionNotFoundException("Merchant not found: " + merchantId));

        Instant now = Instant.now();
        String ref = refGenerator.generate();
        String qrPayload = qrBaseUrl + "/" + ref;

        PaymentTransaction txn = PaymentTransaction.builder()
                .transactionRef(ref)
                .merchantId(merchantId)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "EUR")
                .description(request.getDescription())
                .status(TransactionStatus.QR_GENERATED)
                .creditor(CreditorInfo.builder()
                        .name(merchant.getName())
                        .iban(merchant.getIban())
                        .bic(merchant.getBic())
                        .build())
                .qrPayload(qrPayload)
                .initiatedAt(now)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .createdAt(now)
                .updatedAt(now)
                .build();

        txn.addStatusHistory(null, TransactionStatus.INITIATED, "Transaction created by merchant", "MERCHANT");
        txn.addStatusHistory(TransactionStatus.INITIATED, TransactionStatus.QR_GENERATED, "QR code generated", "SYSTEM");

        transactionRepository.save(txn);
        log.info("Transaction initiated: {} for merchant {}", ref, merchantId);

        return InitiateTransactionResponse.builder()
                .transactionId(txn.getId())
                .transactionRef(ref)
                .status(txn.getStatus().name())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .description(txn.getDescription())
                .qrPayload(qrPayload)
                .expiresAt(txn.getExpiresAt())
                .initiatedAt(txn.getInitiatedAt())
                .build();
    }

    public TransactionStatusResponse getTransactionStatus(String transactionId, String merchantId) {
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (!txn.getMerchantId().equals(merchantId)) {
            throw new TransactionNotFoundException("Transaction not found: " + transactionId);
        }

        return TransactionStatusResponse.builder()
                .transactionId(txn.getId())
                .transactionRef(txn.getTransactionRef())
                .status(txn.getStatus().name())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .completedAt(txn.getCompletedAt())
                .bankReference(txn.getBankIntegration() != null ? txn.getBankIntegration().getBankReference() : null)
                .build();
    }

    public CancelTransactionResponse cancelTransaction(String transactionId, String merchantId) {
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        if (!txn.getMerchantId().equals(merchantId)) {
            throw new TransactionNotFoundException("Transaction not found: " + transactionId);
        }

        transitionStatus(txn, TransactionStatus.CANCELLED, "Cancelled by merchant", "MERCHANT");
        txn.setCompletedAt(Instant.now());
        transactionRepository.save(txn);

        return CancelTransactionResponse.builder()
                .transactionId(txn.getId())
                .status(TransactionStatus.CANCELLED.name())
                .cancelledAt(txn.getCompletedAt())
                .build();
    }

    public PaymentDetailsResponse getPaymentDetails(String transactionRef) {
        PaymentTransaction txn = findByRef(transactionRef);
        checkNotExpired(txn);

        if (txn.getStatus() == TransactionStatus.QR_GENERATED) {
            transitionStatus(txn, TransactionStatus.CUSTOMER_OPENED, "Customer accessed payment page", "CUSTOMER");
            transactionRepository.save(txn);
        }

        return PaymentDetailsResponse.builder()
                .transactionRef(txn.getTransactionRef())
                .merchantName(txn.getCreditor().getName())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .description(txn.getDescription())
                .creditorName(txn.getCreditor().getName())
                .status(txn.getStatus().name())
                .expiresAt(txn.getExpiresAt())
                .build();
    }

    public PaymentConfirmResponse confirmPayment(String transactionRef, ConfirmPaymentRequest request) {
        PaymentTransaction txn = findByRef(transactionRef);
        checkNotExpired(txn);

        if (request.getConsentGiven() == null || !request.getConsentGiven()) {
            throw new InvalidTransactionStateException("Consent must be given to proceed with payment");
        }

        // Set debtor info
        txn.setDebtor(DebtorInfo.builder()
                .iban(request.getDebtorIban().replaceAll("\\s+", "").toUpperCase())
                .name(request.getDebtorName())
                .bic(request.getDebtorBic())
                .build());

        transitionStatus(txn, TransactionStatus.CUSTOMER_CONFIRMED, "Customer confirmed payment details", "CUSTOMER");
        txn.setConfirmedAt(Instant.now());

        transitionStatus(txn, TransactionStatus.PROCESSING, "pain.001 submitted to bank", "SYSTEM");
        transactionRepository.save(txn);

        // Call bank
        try {
            BankIntegrationInfo bankInfo = bankIntegrationService.submitPayment(txn);
            txn.setBankIntegration(bankInfo);

            if ("ACCP".equals(bankInfo.getBankResponseCode())) {
                transitionStatus(txn, TransactionStatus.APPROVED, "Bank confirmed SEPA Instant Credit Transfer", "BANK");
                txn.setCompletedAt(Instant.now());
            } else {
                transitionStatus(txn, TransactionStatus.REJECTED, "Bank rejected: " + bankInfo.getBankResponseReason(), "BANK");
                txn.setFailureReason(bankInfo.getBankResponseReason());
                txn.setCompletedAt(Instant.now());
            }
        } catch (Exception e) {
            log.error("Bank communication error for {}: {}", transactionRef, e.getMessage());
            transitionStatus(txn, TransactionStatus.FAILED, "Bank communication error: " + e.getMessage(), "SYSTEM");
            txn.setFailureReason("Bank communication error");
            txn.setCompletedAt(Instant.now());
        }

        transactionRepository.save(txn);

        return PaymentConfirmResponse.builder()
                .transactionRef(txn.getTransactionRef())
                .status(txn.getStatus().name())
                .message(txn.getStatus() == TransactionStatus.APPROVED
                        ? "Payment is being processed via SEPA Instant."
                        : "Payment " + txn.getStatus().name().toLowerCase() + ": " +
                          (txn.getFailureReason() != null ? txn.getFailureReason() : ""))
                .build();
    }

    public PaymentResultResponse getPaymentResult(String transactionRef) {
        PaymentTransaction txn = findByRef(transactionRef);

        String message = switch (txn.getStatus()) {
            case APPROVED -> String.format("Payment of %s %s to %s was successful.",
                    txn.getCurrency(), txn.getAmount(), txn.getCreditor().getName());
            case REJECTED -> "Payment failed: " + (txn.getFailureReason() != null ? txn.getFailureReason() : "Rejected by bank");
            case FAILED -> "Payment failed: " + (txn.getFailureReason() != null ? txn.getFailureReason() : "System error");
            case EXPIRED -> "Payment expired. Please try again.";
            case CANCELLED -> "Payment was cancelled.";
            default -> "Payment is being processed. Please wait.";
        };

        return PaymentResultResponse.builder()
                .transactionRef(txn.getTransactionRef())
                .status(txn.getStatus().name())
                .message(message)
                .completedAt(txn.getCompletedAt())
                .build();
    }

    private PaymentTransaction findByRef(String transactionRef) {
        return transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionRef));
    }

    private void checkNotExpired(PaymentTransaction txn) {
        if (txn.getStatus().isTerminal()) {
            if (txn.getStatus() == TransactionStatus.EXPIRED) {
                throw new TransactionExpiredException("Transaction has expired: " + txn.getTransactionRef());
            }
            throw new InvalidTransactionStateException(
                    "Transaction is in terminal state: " + txn.getStatus());
        }
        if (txn.getExpiresAt() != null && Instant.now().isAfter(txn.getExpiresAt())) {
            transitionStatus(txn, TransactionStatus.EXPIRED, "TTL expired", "SYSTEM");
            transactionRepository.save(txn);
            throw new TransactionExpiredException("Transaction has expired: " + txn.getTransactionRef());
        }
    }

    private void transitionStatus(PaymentTransaction txn, TransactionStatus newStatus, String reason, String actor) {
        TransactionStatus current = txn.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            throw new InvalidTransactionStateException(
                    String.format("Cannot transition from %s to %s", current, newStatus));
        }
        txn.addStatusHistory(current, newStatus, reason, actor);
        txn.setStatus(newStatus);
        txn.setUpdatedAt(Instant.now());
    }
}
