# TDD Implementation Plan

**Date:** 2026-03-31
**Based on:** `memory/research/2026-03-31-tdd-principles-introduction.md`, `memory/architecture/2026-03-31-tdd-testability-architecture.md`

---

## Phase 1: Backend Test Infrastructure

**Goal:** Create directory structures, config files, and add dependencies so that `mvn test` runs (with zero tests) in both `payment-backend` and `bank-mock`.

### Tasks

- [ ] **1.1** Create test directory tree for `payment-backend`:
  ```
  payment-backend/src/test/java/ro/accesa/payment/domain/
  payment-backend/src/test/java/ro/accesa/payment/service/
  payment-backend/src/test/java/ro/accesa/payment/controller/
  payment-backend/src/test/java/ro/accesa/payment/security/
  payment-backend/src/test/resources/
  ```
  **Verify:** directories exist.

- [ ] **1.2** Create `payment-backend/src/test/resources/application-test.yml`:
  ```yaml
  spring:
    data:
      mongodb:
        uri: mongodb://localhost:27017/sepa_payments_test
        auto-index-creation: true

  app:
    transaction:
      ttl-minutes: 5
      ref-prefix: "TXN"
    qr:
      base-url: http://localhost:5174/pay
    bank-mock:
      base-url: http://localhost:8081
  ```
  **Verify:** file exists with correct content.

- [ ] **1.3** Add Testcontainers BOM and dependencies to `payment-backend/pom.xml`. Insert a `<dependencyManagement>` section BEFORE the existing `<dependencies>` section:
  ```xml
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>org.testcontainers</groupId>
              <artifactId>testcontainers-bom</artifactId>
              <version>1.19.7</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
  </dependencyManagement>
  ```
  And add these two test dependencies inside the existing `<dependencies>` block:
  ```xml
  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mongodb</artifactId>
      <scope>test</scope>
  </dependency>
  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
  </dependency>
  ```
  **Verify:** `cd payment-backend && mvn dependency:resolve -q` succeeds.

- [ ] **1.4** Add JaCoCo plugin to `payment-backend/pom.xml` inside `<build><plugins>`, after the existing `spring-boot-maven-plugin`:
  ```xml
  <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.12</version>
      <executions>
          <execution>
              <goals><goal>prepare-agent</goal></goals>
          </execution>
          <execution>
              <id>report</id>
              <phase>verify</phase>
              <goals><goal>report</goal></goals>
          </execution>
      </executions>
  </plugin>
  ```
  **Verify:** `cd payment-backend && mvn clean package -q` succeeds.

- [ ] **1.5** Create `payment-backend/src/test/java/ro/accesa/payment/TestFixtures.java`:
  ```java
  package ro.accesa.payment;

  import ro.accesa.payment.domain.*;

  import java.math.BigDecimal;
  import java.time.Clock;
  import java.time.Instant;
  import java.time.ZoneOffset;
  import java.time.temporal.ChronoUnit;
  import java.util.ArrayList;

  public final class TestFixtures {

      public static final Instant FIXED_INSTANT = Instant.parse("2026-03-31T10:00:00Z");
      public static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

      private TestFixtures() {}

      public static Merchant.MerchantBuilder aMerchant() {
          return Merchant.builder()
              .id("merchant-1")
              .merchantCode("LIDL001")
              .name("Lidl")
              .iban("DE89370400440532013000")
              .bic("COBADEFF")
              .apiKeyHash("sk_test_lidl_001")
              .active(true)
              .createdAt(FIXED_INSTANT);
      }

      public static PaymentTransaction.PaymentTransactionBuilder aTransaction() {
          return PaymentTransaction.builder()
              .id("txn-1")
              .transactionRef("TXN-20260331-ABC123")
              .merchantId("LIDL001")
              .amount(new BigDecimal("25.50"))
              .currency("EUR")
              .description("Groceries")
              .status(TransactionStatus.QR_GENERATED)
              .creditor(CreditorInfo.builder()
                  .name("Lidl").iban("DE89370400440532013000").bic("COBADEFF").build())
              .qrPayload("http://localhost:5174/pay/TXN-20260331-ABC123")
              .initiatedAt(FIXED_INSTANT)
              .expiresAt(FIXED_INSTANT.plus(5, ChronoUnit.MINUTES))
              .createdAt(FIXED_INSTANT)
              .updatedAt(FIXED_INSTANT)
              .statusHistory(new ArrayList<>());
      }

      public static PaymentTransaction.PaymentTransactionBuilder aTransactionWithDebtor() {
          return aTransaction()
              .status(TransactionStatus.CUSTOMER_OPENED)
              .debtor(DebtorInfo.builder()
                  .iban("RO49AAAA1B31007593840000")
                  .name("John Doe")
                  .build());
      }
  }
  ```
  **Verify:** `cd payment-backend && mvn compile -q -f pom.xml` succeeds (test compilation check comes later with actual tests).

- [ ] **1.6** Create test directory tree for `bank-mock`:
  ```
  bank-mock/src/test/java/ro/accesa/payment/bankmock/service/
  bank-mock/src/test/java/ro/accesa/payment/bankmock/controller/
  bank-mock/src/test/resources/
  ```
  **Verify:** directories exist.

- [ ] **1.7** Create `bank-mock/src/test/resources/application-test.yml`:
  ```yaml
  bank-mock:
    simulate-delay-min-ms: 0
    simulate-delay-max-ms: 0
  ```
  **Verify:** file exists with correct content.

### Phase 1 Verification
Run `cd payment-backend && mvn clean package -q` and `cd bank-mock && mvn clean package -q`. Both must succeed with 0 test failures (and 0 tests).

---

## Phase 2: Testability Refactorings

**Goal:** Inject `Clock`, fix bank-mock delay config, and make `BankIntegrationService` testable. All changes are to production code. No tests yet.

### Tasks

- [ ] **2.1** Create `payment-backend/src/main/java/ro/accesa/payment/config/ClockConfig.java`:
  ```java
  package ro.accesa.payment.config;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import java.time.Clock;

  @Configuration
  public class ClockConfig {
      @Bean
      public Clock clock() {
          return Clock.systemUTC();
      }
  }
  ```
  **Verify:** file exists, compiles.

