package ro.accesa.payment.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.accesa.payment.dto.request.InitiateTransactionRequest;
import ro.accesa.payment.dto.response.CancelTransactionResponse;
import ro.accesa.payment.dto.response.InitiateTransactionResponse;
import ro.accesa.payment.dto.response.TransactionStatusResponse;
import ro.accesa.payment.service.TransactionService;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class MerchantTransactionController {

    private final TransactionService transactionService;

    @PostMapping("/initiate")
    public ResponseEntity<InitiateTransactionResponse> initiateTransaction(
            @Valid @RequestBody InitiateTransactionRequest request,
            HttpServletRequest httpRequest) {
        String merchantId = (String) httpRequest.getAttribute("merchantId");
        InitiateTransactionResponse response = transactionService.initiateTransaction(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{transactionId}/status")
    public ResponseEntity<TransactionStatusResponse> getTransactionStatus(
            @PathVariable String transactionId,
            HttpServletRequest httpRequest) {
        String merchantId = (String) httpRequest.getAttribute("merchantId");
        TransactionStatusResponse response = transactionService.getTransactionStatus(transactionId, merchantId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{transactionId}/cancel")
    public ResponseEntity<CancelTransactionResponse> cancelTransaction(
            @PathVariable String transactionId,
            HttpServletRequest httpRequest) {
        String merchantId = (String) httpRequest.getAttribute("merchantId");
        CancelTransactionResponse response = transactionService.cancelTransaction(transactionId, merchantId);
        return ResponseEntity.ok(response);
    }
}
