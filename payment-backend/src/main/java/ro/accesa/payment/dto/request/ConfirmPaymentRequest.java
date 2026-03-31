package ro.accesa.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @NotBlank(message = "Debtor IBAN is required")
    private String debtorIban;

    private String debtorName;

    private String debtorBic;

    @NotNull(message = "Consent must be given")
    private Boolean consentGiven;
}
