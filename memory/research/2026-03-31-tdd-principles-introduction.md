# TDD Principles Introduction - Research Findings

**Date:** 2026-03-31
**Scope:** All four services (payment-backend, bank-mock, retailer-app, customer-app)

---

## 1. Current Test Coverage and Testing Patterns

### Current State: Zero Tests

None of the four services have any test files. Specifically:

- **payment-backend**: No `src/test/` directory exists. No JUnit test classes.
- **bank-mock**: No `src/test/` directory exists. No JUnit test classes.
- **retailer-app**: No `*.test.ts`, `*.test.tsx`, `*.spec.ts`, or `*.spec.tsx` files. No test script in `package.json`.
- **customer-app**: Same as retailer-app - no test files, no test script.

### Existing Test Dependencies

| Service | Test Dependencies Present | Missing Dependencies |
|---|---|---|
| **payment-backend** | `spring-boot-starter-test` (includes JUnit 5, Mockito, AssertJ, Spring MockMvc, Hamcrest) | Testcontainers for MongoDB, `de.flapdoodle.embed.mongo` for embedded MongoDB |
| **bank-mock** | `spring-boot-starter-test` (same bundle) | None needed for unit tests |
| **retailer-app** | None | Vitest, @testing-library/react, @testing-library/jest-dom, @testing-library/user-event, msw (Mock Service Worker) |
| **customer-app** | None | Same as retailer-app |

### Key Finding
The Java backend services already have `spring-boot-starter-test` which bundles JUnit 5 + Mockito + AssertJ - the core TDD toolkit. The frontend apps have zero test infrastructure and need dependencies added.

---

## 2. What TDD Practices Would Be Most Beneficial

### High-Value TDD Targets (Priority Order)

#### Priority 1 - Pure Logic (Easiest TDD wins, highest confidence)
1. **`TransactionStatus.canTransitionTo()`** - State machine transition logic is a textbook TDD case. Every valid and invalid transition should be captured in tests BEFORE any changes to the state machine.
2. **`ibanValidator.ts`** (customer-app) - Pure function with well-defined inputs/outputs. Algorithmic validation with known edge cases (country lengths, mod-97 checksum). Ideal for TDD.
3. **`TransactionRefGenerator.generate()`** - Pattern validation (format: `TXN-YYYYMMDD-XXXXXX`), uniqueness properties.
4. **`SepaInstantSimulatorService.processPayment()`** - Deterministic rejection rules (IBAN endings, amount limit). Pure business logic with clear rules.

#### Priority 2 - Service Layer (Core business logic, most risk)
5. **`TransactionService`** - The heart of the system. All six public methods orchestrate the payment lifecycle. TDD here catches state machine violations, incorrect status transitions, and missing edge cases (expired transactions, wrong merchant, null consent).
6. **`BankIntegrationService.submitPayment()`** - External integration. TDD with mocked RestClient ensures the pain.001 mapping is correct and error handling works.

#### Priority 3 - API Layer (Contract testing)
7. **`MerchantTransactionController`** - MockMvc tests verify HTTP status codes, request validation, response shapes, and API key enforcement.
8. **`CustomerPaymentController`** - Same pattern. Verify public endpoints work without auth.
9. **`ApiKeyAuthFilter`** - Security filter logic: missing key, invalid key, inactive merchant, valid key.

#### Priority 4 - Frontend Components
10. **`NewPayment.tsx`** (retailer) - Form validation, API call triggering.
11. **`PaymentDetails.tsx` / `ConfirmPayment.tsx`** (customer) - IBAN input, consent checkbox, form submission flow.
12. **API client modules** (`paymentApi.ts` in both apps) - Request/response mapping.

### Why This Order?
- Pure logic tests run in <1ms, give immediate feedback, and have the highest ROI.
- Service layer contains the most business risk (payment state machine errors = real money problems).
- API tests catch contract breakage before integration.
- Frontend component tests are valuable but slower to write and less critical for a payment backend.

---

## 3. Recommended TDD Workflow

