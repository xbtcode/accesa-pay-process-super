package ro.accesa.payment.bankmock.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Pain001Request {

    private String messageId;
    private String creationDateTime;
    private PartyInfo debtor;
    private PartyInfo creditor;
    private Amount amount;
    private String remittanceInformation;

    @Data
    public static class PartyInfo {
        private String name;
        private String iban;
        private String bic;
    }

    @Data
    public static class Amount {
        private BigDecimal value;
        private String currency;
    }
}