- [ ] **2.2** Modify `payment-backend/src/main/java/ro/accesa/payment/domain/PaymentTransaction.java` -- change `addStatusHistory` to accept an `Instant timestamp` parameter instead of calling `Instant.now()` internally. New signature:
  ```java
  public void addStatusHistory(TransactionStatus from, TransactionStatus to,
                               String reason, String actor, Instant timestamp) {
      if (statusHistory == null) {
          statusHistory = new ArrayList<>();
      }
      statusHistory.add(StatusHistoryEntry.builder()
              .fromStatus(from)
              .toStatus(to)
              .reason(reason)
              .actor(actor)
              .timestamp(timestamp)
              .build());
  }
  ```
  **Important:** This changes the method signature. All callers must be updated in subsequent tasks.
  **Verify:** file saved; compilation will break until callers are updated (expected).

- [ ] **2.3** Modify `payment-backend/src/main/java/ro/accesa/payment/service/TransactionService.java`:
  - Add `private final Clock clock;` field.
  - Add `Clock clock` as constructor parameter (between `bankIntegrationService` and `@Value("${app.qr.base-url}")` params).
  - Replace every `Instant.now()` with `Instant.now(clock)` (lines 50, 120, 167, 179, 183, 189, 238, 253).
  - Update all calls to `txn.addStatusHistory(...)` to pass `Instant.now(clock)` as the fifth argument. There are calls on lines 73, 74, 119, 135, 166, 169, 177, 180, 187, 239.
  **Verify:** file compiles with all other Phase 2 changes applied.

- [ ] **2.4** Modify `payment-backend/src/main/java/ro/accesa/payment/service/ExpirationScheduler.java`:
  - Remove `@RequiredArgsConstructor` (we will write an explicit constructor).
  - Add `private final Clock clock;` field.
  - Add explicit constructor: `public ExpirationScheduler(PaymentTransactionRepository repository, Clock clock)`.
  - Replace `Instant.now()` on line 30 with `Instant.now(clock)`.
  - Replace `Instant.now()` on lines 37 and 38 with `Instant.now(clock)`.
  - Update the `txn.addStatusHistory(...)` call on line 36 to pass `Instant.now(clock)` as the fifth argument.
  **Verify:** file compiles.

- [ ] **2.5** Modify `payment-backend/src/main/java/ro/accesa/payment/service/TransactionRefGenerator.java`:
  - Add `private final Clock clock;` field.
  - Add explicit constructor: `public TransactionRefGenerator(Clock clock)`.
  - Replace `LocalDate.now()` on line 18 with `LocalDate.now(clock)`.
  **Verify:** file compiles.

- [ ] **2.6** Modify `payment-backend/src/main/java/ro/accesa/payment/service/BankIntegrationService.java`:
  - Add `private final Clock clock;` field.
  - Change constructor to accept `RestClient.Builder restClientBuilder` and `Clock clock` instead of building RestClient internally:
    ```java
    public BankIntegrationService(
            RestClient.Builder restClientBuilder,
            Clock clock,
            @Value("${app.bank-mock.base-url}") String bankMockBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(bankMockBaseUrl).build();
        this.clock = clock;
    }
    ```
  - Replace `Instant.now()` on lines 32 and 50 with `Instant.now(clock)`.
  - Also on line 60: `Instant respondedAt = Instant.now();` becomes `Instant respondedAt = Instant.now(clock);`.
  **Verify:** file compiles.

- [ ] **2.7** Modify `bank-mock/src/main/java/ro/accesa/payment/bankmock/service/SepaInstantSimulatorService.java`:
  - Add `private final long delayMinMs;` and `private final long delayMaxMs;` fields.
  - Add constructor with `@Value` annotations:
    ```java
    public SepaInstantSimulatorService(
            @Value("${bank-mock.simulate-delay-min-ms}") long delayMinMs,
            @Value("${bank-mock.simulate-delay-max-ms}") long delayMaxMs) {
        this.delayMinMs = delayMinMs;
        this.delayMaxMs = delayMaxMs;
    }
    ```
  - Replace the hardcoded sleep block with:
    ```java
    if (delayMaxMs > 0) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(delayMinMs, delayMaxMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    ```
  **Verify:** file compiles.

### Phase 2 Verification
Run `cd payment-backend && mvn clean package -q` and `cd bank-mock && mvn clean package -q`. Both must compile and pass (0 tests).

---

## Phase 3: Pure Unit Tests

**Goal:** Write tests for pure logic classes with zero external dependencies.

### Tasks

- [ ] **3.1** Create `payment-backend/src/test/java/ro/accesa/payment/domain/TransactionStatusTest.java`:
  ```java
  package ro.accesa.payment.domain;

  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.params.ParameterizedTest;
  import org.junit.jupiter.params.provider.EnumSource;

  import java.util.List;

  import static org.assertj.core.api.Assertions.assertThat;
  import static ro.accesa.payment.domain.TransactionStatus.*;

  class TransactionStatusTest {
      // test methods listed below
  }
  ```

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `initiated_canTransitionToQrGenerated()` | `INITIATED.canTransitionTo(QR_GENERATED)` is true |
  | `initiated_canTransitionToCancelled()` | `INITIATED.canTransitionTo(CANCELLED)` is true |
  | `initiated_canTransitionToExpired()` | `INITIATED.canTransitionTo(EXPIRED)` is true |
  | `initiated_cannotTransitionToApproved()` | `INITIATED.canTransitionTo(APPROVED)` is false |
  | `qrGenerated_canTransitionToCustomerOpened()` | `QR_GENERATED.canTransitionTo(CUSTOMER_OPENED)` is true |
  | `qrGenerated_canTransitionToCancelledAndExpired()` | Both are true |
  | `qrGenerated_cannotTransitionToApproved()` | false |
  | `customerOpened_canTransitionToCustomerConfirmed()` | true |
  | `customerOpened_canTransitionToCancelledAndExpired()` | Both are true |
  | `customerConfirmed_canTransitionToProcessing()` | true |
  | `customerConfirmed_canTransitionToFailedAndExpired()` | Both true |
  | `customerConfirmed_cannotTransitionToApproved()` | false |
  | `processing_canTransitionToApprovedRejectedFailed()` | All three true |
  | `processing_cannotTransitionToExpired()` | false (PROCESSING -> EXPIRED not in map) |
  | `terminalStates_cannotTransitionAnywhere()` | Parameterized: for each terminal state (APPROVED, REJECTED, FAILED, EXPIRED, CANCELLED), `canTransitionTo(target)` is false for all targets |
  | `terminalStates_areTerminal()` | `isTerminal()` returns true for APPROVED, REJECTED, FAILED, EXPIRED, CANCELLED |
  | `nonTerminalStates_areNotTerminal()` | `isTerminal()` returns false for INITIATED, QR_GENERATED, CUSTOMER_OPENED, CUSTOMER_CONFIRMED, PROCESSING |

  **Verify:** `cd payment-backend && mvn test -Dtest=TransactionStatusTest -q` -- all tests pass.

