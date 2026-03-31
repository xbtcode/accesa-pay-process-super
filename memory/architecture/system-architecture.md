# SEPA Instant Payment System — Architecture Document

## System Overview

A monorepo with 4 services orchestrating SEPA Instant Credit Transfer payments at retail POS terminals. The retailer initiates a payment and displays a QR code, the customer scans it, enters their IBAN, and confirms. The backend processes the payment through a mock bank simulating SEPA pain.001.

## Deployment Topology

```
Docker Network
│
├─ MongoDB 7 (:27017)
│  ├─ Database: sepa_payments
│  └─ Collections: transactions, merchants
│
├─ Payment Backend (:8080) ─── Spring Boot 3.2.5, Java 17
│  ├─ MongoDB client (Spring Data)
│  └─ RestClient → Bank Mock
│
├─ Bank Mock (:8081) ─── Spring Boot 3.2.5, Java 17
│  └─ Stateless simulator (no database)
│
├─ Retailer App (:5173) ─── React 19, TypeScript, Vite
│  └─ Nginx proxy: /api → payment-backend:8080
│
└─ Customer App (:5174) ─── React 19, TypeScript, Vite
   └─ Nginx proxy: /api → payment-backend:8080
```

---

## Payment Backend (`payment-backend/`)

### Package Structure: `ro.accesa.payment`

```
ro.accesa.payment/
├── SepaPaymentApplication.java        @SpringBootApplication @EnableScheduling
├── config/
│   ├── MongoConfig.java               BigDecimal ↔ Decimal128 converters
│   └── WebConfig.java                 CORS: localhost:5173, localhost:5174
├── controller/
│   ├── MerchantTransactionController  /api/v1/transactions/** (API key required)
│   └── CustomerPaymentController      /api/v1/payment/** (public)
├── domain/
│   ├── PaymentTransaction.java        @Document("transactions") — core entity
│   ├── Merchant.java                  @Document("merchants")
│   ├── TransactionStatus.java         Enum with state machine transitions
│   ├── StatusHistoryEntry.java        Embedded audit entry
│   ├── CreditorInfo.java              Embedded merchant account
│   ├── DebtorInfo.java                Embedded customer account
│   ├── BankIntegrationInfo.java       Embedded bank response data
│   └── Address.java                   Embedded address
├── dto/
│   ├── request/
│   │   ├── InitiateTransactionRequest (amount, currency, description)
│   │   └── ConfirmPaymentRequest      (debtorIban, debtorName, consentGiven)
│   └── response/
│       ├── InitiateTransactionResponse
│       ├── TransactionStatusResponse
│       ├── PaymentDetailsResponse
│       ├── PaymentConfirmResponse
│       ├── PaymentResultResponse
│       └── CancelTransactionResponse
├── repository/
│   ├── PaymentTransactionRepository   findByTransactionRef, findByStatusInAndExpiresAtBefore
│   └── MerchantRepository             findByApiKeyHash, findByMerchantCode
├── service/
│   ├── TransactionService.java        Core orchestration — 6 business methods
│   ├── BankIntegrationService.java    RestClient → bank-mock POST
│   ├── TransactionRefGenerator.java   TXN-YYYYMMDD-XXXXXX (SecureRandom)
│   └── ExpirationScheduler.java       @Scheduled(fixedRate=10000) expire stale txns
├── security/
│   └── ApiKeyAuthFilter.java          OncePerRequestFilter, X-API-Key header
└── exception/
    ├── TransactionNotFoundException   → 404
    ├── InvalidTransactionStateException → 409
    ├── TransactionExpiredException    → 410
    └── GlobalExceptionHandler.java    @RestControllerAdvice
```

### Transaction State Machine

```
INITIATED ──► QR_GENERATED ──► CUSTOMER_OPENED ──► CUSTOMER_CONFIRMED ──► PROCESSING ──┬──► APPROVED
                                                                                        ├──► REJECTED
                                                                                        └──► FAILED

Any non-terminal state can transition to: CANCELLED (merchant), EXPIRED (system), FAILED (system)
Terminal states: APPROVED, REJECTED, FAILED, EXPIRED, CANCELLED
```

Transitions enforced by `TransactionStatus.canTransitionTo()` and `TransactionService.transitionStatus()`. Every transition is recorded in the embedded `statusHistory` list with actor, reason, and timestamp.

### API Endpoints

