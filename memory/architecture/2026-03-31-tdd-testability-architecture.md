# TDD Testability Architecture

**Date:** 2026-03-31
**Purpose:** Minimal, targeted refactorings to unblock TDD across all four services.

---

## 1. Testability Refactoring: Inject `java.time.Clock`

### Problem
`Instant.now()` is called directly in 4 places, making tests time-dependent:
- `TransactionService`: lines 50, 120, 167, 179, 182, 189, 238, 253 (via `Instant.now()`)
- `PaymentTransaction.addStatusHistory()`: line 57 (`Instant.now()`)
- `ExpirationScheduler.expireStaleTransactions()`: lines 31, 38, 39 (`Instant.now()`)
- `TransactionRefGenerator.generate()`: line 18 (`LocalDate.now()`)
- `BankIntegrationService.submitPayment()`: lines 32, 50 (`Instant.now()`)

### Solution: Single `Clock` bean, injected where needed

#### Step 1: Create a `ClockConfig` class

```java
// payment-backend/src/main/java/ro/accesa/payment/config/ClockConfig.java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

#### Step 2: Inject `Clock` into services

**TransactionService** -- add `Clock clock` as a constructor parameter:
```java
public TransactionService(
        PaymentTransactionRepository transactionRepository,
        MerchantRepository merchantRepository,
        TransactionRefGenerator refGenerator,
        BankIntegrationService bankIntegrationService,
        Clock clock,
        @Value("${app.qr.base-url}") String qrBaseUrl,
        @Value("${app.transaction.ttl-minutes}") int ttlMinutes) {
    this.clock = clock;
    // ... rest unchanged
}
```
Then replace all `Instant.now()` with `Instant.now(clock)`.

**PaymentTransaction.addStatusHistory()** -- this is a domain object (not Spring-managed), so passing `Clock` through the method signature is cleaner than injection:
```java
public void addStatusHistory(TransactionStatus from, TransactionStatus to,
                             String reason, String actor, Instant timestamp) {
    // Use the provided timestamp instead of Instant.now()
}
```
The caller (`TransactionService`) passes `Instant.now(clock)`. This keeps the domain object free of Spring dependencies.

**ExpirationScheduler** -- inject `Clock`:
```java
public class ExpirationScheduler {
    private final PaymentTransactionRepository repository;
    private final Clock clock;
    // replace Instant.now() with Instant.now(clock)
}
```

**TransactionRefGenerator** -- inject `Clock`:
```java
public class TransactionRefGenerator {
    private final Clock clock;
    // replace LocalDate.now() with LocalDate.now(clock)
}
```

**BankIntegrationService** -- inject `Clock`:
```java
public class BankIntegrationService {
    private final RestClient restClient;
    private final Clock clock;
    // replace Instant.now() with Instant.now(clock)
}
```

#### Test usage
In unit tests, use a fixed clock:
```java
Clock fixedClock = Clock.fixed(Instant.parse("2026-03-31T10:00:00Z"), ZoneOffset.UTC);
```
This makes all time-dependent behavior deterministic and testable.

### Files changed
| File | Change |
|---|---|
| `config/ClockConfig.java` | **NEW** -- `@Bean Clock` returning `Clock.systemUTC()` |
| `service/TransactionService.java` | Add `Clock` constructor param, replace `Instant.now()` |
| `domain/PaymentTransaction.java` | Change `addStatusHistory` to accept `Instant timestamp` |
| `service/ExpirationScheduler.java` | Add `Clock` field, replace `Instant.now()` |
| `service/TransactionRefGenerator.java` | Add `Clock` field, replace `LocalDate.now()` |
| `service/BankIntegrationService.java` | Add `Clock` field, replace `Instant.now()` |

---

## 2. Testability Refactoring: Bank-Mock Configurable Delay

### Problem
`SepaInstantSimulatorService` hardcodes `Thread.sleep(ThreadLocalRandom.current().nextLong(200, 801))` despite `application.yml` already having `bank-mock.simulate-delay-min-ms` and `bank-mock.simulate-delay-max-ms` properties. Tests are slowed by 200-800ms per call.

### Solution: Read delay from config, allow zero in test profile

#### Step 1: Inject config values into SepaInstantSimulatorService
```java
@Service
public class SepaInstantSimulatorService {
    private final long delayMinMs;
    private final long delayMaxMs;

    public SepaInstantSimulatorService(
            @Value("${bank-mock.simulate-delay-min-ms}") long delayMinMs,
            @Value("${bank-mock.simulate-delay-max-ms}") long delayMaxMs) {
        this.delayMinMs = delayMinMs;
        this.delayMaxMs = delayMaxMs;
    }