- [ ] **3.2** Create `payment-backend/src/test/java/ro/accesa/payment/service/TransactionRefGeneratorTest.java`:
  ```java
  package ro.accesa.payment.service;

  import org.junit.jupiter.api.Test;
  import static org.assertj.core.api.Assertions.assertThat;
  import static ro.accesa.payment.TestFixtures.FIXED_CLOCK;

  class TransactionRefGeneratorTest {
      private final TransactionRefGenerator generator = new TransactionRefGenerator(FIXED_CLOCK);
      // test methods listed below
  }
  ```

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `generate_shouldStartWithTxnPrefix()` | Result starts with `"TXN-"` |
  | `generate_shouldContainFixedDate()` | Result contains `"20260331"` (from FIXED_CLOCK) |
  | `generate_shouldMatchExpectedFormat()` | Result matches regex `TXN-\\d{8}-[A-Z0-9]{6}` |
  | `generate_shouldProduceDifferentRefsOnConsecutiveCalls()` | Two calls return different values (random suffix) |
  | `generate_shouldHaveExactLength()` | Result length is exactly `TXN-` (4) + date (8) + `-` (1) + suffix (6) = 19 chars |

  **Verify:** `cd payment-backend && mvn test -Dtest=TransactionRefGeneratorTest -q` -- all pass.

- [ ] **3.3** Create `bank-mock/src/test/java/ro/accesa/payment/bankmock/service/SepaInstantSimulatorServiceTest.java`:
  ```java
  package ro.accesa.payment.bankmock.service;

  import org.junit.jupiter.api.Test;
  import ro.accesa.payment.bankmock.dto.BankResponse;
  import ro.accesa.payment.bankmock.dto.Pain001Request;

  import java.math.BigDecimal;

  import static org.assertj.core.api.Assertions.assertThat;

  class SepaInstantSimulatorServiceTest {
      // Construct with zero delay for fast tests
      private final SepaInstantSimulatorService service = new SepaInstantSimulatorService(0, 0);
      // test methods + helper below
  }
  ```

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `processPayment_shouldAcceptNormalPayment()` | status=`ACCP`, bankReference is not null, messageId matches input |
  | `processPayment_shouldRejectIbanEndingWith9999()` | status=`RJCT`, statusReason=`INSUFFICIENT_FUNDS` |
  | `processPayment_shouldRejectIbanEndingWith8888()` | status=`RJCT`, statusReason=`ACCOUNT_BLOCKED` |
  | `processPayment_shouldRejectAmountExceeding15000()` | status=`RJCT`, statusReason=`AMOUNT_EXCEEDS_LIMIT` |
  | `processPayment_shouldAcceptExactly15000()` | status=`ACCP` |
  | `processPayment_shouldAcceptAmountJustBelow15000()` | `14999.99` -- status=`ACCP` |
  | `processPayment_shouldSetExecutionTimestampOnAccept()` | executionTimestamp is not null when accepted |
  | `processPayment_shouldPreserveMessageId()` | response messageId matches request messageId |

  Include a private helper `buildRequest(String debtorIban, BigDecimal amount)` that constructs a valid `Pain001Request` with debtor, creditor, amount, messageId, and remittanceInformation.

  **Verify:** `cd bank-mock && mvn test -Dtest=SepaInstantSimulatorServiceTest -q` -- all pass.

### Phase 3 Verification
Run `cd payment-backend && mvn test -q` and `cd bank-mock && mvn test -q`. All tests pass.

---

## Phase 4: Service Layer Tests

**Goal:** Test `TransactionService`, `BankIntegrationService`, and `ExpirationScheduler` using Mockito mocks.

### Tasks