| Method | Path | Auth | Consumer | HTTP Status |
|--------|------|------|----------|-------------|
| POST | `/api/v1/transactions/initiate` | X-API-Key | Retailer App | 201 Created |
| GET | `/api/v1/transactions/{id}/status` | X-API-Key | Retailer App (poll 500ms) | 200 |
| POST | `/api/v1/transactions/{id}/cancel` | X-API-Key | Retailer App | 200 |
| GET | `/api/v1/payment/{ref}` | None | Customer App | 200 |
| POST | `/api/v1/payment/{ref}/confirm` | None | Customer App | 202 Accepted |
| GET | `/api/v1/payment/{ref}/result` | None | Customer App (poll 1s) | 200 |

### Security

- **ApiKeyAuthFilter** — `OncePerRequestFilter` applied only to `/api/v1/transactions/**`
- Reads `X-API-Key` header → looks up `MerchantRepository.findByApiKeyHash(key)` → sets `merchantId` request attribute
- Customer endpoints (`/api/v1/payment/**`) are completely public
- CORS allows `localhost:5173` and `localhost:5174`

### Key Configuration (`application.yml`)

```yaml
server.port: 8080
spring.data.mongodb.uri: mongodb://localhost:27017/sepa_payments
app.transaction.ttl-minutes: 5
app.qr.base-url: http://localhost:5174/pay
app.bank-mock.base-url: http://localhost:8081
```

---

## Bank Mock (`bank-mock/`)

### Package: `ro.accesa.payment.bankmock`

```
ro.accesa.payment.bankmock/
├── BankMockApplication.java           @SpringBootApplication
├── controller/
│   └── BankMockController.java        POST /bank-mock/sepa-instant/initiate
├── dto/
│   ├── Pain001Request.java            messageId, debtor, creditor, amount, remittanceInfo
│   └── BankResponse.java              messageId, bankReference, status, statusReason
└── service/
    └── SepaInstantSimulatorService.java
```

### Simulator Logic

- Delay: 200-800ms random (ThreadLocalRandom)
- Decision rules:
  - IBAN ends `9999` → RJCT / INSUFFICIENT_FUNDS
  - IBAN ends `8888` → RJCT / ACCOUNT_BLOCKED
  - Amount > 15,000 → RJCT / AMOUNT_EXCEEDS_LIMIT
  - Otherwise → ACCP with UUID bankReference

### Pain001Request Structure

```json
{
  "messageId": "MSG-TXN-...-uuid8",
  "creationDateTime": "2026-03-31T...",
  "debtor": { "name": "...", "iban": "...", "bic": "..." },
  "creditor": { "name": "...", "iban": "...", "bic": "..." },
  "amount": { "value": 47.93, "currency": "EUR" },
  "remittanceInformation": "Lidl Purchase #8842"
}
```

---

## Retailer App (`retailer-app/`)

**Stack:** React 19, TypeScript, Vite, Axios, qrcode.react

### Component Architecture

```
App.tsx (state machine: screen = "new-payment" | "qr-display" | "result")
├── NewPayment.tsx      amount input, description, "Pay with SEPA Instant" button
├── QRDisplay.tsx       QRCodeSVG, polls every 500ms, live status text, cancel button
└── Result.tsx          success/failure display, "New Payment" button
```

### API Client (`src/api/paymentApi.ts`)

- Axios instance with `X-API-Key: sk_test_lidl_001` header
- `initiatePayment(amount, description)` → POST /api/v1/transactions/initiate
- `getTransactionStatus(id)` → GET /api/v1/transactions/{id}/status
- `cancelTransaction(id)` → POST /api/v1/transactions/{id}/cancel

### Polling Behavior (QRDisplay)

- Interval: 500ms via setInterval
- Status text mapping:
  - `CUSTOMER_OPENED` → "Customer scanned QR..."
  - `CUSTOMER_CONFIRMED` → "Customer confirming..."
  - `PROCESSING` → "Processing with bank..."
- Stops on terminal states: APPROVED, REJECTED, FAILED, EXPIRED, CANCELLED

### Vite Config

- Proxy: `/api` → `http://localhost:8080`
- Production: Nginx serves static build, proxies `/api` to `payment-backend:8080`

---

## Customer App (`customer-app/`)

**Stack:** React 19, TypeScript, Vite, Axios, React Router DOM

### Component Architecture

```
App.tsx (BrowserRouter)
└── /pay/:ref → PayPage.tsx (state machine: loading → details → confirm → processing → result)
    ├── PaymentDetails.tsx    merchant name, amount, description, "Proceed to Pay"
    ├── ConfirmPayment.tsx    IBAN input (auto-format, mod-97 validation), consent checkbox
    └── PaymentResult.tsx     spinner while processing, success/failure display
```