    public BankResponse processPayment(Pain001Request request) {
        if (delayMaxMs > 0) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(delayMinMs, delayMaxMs + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // ... rest of business logic unchanged
    }
}
```

#### Step 2: Test application properties
```yaml
# bank-mock/src/test/resources/application-test.yml
bank-mock:
  simulate-delay-min-ms: 0
  simulate-delay-max-ms: 0
```

#### Step 3: For pure unit tests (no Spring context)
Construct directly: `new SepaInstantSimulatorService(0, 0)` -- zero delay.

### Files changed
| File | Change |
|---|---|
| `bankmock/service/SepaInstantSimulatorService.java` | Inject delay config, skip sleep when max=0 |
| `bank-mock/src/test/resources/application-test.yml` | **NEW** -- zero delay config |

---

## 3. Testability Refactoring: BankIntegrationService RestClient Injection

### Problem
`BankIntegrationService` builds its `RestClient` in the constructor from a `@Value` string. This makes it impossible to inject a mock `RestClient` for unit testing.

### Solution: Accept `RestClient.Builder` from Spring (preferred Spring Boot pattern)

Spring Boot auto-configures a `RestClient.Builder` bean. Use it:

```java
@Service
@Slf4j
public class BankIntegrationService {
    private final RestClient restClient;
    private final Clock clock;

