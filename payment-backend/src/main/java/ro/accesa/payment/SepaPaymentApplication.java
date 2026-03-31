package ro.accesa.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SepaPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SepaPaymentApplication.class, args);
    }
}