### 3.1 Java/Spring Boot Backend (payment-backend, bank-mock)

#### Red-Green-Refactor Cycle

```
1. RED:    Write a failing test that describes the expected behavior
2. GREEN:  Write the minimum code to make the test pass
3. REFACTOR: Clean up without changing behavior (tests stay green)
```

#### Test Pyramid for Backend

```
                    /  \
                   / E2E \          (few - Docker Compose integration tests)
                  /--------\
                 / Integration \    (some - MockMvc, Testcontainers)
                /--------------\
               /   Unit Tests    \  (many - Mockito, plain JUnit)
              /------------------\
```

#### Concrete Workflow for Adding a New Feature (e.g., "partial refund")

```bash
# Step 1: Write the unit test first
# File: src/test/java/ro/accesa/payment/service/TransactionServiceTest.java

@Test
void confirmPayment_shouldRejectWhenConsentNotGiven() {
    // Given
    var request = new ConfirmPaymentRequest("DE89370400440532013000", "John", null, false);
    var txn = buildTransaction(TransactionStatus.CUSTOMER_OPENED);
    when(repository.findByTransactionRef("REF-123")).thenReturn(Optional.of(txn));

    // When / Then
    assertThatThrownBy(() -> service.confirmPayment("REF-123", request))
        .isInstanceOf(InvalidTransactionStateException.class)
        .hasMessageContaining("Consent must be given");
}

# Step 2: Run -> RED (fails)
mvn test -Dtest=TransactionServiceTest#confirmPayment_shouldRejectWhenConsentNotGiven

# Step 3: Implement -> GREEN (passes)
# Step 4: Refactor if needed, re-run tests
```

#### Test Class Organization

```
src/test/java/ro/accesa/payment/
├── domain/
│   └── TransactionStatusTest.java          # State machine transitions
├── service/
│   ├── TransactionServiceTest.java         # Unit tests with mocked deps
│   ├── BankIntegrationServiceTest.java     # Unit tests with mocked RestClient
│   ├── TransactionRefGeneratorTest.java    # Pure logic
│   └── ExpirationSchedulerTest.java        # Scheduler logic
├── controller/
│   ├── MerchantTransactionControllerTest.java  # MockMvc
│   └── CustomerPaymentControllerTest.java      # MockMvc
├── security/
│   └── ApiKeyAuthFilterTest.java           # Filter logic
└── exception/
    └── GlobalExceptionHandlerTest.java     # Error response shapes
```

### 3.2 React/TypeScript Frontend (retailer-app, customer-app)

#### Test Pyramid for Frontend

```
                    /  \
                   / E2E \          (optional - Playwright/Cypress)
                  /--------\
                 / Integration \    (component tests with MSW for API mocking)
                /--------------\
               /   Unit Tests    \  (pure functions, hooks, utils)
              /------------------\
```

#### Concrete Workflow

```bash
# Step 1: Write test for ibanValidator
# File: src/utils/ibanValidator.test.ts

import { validateIban, formatIban } from './ibanValidator';

describe('validateIban', () => {
  it('should accept a valid German IBAN', () => {
    expect(validateIban('DE89 3704 0044 0532 0130 00')).toEqual({ valid: true });
  });

  it('should reject IBAN with wrong checksum', () => {
    const result = validateIban('DE00370400440532013000');
    expect(result.valid).toBe(false);
    expect(result.error).toContain('checksum');
  });

  it('should reject IBAN that is too short', () => {
    const result = validateIban('DE89');
    expect(result.valid).toBe(false);
  });
});

# Step 2: Run -> RED
npx vitest run src/utils/ibanValidator.test.ts

# Step 3: (code already exists, so tests should pass - this is "characterization testing")
# For new features, write the test first, then implement.
```

#### Test File Organization