    public BankIntegrationService(
            RestClient.Builder restClientBuilder,
            Clock clock,
            @Value("${app.bank-mock.base-url}") String bankMockBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(bankMockBaseUrl).build();
        this.clock = clock;
    }
}
```

#### For unit tests
Use `MockRestServiceServer` (comes with `spring-boot-starter-test`):
```java
RestClient.Builder builder = RestClient.builder();
MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
BankIntegrationService service = new BankIntegrationService(builder, fixedClock, "http://localhost:8081");

server.expect(requestTo("/bank-mock/sepa-instant/initiate"))
    .andRespond(withSuccess("{...}", MediaType.APPLICATION_JSON));
```

Alternative for pure Mockito (no Spring context): since `RestClient` is an interface we cannot easily mock the fluent chain. `MockRestServiceServer` is the cleanest approach for this class. For integration tests, WireMock is another option.

### Files changed
| File | Change |
|---|---|
| `service/BankIntegrationService.java` | Accept `RestClient.Builder` + `Clock` instead of building internally |

---

## 4. Additional Testability Improvement: TransactionRefGenerator

### Problem
`TransactionRefGenerator` uses `new SecureRandom()` internally and `LocalDate.now()`. Both are non-deterministic.

### Solution
- `Clock` injection (covered in section 1) fixes the date part.
- For the random suffix: the current approach is acceptable for testing. Tests should assert the format pattern (`TXN-YYYYMMDD-XXXXXX`) rather than exact values. No refactoring needed for the random part.

---

## 5. Test Infrastructure Setup

### 5.1 payment-backend

#### Directory structure to create
```
payment-backend/src/test/
  java/ro/accesa/payment/
    domain/
      TransactionStatusTest.java
    service/
      TransactionServiceTest.java
      BankIntegrationServiceTest.java
      TransactionRefGeneratorTest.java
      ExpirationSchedulerTest.java
    controller/
      MerchantTransactionControllerTest.java
      CustomerPaymentControllerTest.java
    security/
      ApiKeyAuthFilterTest.java
  resources/
    application-test.yml
```

#### application-test.yml
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

#### Dependencies to add to pom.xml
```xml
<!-- Testcontainers for MongoDB integration tests -->
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

And a Testcontainers BOM in `<dependencyManagement>`:
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

JaCoCo plugin (in `<build><plugins>`):
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

#### Base test utility: TestFixtures (optional helper, not a base class)
A static factory class for building test domain objects:
```java
// payment-backend/src/test/java/ro/accesa/payment/TestFixtures.java
public final class TestFixtures {
    public static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-03-31T10:00:00Z"), ZoneOffset.UTC);

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
                .name("Lidl").iban("DE1234").bic("COBADEFF").build())
            .initiatedAt(Instant.now(FIXED_CLOCK))
            .expiresAt(Instant.now(FIXED_CLOCK).plus(5, ChronoUnit.MINUTES))
            .createdAt(Instant.now(FIXED_CLOCK))
            .updatedAt(Instant.now(FIXED_CLOCK))
            .statusHistory(new ArrayList<>());
    }
}
```

### 5.2 bank-mock

#### Directory structure to create
```
bank-mock/src/test/
  java/ro/accesa/payment/bankmock/
    service/
      SepaInstantSimulatorServiceTest.java
    controller/
      BankMockControllerTest.java
  resources/
    application-test.yml
```

#### application-test.yml
```yaml
bank-mock:
  simulate-delay-min-ms: 0
  simulate-delay-max-ms: 0
```

#### Dependencies
No additional dependencies needed. `spring-boot-starter-test` is sufficient. No MongoDB, no external integrations.

### 5.3 retailer-app

#### Dependencies to add
```bash
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
```

#### vite.config.ts modification
Add Vitest config block:
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

#### Test setup file
```typescript
// retailer-app/src/test/setup.ts
import '@testing-library/jest-dom';
```

#### package.json scripts to add
```json
"test": "vitest run",
"test:watch": "vitest",
"test:coverage": "vitest run --coverage"
```

#### MSW handler setup (for API mocking)
```typescript
// retailer-app/src/test/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('/api/v1/transactions', () => {
    return HttpResponse.json({
      transactionId: 'txn-1',
      transactionRef: 'TXN-20260331-ABC123',
      status: 'QR_GENERATED',
      amount: 25.50,
      currency: 'EUR',
      qrPayload: 'http://localhost:5174/pay/TXN-20260331-ABC123',
    });
  }),
];
```

```typescript
// retailer-app/src/test/mocks/server.ts
import { setupServer } from 'msw/node';
import { handlers } from './handlers';
export const server = setupServer(...handlers);
```

Update `setup.ts`:
```typescript
import '@testing-library/jest-dom';
import { server } from './mocks/server';

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

### 5.4 customer-app

Same pattern as retailer-app:

#### Dependencies
```bash
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
```

#### vite.config.ts, setup.ts, package.json scripts
Identical pattern to retailer-app (see 5.3).

#### MSW handlers tailored to customer endpoints
```typescript
// customer-app/src/test/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('/api/v1/payment/:ref', ({ params }) => {
    return HttpResponse.json({
      transactionRef: params.ref,
      merchantName: 'Lidl',
      amount: 25.50,
      currency: 'EUR',
      status: 'CUSTOMER_OPENED',
    });
  }),
  http.post('/api/v1/payment/:ref/confirm', () => {
    return HttpResponse.json({
      transactionRef: 'TXN-20260331-ABC123',
      status: 'APPROVED',
      message: 'Payment is being processed via SEPA Instant.',
    });
  }),
];
```

---

## 6. Summary of All Changes

### Refactoring changes (production code)

| # | File | Change | Reason |
|---|---|---|---|
| 1 | `payment-backend/.../config/ClockConfig.java` | NEW: `@Bean Clock` | Central clock bean |
| 2 | `payment-backend/.../service/TransactionService.java` | Add `Clock` param, replace `Instant.now()` | Deterministic time in tests |
| 3 | `payment-backend/.../domain/PaymentTransaction.java` | `addStatusHistory` takes `Instant` param | Remove hidden `Instant.now()` |
| 4 | `payment-backend/.../service/ExpirationScheduler.java` | Add `Clock`, replace `Instant.now()` | Deterministic time in tests |
| 5 | `payment-backend/.../service/TransactionRefGenerator.java` | Add `Clock`, replace `LocalDate.now()` | Deterministic date in tests |
| 6 | `payment-backend/.../service/BankIntegrationService.java` | Accept `RestClient.Builder` + `Clock` | Mockable HTTP client, deterministic time |
| 7 | `bank-mock/.../service/SepaInstantSimulatorService.java` | Inject delay config from `@Value`, skip sleep when 0 | Fast tests |

### Infrastructure changes (test setup)

| # | Change | Service |
|---|---|---|
| 8 | Create `src/test/` directory tree | payment-backend |
| 9 | Create `src/test/resources/application-test.yml` | payment-backend |
| 10 | Add Testcontainers + JaCoCo to `pom.xml` | payment-backend |
| 11 | Create `TestFixtures.java` helper | payment-backend |
| 12 | Create `src/test/` directory tree | bank-mock |
| 13 | Create `src/test/resources/application-test.yml` | bank-mock |
| 14 | Install Vitest + RTL + MSW deps | retailer-app |
| 15 | Update `vite.config.ts` with test block | retailer-app |
| 16 | Create `src/test/setup.ts` + MSW mocks | retailer-app |
| 17 | Add test scripts to `package.json` | retailer-app |
| 18 | Install Vitest + RTL + MSW deps | customer-app |
| 19 | Update `vite.config.ts` with test block | customer-app |
| 20 | Create `src/test/setup.ts` + MSW mocks | customer-app |
| 21 | Add test scripts to `package.json` | customer-app |

### What we are NOT changing
- No new abstractions or interfaces beyond `Clock` (which is a JDK type)
- No architectural changes to the service layer
- No changes to API contracts or domain model structure
- No changes to MongoDB schema
- No introduction of dependency injection frameworks beyond what Spring already provides
- `SecureRandom` in `TransactionRefGenerator` stays as-is (test format, not exact value)

---

## 7. Implementation Order

The implementer should apply changes in this order:

1. **Phase A: Backend testability refactoring** (must be done first, unblocks all backend tests)
   - Create `ClockConfig.java`
   - Modify `PaymentTransaction.addStatusHistory()` signature
   - Inject `Clock` into `TransactionService`, `ExpirationScheduler`, `TransactionRefGenerator`, `BankIntegrationService`
   - Modify `BankIntegrationService` to accept `RestClient.Builder`
   - Modify `SepaInstantSimulatorService` to use injected delay config

2. **Phase B: Backend test infrastructure** (can proceed right after Phase A)
   - Create test directory structures
   - Add dependencies to `pom.xml` files
   - Create `application-test.yml` files
   - Create `TestFixtures.java`
   - Verify build still passes: `mvn clean package`

3. **Phase C: Frontend test infrastructure** (independent of Phases A-B)
   - Install npm dependencies in both apps
   - Update `vite.config.ts` in both apps
   - Create test setup files and MSW mocks
   - Add test scripts to `package.json`
   - Verify build still passes: `npm run build`
