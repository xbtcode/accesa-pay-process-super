package ro.accesa.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("merchants")
public class Merchant {

    @Id
    private String id;

    @Indexed(unique = true)
    private String merchantCode;

    private String name;
    private String iban;
    private String bic;
    private Address address;

    @Indexed(unique = true)
    private String apiKeyHash;

    private boolean active;
    private Instant createdAt;
}