```
customer-app/src/
├── utils/
│   ├── ibanValidator.ts
│   └── ibanValidator.test.ts           # Co-located unit test
├── api/
│   ├── paymentApi.ts
│   └── paymentApi.test.ts              # API client tests with MSW
├── components/
│   ├── PaymentDetails.tsx
│   ├── PaymentDetails.test.tsx         # Component rendering + interaction
│   ├── ConfirmPayment.tsx
│   └── ConfirmPayment.test.tsx
└── pages/
    ├── PayPage.tsx
    └── PayPage.test.tsx                # Integration test (full page flow)
```

---

## 4. How to Integrate TDD into the Development Workflow

### 4.1 Immediate Setup Steps

#### Backend (payment-backend)

1. **Create the test directory structure:**
   ```
   mkdir -p src/test/java/ro/accesa/payment/{domain,service,controller,security,exception}
   mkdir -p src/test/resources
   ```

2. **Add test application properties:**
   ```yaml
   # src/test/resources/application-test.yml
   app:
     qr.base-url: http://localhost:5174/pay
     transaction.ttl-minutes: 5
     bank-mock.base-url: http://localhost:8081
   ```

3. **Add Testcontainers for MongoDB integration tests (optional, pom.xml):**
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

#### Backend (bank-mock)
- Same structure, simpler: only needs `SepaInstantSimulatorServiceTest.java` and `BankMockControllerTest.java`.

#### Frontend (both apps)

1. **Install test dependencies:**
   ```bash
   npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom msw
   ```

2. **Configure Vitest in `vite.config.ts`:**
   ```typescript
   /// <reference types="vitest" />
   import { defineConfig } from 'vite';
   import react from '@vitejs/plugin-react';

   export default defineConfig({
     plugins: [react()],
     test: {
       globals: true,
       environment: 'jsdom',
       setupFiles: './src/test/setup.ts',
       css: true,
     },
   });
   ```

3. **Create test setup file `src/test/setup.ts`:**
   ```typescript
   import '@testing-library/jest-dom';
   ```

4. **Add test script to `package.json`:**
   ```json
   "scripts": {
     "test": "vitest run",
     "test:watch": "vitest",
     "test:coverage": "vitest run --coverage"
   }
   ```

### 4.2 CI/CD Integration

No CI/CD pipeline exists yet (no `.github/workflows/`, no `Jenkinsfile`). When one is created, the recommended pipeline stages are:

```yaml
# Suggested GitHub Actions workflow
name: CI
on: [push, pull_request]

jobs:
  backend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: cd payment-backend && mvn test
      - run: cd bank-mock && mvn test

  frontend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd retailer-app && npm ci && npm test
      - run: cd customer-app && npm ci && npm test
```

### 4.3 TDD in the Agent Workflow

Given the team leader protocol in CLAUDE.md, TDD fits naturally:

1. **Planner** writes the test plan (which tests to write, in which order)
2. **Implementer** writes tests FIRST (Red), then implementation (Green), then refactors
3. **Reviewer** checks that tests exist for all new logic and that TDD ordering was followed (test file modified before/with source file in the same commit)

### 4.4 Pre-commit Hooks (Optional but Recommended)

```bash
# Using Maven wrapper for backend
mvn test -pl payment-backend -q

# Using Vitest for frontend
cd retailer-app && npx vitest run --reporter=dot
cd customer-app && npx vitest run --reporter=dot
```

---

## 5. Specific Frameworks and Tools

### Backend Stack

| Tool | Already in pom.xml? | Purpose |
|---|---|---|
| **JUnit 5** (Jupiter) | Yes (via `spring-boot-starter-test`) | Test framework |
| **Mockito** | Yes (via `spring-boot-starter-test`) | Mocking dependencies |
| **AssertJ** | Yes (via `spring-boot-starter-test`) | Fluent assertions |
| **Spring MockMvc** | Yes (via `spring-boot-starter-test`) | Controller testing without server |
| **Hamcrest** | Yes (via `spring-boot-starter-test`) | Matchers |
| **Testcontainers** | **No - needs adding** | MongoDB integration tests |
| **Embedded MongoDB (flapdoodle)** | **No - needs adding** | Lightweight alternative to Testcontainers |
| **WireMock** | **No - optional** | Mock bank-mock HTTP responses for BankIntegrationService |
| **JaCoCo** | **No - recommended** | Code coverage reporting |

