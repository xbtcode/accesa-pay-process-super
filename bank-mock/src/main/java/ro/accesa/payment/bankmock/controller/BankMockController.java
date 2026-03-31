package ro.accesa.payment.bankmock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.accesa.payment.bankmock.dto.BankResponse;
import ro.accesa.payment.bankmock.dto.Pain001Request;
import ro.accesa.payment.bankmock.service.SepaInstantSimulatorService;

@RestController
@RequestMapping("/bank-mock/sepa-instant")
public class BankMockController {

    private final SepaInstantSimulatorService simulatorService;

    public BankMockController(SepaInstantSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<BankResponse> initiatePayment(@RequestBody Pain001Request request) {
        BankResponse response = simulatorService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}
