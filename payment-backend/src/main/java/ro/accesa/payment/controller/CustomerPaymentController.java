package ro.accesa.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.accesa.payment.dto.request.ConfirmPaymentRequest;
import ro.accesa.payment.dto.response.PaymentConfirmResponse;
import ro.accesa.payment.dto.response.PaymentDetailsResponse;
import ro.accesa.payment.dto.response.PaymentResultResponse;
import ro.accesa.payment.service.TransactionService;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final TransactionService transactionService;

    @GetMapping("/{transactionRef}")
    public ResponseEntity<PaymentDetailsResponse> getPaymentDetails(
            @PathVariable String transactionRef) {
        PaymentDetailsResponse response = transactionService.getPaymentDetails(transactionRef);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{transactionRef}/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirmPayment(
            @PathVariable String transactionRef,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        PaymentConfirmResponse response = transactionService.confirmPayment(transactionRef, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{transactionRef}/result")
    public ResponseEntity<PaymentResultResponse> getPaymentResult(
            @PathVariable String transactionRef) {
        PaymentResultResponse response = transactionService.getPaymentResult(transactionRef);
        return ResponseEntity.ok(response);
    }
}