### API Client (`src/api/paymentApi.ts`)

- Axios instance, no API key (public endpoints)
- `getPaymentDetails(ref)` → GET /api/v1/payment/{ref}
- `confirmPayment(ref, data)` → POST /api/v1/payment/{ref}/confirm
- `getPaymentResult(ref)` → GET /api/v1/payment/{ref}/result

### IBAN Validator (`src/utils/ibanValidator.ts`)

- Country length map: RO=24, DE=22, FR=27, GB=22, IT=27, ES=24, NL=18, BE=16, AT=20, PT=25
- Validation: strip spaces, uppercase, check length, **mod-97 checksum** (ISO 13616)
- Formatting: groups of 4 characters separated by spaces

### Polling Behavior (PayPage)

- Interval: 1000ms via setTimeout chain
- Max poll time: 30 seconds
- Terminal states: APPROVED, REJECTED, FAILED, EXPIRED

### Vite Config

- Port: 5174
- Proxy: `/api` → `http://localhost:8080`
- Production: Nginx serves static build, proxies `/api` to `payment-backend:8080`

---

## Data Flow: Happy Path

```
Retailer App                    Backend                         Customer App              Bank Mock
     │                            │                                  │                       │
     │ POST /transactions/initiate│                                  │                       │
     │───────────────────────────►│ save txn (QR_GENERATED)          │                       │
     │◄─── 201 {qrPayload, id}   │                                  │                       │
     │                            │                                  │                       │
     │ [render QR code]           │                                  │                       │
     │ [poll /status every 500ms] │                                  │                       │
     │                            │     GET /payment/{ref}           │                       │
     │                            │◄─────────────────────────────────│ [scan QR]             │
     │                            │ → CUSTOMER_OPENED                │                       │
     │                            │──────────────────────────────────►│ [show details]       │
     │                            │                                  │                       │
     │ poll → CUSTOMER_OPENED     │     POST /payment/{ref}/confirm  │                       │
     │                            │◄─────────────────────────────────│ [enter IBAN, consent] │
     │                            │ → CUSTOMER_CONFIRMED → PROCESSING│                       │
     │                            │                                  │                       │
     │                            │ POST /bank-mock/sepa-instant/initiate                    │
     │                            │─────────────────────────────────────────────────────────►│
     │                            │                                  │          [200-800ms]  │
     │                            │◄──────── {status: ACCP} ────────────────────────────────│
     │                            │ → APPROVED                       │                       │
     │                            │                                  │                       │
     │ poll → APPROVED            │     GET /payment/{ref}/result    │                       │
     │ [show success]             │◄─────────────────────────────────│ [poll every 1s]       │
     │                            │──────────────────────────────────►│ [show success]       │
```

---

## MongoDB Collections

### `merchants` (seeded via `mongo-init/init-mongo.js`)

| Field | Test Value |
|-------|------------|
| merchantCode | LIDL_RO_0001 |
| name | Lidl Romania - Terminal POS #1 |
| iban | RO49AAAA1B31007593840000 |
| bic | ABOROBU2XXX |
| apiKeyHash | sk_test_lidl_001 |
| active | true |

### `transactions` (created at runtime)

Document includes: transactionRef, merchantId, amount, currency, description, status, creditor (embedded), debtor (embedded), bankIntegration (embedded), statusHistory (embedded list), qrPayload, timestamps, expiresAt.

Indexed: `transactionRef` (unique).

---

## Infrastructure

### Docker Compose Services

| Service | Image | Port | Depends On |
|---------|-------|------|------------|
| mongo | mongo:7 | 27017 | — |
| payment-backend | ./payment-backend (multi-stage Maven+JRE) | 8080 | mongo (healthy), bank-mock |
| bank-mock | ./bank-mock (multi-stage Maven+JRE) | 8081 | — |
| retailer-app | ./retailer-app (multi-stage npm+nginx) | 5173→80 | payment-backend |
| customer-app | ./customer-app (multi-stage npm+nginx) | 5174→80 | payment-backend |

### Key Configuration Values

| Parameter | Value |
|-----------|-------|
| Transaction TTL | 5 minutes |
| Ref format | TXN-YYYYMMDD-XXXXXX |
| Expiration check interval | 10 seconds |
| Retailer poll interval | 500ms |
| Customer poll interval | 1000ms |
| Customer max poll time | 30 seconds |
| Bank mock delay | 200-800ms |
| Bank mock max amount | 15,000 EUR |
| Test API key | sk_test_lidl_001 |