### Frontend Stack

| Tool | Already in package.json? | Purpose |
|---|---|---|
| **Vitest** | **No - needs adding** | Test runner (Vite-native, fast) |
| **@testing-library/react** | **No - needs adding** | Component testing |
| **@testing-library/jest-dom** | **No - needs adding** | DOM assertions |
| **@testing-library/user-event** | **No - needs adding** | Simulating user interactions |
| **jsdom** | **No - needs adding** | Browser environment for tests |
| **MSW (Mock Service Worker)** | **No - needs adding** | API mocking at network level |
| **@vitest/coverage-v8** | **No - optional** | Coverage reporting |

### Recommended Additions to `pom.xml` (payment-backend)

```xml
<!-- Code coverage -->
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

---

## 6. TDD Examples for Key Components

### 6.1 TransactionStatus State Machine (Pure Unit Test)

```java
@Test
void qrGenerated_canTransitionToCustomerOpened() {
    assertTrue(TransactionStatus.QR_GENERATED.canTransitionTo(TransactionStatus.CUSTOMER_OPENED));
}

@Test
void qrGenerated_cannotTransitionToApproved() {
    assertFalse(TransactionStatus.QR_GENERATED.canTransitionTo(TransactionStatus.APPROVED));
}

@Test
void terminalStates_cannotTransitionAnywhere() {
    for (TransactionStatus terminal : List.of(APPROVED, REJECTED, FAILED, EXPIRED, CANCELLED)) {
        assertTrue(terminal.isTerminal());
        for (TransactionStatus target : TransactionStatus.values()) {
            assertFalse(terminal.canTransitionTo(target),
                terminal + " should not transition to " + target);
        }
    }
}

