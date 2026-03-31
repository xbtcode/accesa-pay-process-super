package ro.accesa.payment.bankmock.dto;

import lombok.Data;

@Data
public class BankResponse {

    private String messageId;
    private String bankReference;
    private String status;
    private String statusReason;
    private String executionTimestamp;
}