- [ ] **4.1** Create `payment-backend/src/test/java/ro/accesa/payment/service/TransactionServiceTest.java`:
  ```java
  package ro.accesa.payment.service;

  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.ArgumentCaptor;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import ro.accesa.payment.domain.*;
  import ro.accesa.payment.dto.request.*;
  import ro.accesa.payment.dto.response.*;
  import ro.accesa.payment.exception.*;
  import ro.accesa.payment.repository.*;

  import java.math.BigDecimal;
  import java.time.Instant;
  import java.util.Optional;

  import static org.assertj.core.api.Assertions.*;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.*;
  import static ro.accesa.payment.TestFixtures.*;

  @ExtendWith(MockitoExtension.class)
  class TransactionServiceTest {

      @Mock PaymentTransactionRepository transactionRepository;
      @Mock MerchantRepository merchantRepository;
      @Mock TransactionRefGenerator refGenerator;
      @Mock BankIntegrationService bankIntegrationService;

      TransactionService service;

      @BeforeEach
      void setUp() {
          service = new TransactionService(
              transactionRepository, merchantRepository, refGenerator,
              bankIntegrationService, FIXED_CLOCK,
              "http://localhost:5174/pay", 5);
      }
      // test methods below
  }
  ```

  **Test methods to include:**

  **initiateTransaction tests:**
  | Method | Verifies |
  |---|---|
  | `initiateTransaction_shouldCreateTransactionWithQrGeneratedStatus()` | Response status is `QR_GENERATED`, qrPayload contains the ref, amount matches, `transactionRepository.save()` called once, saved txn has 2 statusHistory entries (INITIATED -> QR_GENERATED) |
  | `initiateTransaction_shouldDefaultCurrencyToEur()` | When request.currency is null, saved txn has `EUR` |
  | `initiateTransaction_shouldThrowWhenMerchantNotFound()` | `merchantRepository.findByMerchantCode()` returns empty -> throws `TransactionNotFoundException` |
  | `initiateTransaction_shouldSetExpiresAtToTtlMinutesFromNow()` | Saved txn's `expiresAt` equals `FIXED_INSTANT.plus(5, MINUTES)` |

  **getTransactionStatus tests:**
  | Method | Verifies |
  |---|---|
  | `getTransactionStatus_shouldReturnStatusForValidTransaction()` | Returns correct status, transactionRef, amount |
  | `getTransactionStatus_shouldThrowWhenTransactionNotFound()` | empty Optional -> `TransactionNotFoundException` |
  | `getTransactionStatus_shouldThrowWhenMerchantIdDoesNotMatch()` | txn.merchantId="LIDL001" but called with "OTHER" -> `TransactionNotFoundException` |

  **cancelTransaction tests:**
  | Method | Verifies |
  |---|---|
  | `cancelTransaction_shouldSetStatusToCancelled()` | Response status is `CANCELLED`, `transactionRepository.save()` called |
  | `cancelTransaction_shouldThrowWhenTransactionNotFound()` | `TransactionNotFoundException` |
  | `cancelTransaction_shouldThrowWhenMerchantIdDoesNotMatch()` | `TransactionNotFoundException` |
  | `cancelTransaction_shouldThrowWhenTransactionIsTerminal()` | txn status is APPROVED -> `InvalidTransactionStateException` (cannot transition from APPROVED to CANCELLED) |

  **getPaymentDetails tests:**
  | Method | Verifies |
  |---|---|
  | `getPaymentDetails_shouldReturnDetailsAndTransitionToCustomerOpened()` | Response has correct merchantName, amount; saved txn status is `CUSTOMER_OPENED` |
  | `getPaymentDetails_shouldNotTransitionIfAlreadyCustomerOpened()` | txn already `CUSTOMER_OPENED` -> no save called (or save not called for transition), response still correct |
  | `getPaymentDetails_shouldThrowWhenTransactionNotFound()` | `TransactionNotFoundException` |
  | `getPaymentDetails_shouldThrowWhenTransactionExpired()` | txn `expiresAt` is before `Instant.now(FIXED_CLOCK)` -> `TransactionExpiredException` |
  | `getPaymentDetails_shouldThrowWhenTransactionInTerminalState()` | txn status is APPROVED -> `InvalidTransactionStateException` |

  **confirmPayment tests:**
  | Method | Verifies |
  |---|---|
  | `confirmPayment_withBankApproval_shouldTransitionToApproved()` | bankIntegrationService returns ACCP -> response status is `APPROVED`, message contains "SEPA Instant" |
  | `confirmPayment_withBankRejection_shouldTransitionToRejected()` | bankIntegrationService returns RJCT with reason -> response status is `REJECTED` |
  | `confirmPayment_withBankException_shouldTransitionToFailed()` | bankIntegrationService throws RuntimeException -> response status is `FAILED` |
  | `confirmPayment_shouldRejectWhenConsentNotGiven()` | consentGiven=false -> `InvalidTransactionStateException` with message containing "Consent" |
  | `confirmPayment_shouldRejectWhenConsentIsNull()` | consentGiven=null -> `InvalidTransactionStateException` |
  | `confirmPayment_shouldSetDebtorInfo()` | After confirmation, saved txn debtor IBAN matches request (uppercased, spaces removed) |
  | `confirmPayment_shouldThrowWhenTransactionExpired()` | `TransactionExpiredException` |

  **getPaymentResult tests:**
  | Method | Verifies |
  |---|---|
  | `getPaymentResult_forApprovedTransaction_shouldReturnSuccessMessage()` | Message contains "successful" |
  | `getPaymentResult_forRejectedTransaction_shouldReturnFailureMessage()` | Message contains failure reason |
  | `getPaymentResult_forProcessingTransaction_shouldReturnWaitMessage()` | Message contains "being processed" |
  | `getPaymentResult_shouldThrowWhenNotFound()` | `TransactionNotFoundException` |

  **Verify:** `cd payment-backend && mvn test -Dtest=TransactionServiceTest -q` -- all pass.

- [ ] **4.2** Create `payment-backend/src/test/java/ro/accesa/payment/service/BankIntegrationServiceTest.java`:
  ```java
  package ro.accesa.payment.service;

  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.client.MockRestServiceServer;
  import org.springframework.web.client.RestClient;
  import ro.accesa.payment.domain.*;

  import java.math.BigDecimal;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
  import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
  import static ro.accesa.payment.TestFixtures.*;

  class BankIntegrationServiceTest {

      private MockRestServiceServer mockServer;
      private BankIntegrationService service;

      @BeforeEach
      void setUp() {
          RestClient.Builder builder = RestClient.builder();
          mockServer = MockRestServiceServer.bindTo(builder).build();
          service = new BankIntegrationService(builder, FIXED_CLOCK, "http://localhost:8081");
      }
      // test methods below
  }
  ```

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `submitPayment_shouldReturnAcceptedResponse()` | Mock server returns `{"status":"ACCP","bankReference":"BANK-1"}` -> service returns `BankIntegrationInfo` with bankResponseCode=`ACCP`, bankReference=`BANK-1` |
  | `submitPayment_shouldReturnRejectedResponse()` | Mock server returns `{"status":"RJCT","statusReason":"INSUFFICIENT_FUNDS"}` -> bankResponseCode=`RJCT`, bankResponseReason=`INSUFFICIENT_FUNDS` |
  | `submitPayment_shouldSendRequestToCorrectEndpoint()` | MockServer expects POST to `/bank-mock/sepa-instant/initiate` |
  | `submitPayment_shouldSetPainMessageId()` | Returned `painMessageId` starts with `MSG-TXN-20260331-ABC123-` |
  | `submitPayment_shouldSetSubmittedAtAndRespondedAt()` | Both timestamps equal `FIXED_INSTANT` (since clock is fixed) |
  | `submitPayment_shouldHandleNullDebtorName()` | txn with debtor name=null -> request body contains `"name":"Unknown"` |
  | `submitPayment_whenServerError_shouldThrowException()` | Mock server returns 500 -> `RestClientException` or similar is thrown |

  For each test that needs a transaction, use `TestFixtures.aTransactionWithDebtor().build()`.

  **Verify:** `cd payment-backend && mvn test -Dtest=BankIntegrationServiceTest -q` -- all pass.

