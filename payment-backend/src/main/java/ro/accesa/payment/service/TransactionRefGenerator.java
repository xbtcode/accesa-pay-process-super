package ro.accesa.payment.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class TransactionRefGenerator {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 6;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        String datePart = LocalDate.now().format(DATE_FMT);
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return "TXN-" + datePart + "-" + suffix;
    }
}