@Test
void processing_canTransitionToApprovedOrRejectedOrFailed() {
    assertTrue(PROCESSING.canTransitionTo(APPROVED));
    assertTrue(PROCESSING.canTransitionTo(REJECTED));
    assertTrue(PROCESSING.canTransitionTo(FAILED));
    assertFalse(PROCESSING.canTransitionTo(EXPIRED)); // Note: PROCESSING -> EXPIRED not in allowed map
}
```

### 6.2 TransactionService (Unit Test with Mocks)

```java
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
            bankIntegrationService, "http://localhost:5174/pay", 5);
    }

    @Test
    void initiateTransaction_shouldCreateTransactionWithQrGeneratedStatus() {
        // Given
        var merchant = Merchant.builder()
            .merchantCode("LIDL001").name("Lidl").iban("DE1234").bic("COBADEFF").build();
        when(merchantRepository.findByMerchantCode("LIDL001")).thenReturn(Optional.of(merchant));
        when(refGenerator.generate()).thenReturn("TXN-20260331-ABC123");
        var request = new InitiateTransactionRequest(new BigDecimal("25.50"), "EUR", "Groceries");

        // When
        var response = service.initiateTransaction("LIDL001", request);

        // Then
        assertThat(response.getStatus()).isEqualTo("QR_GENERATED");
        assertThat(response.getQrPayload()).contains("TXN-20260331-ABC123");
        assertThat(response.getAmount()).isEqualByComparingTo("25.50");

        var captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatusHistory()).hasSize(2); // INITIATED + QR_GENERATED
    }

    @Test
    void confirmPayment_withBankApproval_shouldTransitionToApproved() {
        // Given
        var txn = buildTxn(TransactionStatus.CUSTOMER_OPENED, Instant.now().plusSeconds(300));
        when(transactionRepository.findByTransactionRef("REF")).thenReturn(Optional.of(txn));
        when(bankIntegrationService.submitPayment(any())).thenReturn(
            BankIntegrationInfo.builder().bankResponseCode("ACCP").bankReference("BANK-123").build());
        var request = new ConfirmPaymentRequest("DE89370400440532013000", "John", null, true);

        // When
        var response = service.confirmPayment("REF", request);

        // Then
        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void confirmPayment_withBankRejection_shouldTransitionToRejected() {
        // Given
        var txn = buildTxn(TransactionStatus.CUSTOMER_OPENED, Instant.now().plusSeconds(300));
        when(transactionRepository.findByTransactionRef("REF")).thenReturn(Optional.of(txn));
        when(bankIntegrationService.submitPayment(any())).thenReturn(
            BankIntegrationInfo.builder().bankResponseCode("RJCT").bankResponseReason("INSUFFICIENT_FUNDS").build());
        var request = new ConfirmPaymentRequest("DE89370400440532019999", "John", null, true);

        // When
        var response = service.confirmPayment("REF", request);

        // Then
        assertThat(response.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void confirmPayment_withBankException_shouldTransitionToFailed() {
        // Given
        var txn = buildTxn(TransactionStatus.CUSTOMER_OPENED, Instant.now().plusSeconds(300));
        when(transactionRepository.findByTransactionRef("REF")).thenReturn(Optional.of(txn));
        when(bankIntegrationService.submitPayment(any())).thenThrow(new RuntimeException("Connection timeout"));
        var request = new ConfirmPaymentRequest("DE89370400440532013000", "John", null, true);

        // When
        var response = service.confirmPayment("REF", request);

        // Then
        assertThat(response.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void getPaymentDetails_forExpiredTransaction_shouldThrow() {
        // Given
        var txn = buildTxn(TransactionStatus.QR_GENERATED, Instant.now().minusSeconds(60));
        when(transactionRepository.findByTransactionRef("REF")).thenReturn(Optional.of(txn));

        // When / Then
        assertThatThrownBy(() -> service.getPaymentDetails("REF"))
            .isInstanceOf(TransactionExpiredException.class);
    }

    @Test
    void cancelTransaction_wrongMerchant_shouldThrowNotFound() {
        // Given
        var txn = buildTxn(TransactionStatus.QR_GENERATED, Instant.now().plusSeconds(300));
        txn.setMerchantId("LIDL001");
        when(transactionRepository.findById("TX-1")).thenReturn(Optional.of(txn));

        // When / Then
        assertThatThrownBy(() -> service.cancelTransaction("TX-1", "WRONG_MERCHANT"))
            .isInstanceOf(TransactionNotFoundException.class);
    }
}
```

### 6.3 BankIntegrationService (Unit Test with Mocked RestClient)

```java
@ExtendWith(MockitoExtension.class)
class BankIntegrationServiceTest {

    // Use MockRestServiceServer or WireMock to intercept RestClient calls

    @Test
    void submitPayment_shouldMapPain001FieldsCorrectly() {
        // Verify the request body sent to bank-mock contains:
        // - messageId starting with "MSG-"
        // - debtor.iban matching txn.getDebtor().getIban()
        // - creditor.iban matching txn.getCreditor().getIban()
        // - amount.value matching txn.getAmount()
    }

    @Test
    void submitPayment_shouldHandleNullDebtorName() {
        // When debtor name is null, should default to "Unknown"
    }

    @Test
    void submitPayment_whenBankReturnsNull_shouldReturnUnknownStatus() {
        // Edge case: what if the RestClient returns null?
    }
}
```

### 6.4 SepaInstantSimulatorService (bank-mock, Pure Unit Test)

```java
class SepaInstantSimulatorServiceTest {

    SepaInstantSimulatorService service = new SepaInstantSimulatorService();

    @Test
    void shouldAcceptNormalPayment() {
        var request = buildRequest("DE89370400440532013000", new BigDecimal("100"));
        var response = service.processPayment(request);
        assertThat(response.getStatus()).isEqualTo("ACCP");
        assertThat(response.getBankReference()).isNotNull();
    }

    @Test
    void shouldRejectIbanEndingWith9999() {
        var request = buildRequest("DE89370400440532019999", new BigDecimal("100"));
        var response = service.processPayment(request);
        assertThat(response.getStatus()).isEqualTo("RJCT");
        assertThat(response.getStatusReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void shouldRejectIbanEndingWith8888() {
        var request = buildRequest("DE89370400440532018888", new BigDecimal("100"));
        var response = service.processPayment(request);
        assertThat(response.getStatus()).isEqualTo("RJCT");
        assertThat(response.getStatusReason()).isEqualTo("ACCOUNT_BLOCKED");
    }

    @Test
    void shouldRejectAmountExceeding15000() {
        var request = buildRequest("DE89370400440532013000", new BigDecimal("15001"));
        var response = service.processPayment(request);
        assertThat(response.getStatus()).isEqualTo("RJCT");
        assertThat(response.getStatusReason()).isEqualTo("AMOUNT_EXCEEDS_LIMIT");
    }

    @Test
    void shouldAcceptExactly15000() {
        var request = buildRequest("DE89370400440532013000", new BigDecimal("15000"));
        var response = service.processPayment(request);
        assertThat(response.getStatus()).isEqualTo("ACCP");
    }
}
```

### 6.5 IBAN Validator (Frontend Unit Test)

```typescript
import { describe, it, expect } from 'vitest';
import { validateIban, formatIban } from './ibanValidator';

describe('validateIban', () => {
  it('accepts valid German IBAN', () => {
    expect(validateIban('DE89370400440532013000')).toEqual({ valid: true });
  });

  it('accepts valid Romanian IBAN', () => {
    expect(validateIban('RO49AAAA1B31007593840000')).toEqual({ valid: true });
  });

  it('accepts IBAN with spaces', () => {
    expect(validateIban('DE89 3704 0044 0532 0130 00')).toEqual({ valid: true });
  });

  it('rejects IBAN shorter than 15 chars', () => {
    const result = validateIban('DE893704');
    expect(result.valid).toBe(false);
    expect(result.error).toContain('too short');
  });

  it('rejects IBAN with wrong country length', () => {
    const result = validateIban('DE8937040044053201300'); // 21 chars, DE needs 22
    expect(result.valid).toBe(false);
    expect(result.error).toContain('22 characters');
  });

  it('rejects IBAN with invalid checksum', () => {
    const result = validateIban('DE00370400440532013000');
    expect(result.valid).toBe(false);
    expect(result.error).toContain('checksum');
  });
});

describe('formatIban', () => {
  it('formats IBAN into groups of 4', () => {
    expect(formatIban('DE89370400440532013000')).toBe('DE89 3704 0044 0532 0130 00');
  });

  it('handles already formatted IBAN', () => {
    expect(formatIban('DE89 3704 0044 0532 0130 00')).toBe('DE89 3704 0044 0532 0130 00');
  });
});
```

### 6.6 Frontend Component Test (Customer ConfirmPayment)

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmPayment } from './ConfirmPayment';

describe('ConfirmPayment', () => {
  it('disables submit button when IBAN is empty', () => {
    render(<ConfirmPayment details={mockDetails} onConfirm={vi.fn()} />);
    expect(screen.getByRole('button', { name: /confirm/i })).toBeDisabled();
  });

  it('shows validation error for invalid IBAN', async () => {
    render(<ConfirmPayment details={mockDetails} onConfirm={vi.fn()} />);
    const input = screen.getByLabelText(/iban/i);
    await userEvent.type(input, 'INVALID');
    await userEvent.tab(); // trigger blur validation
    expect(screen.getByText(/checksum|too short/i)).toBeInTheDocument();
  });

  it('calls onConfirm with correct data when form is valid', async () => {
    const onConfirm = vi.fn();
    render(<ConfirmPayment details={mockDetails} onConfirm={onConfirm} />);
    await userEvent.type(screen.getByLabelText(/iban/i), 'DE89370400440532013000');
    await userEvent.click(screen.getByLabelText(/consent/i));
    await userEvent.click(screen.getByRole('button', { name: /confirm/i }));
    expect(onConfirm).toHaveBeenCalledWith(expect.objectContaining({
      debtorIban: 'DE89370400440532013000',
      consentGiven: true,
    }));
  });
});
```

---

## 7. Recommended Implementation Plan

### Phase 1: Foundation (Day 1)
- [ ] Create test directory structures for all services
- [ ] Add missing test dependencies (Vitest + RTL for frontend)
- [ ] Configure Vitest in both frontend apps
- [ ] Write first passing test in each service (smoke test)
- [ ] Add `test` script to frontend `package.json`

### Phase 2: Core Logic Tests (Day 2-3)
- [ ] `TransactionStatusTest` - all state machine transitions (pure unit)
- [ ] `SepaInstantSimulatorServiceTest` - all rejection rules (pure unit)
- [ ] `ibanValidator.test.ts` - all validation paths (pure unit)
- [ ] `TransactionRefGeneratorTest` - format and uniqueness (pure unit)
- [ ] `ExpirationSchedulerTest` - expiration logic

### Phase 3: Service Layer Tests (Day 3-5)
- [ ] `TransactionServiceTest` - all 6 public methods with mocked dependencies
- [ ] `BankIntegrationServiceTest` - request mapping + error handling
- [ ] `ApiKeyAuthFilterTest` - security edge cases

### Phase 4: API Contract Tests (Day 5-6)
- [ ] `MerchantTransactionControllerTest` - MockMvc tests for all merchant endpoints
- [ ] `CustomerPaymentControllerTest` - MockMvc tests for all customer endpoints
- [ ] `GlobalExceptionHandlerTest` - error response format verification

### Phase 5: Frontend Component Tests (Day 6-8)
- [ ] API client tests with MSW
- [ ] Component rendering tests
- [ ] User interaction flow tests

### Phase 6: Integration & Coverage (Day 8-10)
- [ ] Add JaCoCo for Java coverage reporting
- [ ] Add Vitest coverage for frontend
- [ ] Add Testcontainers for MongoDB integration tests (optional)
- [ ] Set up CI pipeline with test gates
- [ ] Set minimum coverage thresholds (recommend: 70% line coverage for backend, 60% for frontend)

---

## 8. Key TDD Principles to Follow

1. **Test behavior, not implementation** - test what the method does, not how it does it.
2. **One assertion concept per test** - each test verifies one logical thing (may have multiple `assertThat` calls but about one concept).
3. **Arrange-Act-Assert (AAA)** or **Given-When-Then** - consistent test structure.
4. **Test names describe behavior** - `shouldRejectWhenConsentNotGiven()` not `test1()`.
5. **Fast tests** - unit tests should run in <100ms each. No network, no disk, no database.
6. **Independent tests** - no shared mutable state, no test ordering dependencies.
7. **Write the test BEFORE the code for new features** - for existing code, write characterization tests first.
8. **Refactor only when green** - never refactor with failing tests.

---

## 9. Risks and Considerations

1. **MongoDB dependency in tests**: `TransactionService` depends on `PaymentTransactionRepository` (Spring Data MongoDB). For unit tests, Mockito mocks suffice. For integration tests, either Testcontainers or embedded MongoDB is needed. Recommend starting with pure unit tests (mocked) and adding Testcontainers later.

2. **`Instant.now()` in production code**: Several methods use `Instant.now()` directly (in `TransactionService`, `PaymentTransaction.addStatusHistory()`). This makes tests time-dependent. For TDD, consider injecting a `Clock` or using `Instant.now(clock)` to make time controllable in tests. This is a refactoring opportunity after initial characterization tests are in place.

3. **`Thread.sleep()` in bank-mock**: `SepaInstantSimulatorService.processPayment()` has a 200-800ms sleep. Unit tests should not include this delay. Either: (a) extract the delay to a separate method that can be overridden, or (b) accept the delay in tests (not recommended for TDD speed).

4. **RestClient in BankIntegrationService**: The `RestClient` is created in the constructor with a hardcoded base URL. For testability, consider accepting a `RestClient` parameter or using `MockRestServiceServer`. WireMock is another option for integration-level tests.

5. **No existing CI/CD**: Tests need to be enforced somewhere. Even without CI, adding `mvn test` as a pre-push hook would prevent untested code from being pushed.