- [ ] **4.3** Create `payment-backend/src/test/java/ro/accesa/payment/service/ExpirationSchedulerTest.java`:
  ```java
  package ro.accesa.payment.service;

  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.ArgumentCaptor;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import ro.accesa.payment.domain.*;
  import ro.accesa.payment.repository.PaymentTransactionRepository;

  import java.util.Collections;
  import java.util.List;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.*;
  import static ro.accesa.payment.TestFixtures.*;

  @ExtendWith(MockitoExtension.class)
  class ExpirationSchedulerTest {

      @Mock PaymentTransactionRepository repository;

      // test methods below
  }
  ```

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `expireStaleTransactions_shouldExpireStaleTransactions()` | Given repository returns a list with one stale txn (status=QR_GENERATED, expiresAt in past), scheduler sets status to EXPIRED, calls `repository.save()`, and adds status history entry |
  | `expireStaleTransactions_shouldSetCompletedAt()` | Saved txn has completedAt = `FIXED_INSTANT` |
  | `expireStaleTransactions_shouldDoNothingWhenNoStaleTransactions()` | Repository returns empty list -> `save()` never called |
  | `expireStaleTransactions_shouldQueryCorrectStatuses()` | Verify the `findByStatusInAndExpiresAtBefore` is called with `[INITIATED, QR_GENERATED, CUSTOMER_OPENED, CUSTOMER_CONFIRMED]` and `FIXED_INSTANT` |

  **Verify:** `cd payment-backend && mvn test -Dtest=ExpirationSchedulerTest -q` -- all pass.

### Phase 4 Verification
Run `cd payment-backend && mvn test -q`. All tests pass.

---

## Phase 5: Security and Controller Tests

**Goal:** Test `ApiKeyAuthFilter` and both controllers using MockMvc.

### Tasks

- [ ] **5.1** Create `payment-backend/src/test/java/ro/accesa/payment/security/ApiKeyAuthFilterTest.java`:
  ```java
  package ro.accesa.payment.security;

  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import jakarta.servlet.FilterChain;
  import org.springframework.mock.web.MockHttpServletRequest;
  import org.springframework.mock.web.MockHttpServletResponse;
  import ro.accesa.payment.domain.Merchant;
  import ro.accesa.payment.repository.MerchantRepository;

  import java.util.Optional;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.Mockito.*;
  import static ro.accesa.payment.TestFixtures.*;

  @ExtendWith(MockitoExtension.class)
  class ApiKeyAuthFilterTest {

      @Mock MerchantRepository merchantRepository;
      @Mock FilterChain filterChain;

      ApiKeyAuthFilter filter;

      @BeforeEach
      void setUp() {
          filter = new ApiKeyAuthFilter(merchantRepository);
      }
      // test methods below
  }
  ```

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `shouldNotFilterNonTransactionPaths()` | Request to `/api/v1/payment/REF` -> `shouldNotFilter()` returns true |
  | `shouldFilterTransactionPaths()` | Request to `/api/v1/transactions/initiate` -> `shouldNotFilter()` returns false |
  | `shouldReturn401WhenApiKeyMissing()` | No `X-API-Key` header -> response status 401, body contains "Missing X-API-Key" |
  | `shouldReturn401WhenApiKeyBlank()` | Header set to `""` -> 401 |
  | `shouldReturn401WhenApiKeyInvalid()` | `merchantRepository.findByApiKeyHash()` returns empty -> 401, body contains "Invalid API key" |
  | `shouldReturn401WhenMerchantInactive()` | Merchant found but `active=false` -> 401 |
  | `shouldSetMerchantIdAttributeAndContinueChain()` | Valid key, active merchant -> `filterChain.doFilter()` called, `request.getAttribute("merchantId")` equals merchant's merchantCode |

  **Verify:** `cd payment-backend && mvn test -Dtest=ApiKeyAuthFilterTest -q` -- all pass.

- [ ] **5.2** Create `payment-backend/src/test/java/ro/accesa/payment/controller/MerchantTransactionControllerTest.java`:
  ```java
  package ro.accesa.payment.controller;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.bean.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;
  import ro.accesa.payment.dto.request.InitiateTransactionRequest;
  import ro.accesa.payment.dto.response.*;
  import ro.accesa.payment.exception.TransactionNotFoundException;
  import ro.accesa.payment.repository.MerchantRepository;
  import ro.accesa.payment.service.TransactionService;

  import java.math.BigDecimal;
  import java.time.Instant;

  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(MerchantTransactionController.class)
  class MerchantTransactionControllerTest {

      @Autowired MockMvc mockMvc;
      @Autowired ObjectMapper objectMapper;
      @MockBean TransactionService transactionService;
      @MockBean MerchantRepository merchantRepository; // needed by ApiKeyAuthFilter
      // test methods below
  }
  ```

  **Note:** Since `ApiKeyAuthFilter` is a `@Component`, it will be loaded in the `@WebMvcTest` slice. Tests must either: (a) set the `merchantId` request attribute directly using a `RequestPostProcessor`, or (b) mock `merchantRepository.findByApiKeyHash()` to return an active merchant for the test API key. Approach (b) is recommended so the filter is also exercised.

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `initiateTransaction_shouldReturn201WithValidRequest()` | POST `/api/v1/transactions/initiate` with valid body and `X-API-Key: sk_test_lidl_001` -> 201, response JSON has transactionRef, status=`QR_GENERATED` |
  | `initiateTransaction_shouldReturn401WithoutApiKey()` | POST without `X-API-Key` header -> 401 |
  | `initiateTransaction_shouldReturn400WhenAmountMissing()` | Body without `amount` field -> 400 (validation error) |
  | `initiateTransaction_shouldReturn400WhenDescriptionBlank()` | Body with `description: ""` -> 400 |
  | `getTransactionStatus_shouldReturn200()` | GET `/api/v1/transactions/txn-1/status` with API key -> 200, response has status field |
  | `getTransactionStatus_shouldReturn404WhenNotFound()` | Service throws `TransactionNotFoundException` -> 404 |
  | `cancelTransaction_shouldReturn200()` | POST `/api/v1/transactions/txn-1/cancel` with API key -> 200, response has status=`CANCELLED` |

  **Verify:** `cd payment-backend && mvn test -Dtest=MerchantTransactionControllerTest -q` -- all pass.

- [ ] **5.3** Create `payment-backend/src/test/java/ro/accesa/payment/controller/CustomerPaymentControllerTest.java`:
  ```java
  package ro.accesa.payment.controller;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.bean.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.servlet.MockMvc;
  import ro.accesa.payment.dto.request.ConfirmPaymentRequest;
  import ro.accesa.payment.dto.response.*;
  import ro.accesa.payment.exception.*;
  import ro.accesa.payment.repository.MerchantRepository;
  import ro.accesa.payment.service.TransactionService;

  import static org.mockito.ArgumentMatchers.*;
  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(CustomerPaymentController.class)
  class CustomerPaymentControllerTest {

      @Autowired MockMvc mockMvc;
      @Autowired ObjectMapper objectMapper;
      @MockBean TransactionService transactionService;
      @MockBean MerchantRepository merchantRepository; // needed by ApiKeyAuthFilter in context
      // test methods below
  }
  ```

  **Note:** Customer endpoints are at `/api/v1/payment/**` which the `ApiKeyAuthFilter.shouldNotFilter()` skips (it only guards `/api/v1/transactions/**`). So no API key header is needed.

  **Test methods to include:**
  | Method | Verifies |
  |---|---|
  | `getPaymentDetails_shouldReturn200()` | GET `/api/v1/payment/TXN-REF` -> 200, response has merchantName, amount, status |
  | `getPaymentDetails_shouldReturn404WhenNotFound()` | Service throws `TransactionNotFoundException` -> 404 |
  | `getPaymentDetails_shouldReturn410WhenExpired()` | Service throws `TransactionExpiredException` -> 410 (GONE) |
  | `confirmPayment_shouldReturn202()` | POST `/api/v1/payment/TXN-REF/confirm` with valid body -> 202, response has status |
  | `confirmPayment_shouldReturn400WhenIbanMissing()` | Body without `debtorIban` -> 400 |
  | `confirmPayment_shouldReturn409WhenInvalidState()` | Service throws `InvalidTransactionStateException` -> 409 (CONFLICT) |
  | `getPaymentResult_shouldReturn200()` | GET `/api/v1/payment/TXN-REF/result` -> 200, response has status and message |
  | `getPaymentResult_shouldReturn404WhenNotFound()` | 404 |

  **Verify:** `cd payment-backend && mvn test -Dtest=CustomerPaymentControllerTest -q` -- all pass.

### Phase 5 Verification
Run `cd payment-backend && mvn test -q`. All tests pass (Phase 3 + 4 + 5 combined).

---

## Phase 6: Frontend Test Infrastructure

**Goal:** Install test dependencies, configure Vitest, create setup files and MSW mocks for both frontend apps.

### Tasks

- [ ] **6.1** Install test dependencies in `customer-app`:
  ```bash
  cd customer-app && npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
  ```
  **Verify:** All packages appear in `devDependencies` of `customer-app/package.json`.

- [ ] **6.2** Add test scripts to `customer-app/package.json`. Add these to the `"scripts"` object:
  ```json
  "test": "vitest run",
  "test:watch": "vitest",
  "test:coverage": "vitest run --coverage"
  ```
  **Verify:** `"test"` key exists in scripts.

- [ ] **6.3** Update `customer-app/vite.config.ts` to add Vitest configuration:
  ```typescript
  /// <reference types="vitest" />
  import { defineConfig } from 'vite'
  import react from '@vitejs/plugin-react'

  export default defineConfig({
    plugins: [react()],
    server: {
      port: 5174,
      proxy: {
        '/api': 'http://localhost:8080',
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      css: true,
    },
  })
  ```
  **Verify:** file has `test` block.

- [ ] **6.4** Create `customer-app/src/test/setup.ts`:
  ```typescript
  import '@testing-library/jest-dom';
  import { server } from './mocks/server';

  beforeAll(() => server.listen());
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());
  ```
  **Verify:** file exists.

- [ ] **6.5** Create `customer-app/src/test/mocks/handlers.ts`:
  ```typescript
  import { http, HttpResponse } from 'msw';

  export const handlers = [
    http.get('/api/v1/payment/:ref', () => {
      return HttpResponse.json({
        transactionRef: 'TXN-20260331-ABC123',
        merchantName: 'Lidl',
        amount: 25.50,
        currency: 'EUR',
        description: 'Groceries',
        creditorName: 'Lidl',
        status: 'CUSTOMER_OPENED',
        expiresAt: '2026-03-31T10:05:00Z',
      });
    }),
    http.post('/api/v1/payment/:ref/confirm', () => {
      return HttpResponse.json({
        transactionRef: 'TXN-20260331-ABC123',
        status: 'APPROVED',
        message: 'Payment is being processed via SEPA Instant.',
      });
    }),
    http.get('/api/v1/payment/:ref/result', () => {
      return HttpResponse.json({
        transactionRef: 'TXN-20260331-ABC123',
        status: 'APPROVED',
        message: 'Payment of EUR 25.50 to Lidl was successful.',
        completedAt: '2026-03-31T10:01:00Z',
      });
    }),
  ];
  ```
  **Verify:** file exists.

- [ ] **6.6** Create `customer-app/src/test/mocks/server.ts`:
  ```typescript
  import { setupServer } from 'msw/node';
  import { handlers } from './handlers';

  export const server = setupServer(...handlers);
  ```
  **Verify:** file exists.

- [ ] **6.7** Install test dependencies in `retailer-app`:
  ```bash
  cd retailer-app && npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
  ```
  **Verify:** All packages appear in `devDependencies` of `retailer-app/package.json`.

- [ ] **6.8** Add test scripts to `retailer-app/package.json`. Same three scripts as 6.2.
  **Verify:** `"test"` key exists in scripts.

- [ ] **6.9** Update `retailer-app/vite.config.ts` to add Vitest configuration:
  ```typescript
  /// <reference types="vitest" />
  import { defineConfig } from 'vite'
  import react from '@vitejs/plugin-react'

  export default defineConfig({
    plugins: [react()],
    server: {
      proxy: {
        '/api': 'http://localhost:8080',
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      css: true,
    },
  })
  ```
  **Verify:** file has `test` block.

- [ ] **6.10** Create `retailer-app/src/test/setup.ts`:
  ```typescript
  import '@testing-library/jest-dom';
  import { server } from './mocks/server';

  beforeAll(() => server.listen());
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());
  ```
  **Verify:** file exists.

- [ ] **6.11** Create `retailer-app/src/test/mocks/handlers.ts`:
  ```typescript
  import { http, HttpResponse } from 'msw';

  export const handlers = [
    http.post('/api/v1/transactions/initiate', () => {
      return HttpResponse.json({
        transactionId: 'txn-1',
        transactionRef: 'TXN-20260331-ABC123',
        status: 'QR_GENERATED',
        amount: 25.50,
        currency: 'EUR',
        description: 'Groceries',
        qrPayload: 'http://localhost:5174/pay/TXN-20260331-ABC123',
        expiresAt: '2026-03-31T10:05:00Z',
        initiatedAt: '2026-03-31T10:00:00Z',
      });
    }),
    http.get('/api/v1/transactions/:id/status', () => {
      return HttpResponse.json({
        transactionId: 'txn-1',
        transactionRef: 'TXN-20260331-ABC123',
        status: 'APPROVED',
        amount: 25.50,
        currency: 'EUR',
      });
    }),
    http.post('/api/v1/transactions/:id/cancel', () => {
      return new HttpResponse(null, { status: 200 });
    }),
  ];
  ```
  **Verify:** file exists.

- [ ] **6.12** Create `retailer-app/src/test/mocks/server.ts`:
  ```typescript
  import { setupServer } from 'msw/node';
  import { handlers } from './handlers';

  export const server = setupServer(...handlers);
  ```
  **Verify:** file exists.

### Phase 6 Verification
Run `cd customer-app && npx vitest run 2>&1 | head -5` and `cd retailer-app && npx vitest run 2>&1 | head -5`. Both should report 0 tests (no test files yet) and exit cleanly. Also verify `npm run build` still works for both apps.

---

## Phase 7: Frontend Tests

**Goal:** Write unit and component tests for both frontend apps.

### Tasks

- [ ] **7.1** Create `customer-app/src/utils/ibanValidator.test.ts`:
  ```typescript
  import { describe, it, expect } from 'vitest';
  import { validateIban, formatIban } from './ibanValidator';
  ```

  **Test cases for `validateIban`:**
  | Test name | Input | Expected |
  |---|---|---|
  | `accepts valid German IBAN` | `'DE89370400440532013000'` | `{ valid: true }` |
  | `accepts valid Romanian IBAN` | `'RO49AAAA1B31007593840000'` | `{ valid: true }` |
  | `accepts IBAN with spaces` | `'DE89 3704 0044 0532 0130 00'` | `{ valid: true }` |
  | `accepts lowercase IBAN` | `'de89370400440532013000'` | `{ valid: true }` |
  | `rejects IBAN shorter than 15 chars` | `'DE893704'` | `valid: false`, error contains `too short` |
  | `rejects IBAN longer than 34 chars` | `'DE89370400440532013000123456789012345'` | `valid: false`, error contains `too long` |
  | `rejects IBAN with wrong country length` | `'DE8937040044053201300'` (21 chars, DE needs 22) | `valid: false`, error contains `22 characters` |
  | `rejects IBAN with invalid checksum` | `'DE00370400440532013000'` | `valid: false`, error contains `checksum` |

  **Test cases for `formatIban`:**
  | Test name | Input | Expected |
  |---|---|---|
  | `formats IBAN into groups of 4` | `'DE89370400440532013000'` | `'DE89 3704 0044 0532 0130 00'` |
  | `handles already formatted IBAN` | `'DE89 3704 0044 0532 0130 00'` | `'DE89 3704 0044 0532 0130 00'` |
  | `uppercases input` | `'de89370400440532013000'` | `'DE89 3704 0044 0532 0130 00'` |

  **Verify:** `cd customer-app && npx vitest run src/utils/ibanValidator.test.ts` -- all pass.

- [ ] **7.2** Create `customer-app/src/api/paymentApi.test.ts`:
  ```typescript
  import { describe, it, expect } from 'vitest';
  import { getPaymentDetails, confirmPayment, getPaymentResult } from './paymentApi';
  ```

  **Test cases (using MSW handlers from setup):**
  | Test name | Verifies |
  |---|---|
  | `getPaymentDetails returns payment details` | Calls `getPaymentDetails('TXN-REF')`, asserts response has `merchantName`, `amount`, `status` |
  | `confirmPayment returns confirmation response` | Calls `confirmPayment('TXN-REF', { debtorIban: '...', consentGiven: true })`, asserts response has `status: 'APPROVED'` |
  | `getPaymentResult returns result` | Calls `getPaymentResult('TXN-REF')`, asserts response has `status` and `message` |

  **Verify:** `cd customer-app && npx vitest run src/api/paymentApi.test.ts` -- all pass.

- [ ] **7.3** Create `retailer-app/src/api/paymentApi.test.ts`:
  ```typescript
  import { describe, it, expect } from 'vitest';
  import { initiatePayment, getTransactionStatus, cancelTransaction } from './paymentApi';
  ```

  **Test cases (using MSW handlers from setup):**
  | Test name | Verifies |
  |---|---|
  | `initiatePayment returns initiation response` | Calls `initiatePayment(25.50, 'Groceries')`, asserts response has `transactionRef`, `status: 'QR_GENERATED'`, `qrPayload` |
  | `getTransactionStatus returns status` | Calls `getTransactionStatus('txn-1')`, asserts response has `status`, `transactionRef` |
  | `cancelTransaction completes without error` | Calls `cancelTransaction('txn-1')`, asserts no exception thrown |

  **Verify:** `cd retailer-app && npx vitest run src/api/paymentApi.test.ts` -- all pass.

- [ ] **7.4** Create `customer-app/src/components/PaymentDetails.test.tsx`:
  ```tsx
  import { describe, it, expect, vi } from 'vitest';
  import { render, screen } from '@testing-library/react';
  import userEvent from '@testing-library/user-event';
  import PaymentDetails from './PaymentDetails';
  ```

  **Test cases:**
  | Test name | Verifies |
  |---|---|
  | `renders merchant name` | Screen contains `paymentDetails.merchantName` text |
  | `renders formatted amount` | Screen contains the formatted amount |
  | `renders description when provided` | Description text is visible |
  | `renders transaction reference` | Ref text is visible |
  | `calls onProceed when button clicked` | Click "Proceed to Pay" button -> `onProceed` mock called once |

  Use mock `paymentDetails` object matching the `PaymentDetails` type.

  **Verify:** `cd customer-app && npx vitest run src/components/PaymentDetails.test.tsx` -- all pass.

- [ ] **7.5** Create `customer-app/src/components/ConfirmPayment.test.tsx`:
  ```tsx
  import { describe, it, expect, vi } from 'vitest';
  import { render, screen } from '@testing-library/react';
  import userEvent from '@testing-library/user-event';
  import ConfirmPayment from './ConfirmPayment';
  ```

  **Test cases:**
  | Test name | Verifies |
  |---|---|
  | `disables submit button initially` | Button with text "Confirm & Pay" is disabled (no IBAN entered, no consent) |
  | `shows validation error for invalid IBAN` | Type `'INVALID'` into IBAN input, then tab out -> error text appears |
  | `shows checkmark for valid IBAN` | Type valid IBAN `'DE89370400440532013000'` -> checkmark (unicode 10003) appears |
  | `enables button when IBAN valid and consent given` | Enter valid IBAN, check consent checkbox -> button is enabled |
  | `calls onConfirm with correct data` | Enter valid IBAN, check consent, click submit -> `onConfirm` called with `{ debtorIban: 'DE89370400440532013000', consentGiven: true }` |
  | `disables inputs when loading` | Pass `loading={true}` -> IBAN input and checkbox are disabled |

  Use mock `paymentDetails` matching the `PaymentDetails` type.

  **Verify:** `cd customer-app && npx vitest run src/components/ConfirmPayment.test.tsx` -- all pass.

- [ ] **7.6** Create `retailer-app/src/components/NewPayment.test.tsx`:
  ```tsx
  import { describe, it, expect, vi } from 'vitest';
  import { render, screen, waitFor } from '@testing-library/react';
  import userEvent from '@testing-library/user-event';
  import NewPayment from './NewPayment';
  ```

  **Test cases:**
  | Test name | Verifies |
  |---|---|
  | `renders amount input and description textarea` | Screen has input with label "Amount (EUR)" and textarea with label "Description" |
  | `shows error for invalid amount` | Clear amount field, submit -> error text "valid amount" appears |
  | `calls onPaymentInitiated after successful submission` | Enter amount `25.50`, description `Groceries`, submit -> `onPaymentInitiated` called with response object (from MSW handler) |
  | `shows loading state during submission` | During API call, button text changes to "Processing..." |
  | `shows error message on API failure` | Override MSW handler to return 500 -> error text is visible |

  **Verify:** `cd retailer-app && npx vitest run src/components/NewPayment.test.tsx` -- all pass.

### Phase 7 Verification
Run `cd customer-app && npm test` and `cd retailer-app && npm test`. All tests pass in both apps.

---

## Final Verification Checklist

After all 7 phases are complete, run these commands and confirm all pass:

1. `cd payment-backend && mvn clean test -q` -- all backend tests pass
2. `cd bank-mock && mvn clean test -q` -- all bank-mock tests pass
3. `cd customer-app && npm test` -- all customer-app tests pass
4. `cd retailer-app && npm test` -- all retailer-app tests pass
5. `cd payment-backend && mvn clean package -q` -- full build succeeds
6. `cd bank-mock && mvn clean package -q` -- full build succeeds
7. `cd customer-app && npm run build` -- frontend build succeeds
8. `cd retailer-app && npm run build` -- frontend build succeeds

---

## Summary

| Phase | Tasks | Test files created | Estimated test count |
|---|---|---|---|
| 1 - Backend Test Infra | 7 | 0 (infra only) | 0 |
| 2 - Testability Refactorings | 7 | 0 (prod code only) | 0 |
| 3 - Pure Unit Tests | 3 | 3 (TransactionStatusTest, TransactionRefGeneratorTest, SepaInstantSimulatorServiceTest) | ~30 |
| 4 - Service Layer Tests | 3 | 3 (TransactionServiceTest, BankIntegrationServiceTest, ExpirationSchedulerTest) | ~32 |
| 5 - Security & Controller Tests | 3 | 3 (ApiKeyAuthFilterTest, MerchantTransactionControllerTest, CustomerPaymentControllerTest) | ~22 |
| 6 - Frontend Test Infra | 12 | 0 (infra only) | 0 |
| 7 - Frontend Tests | 6 | 6 (ibanValidator.test.ts, 2x paymentApi.test.ts, PaymentDetails.test.tsx, ConfirmPayment.test.tsx, NewPayment.test.tsx) | ~30 |
| **Total** | **41** | **15** | **~114** |
