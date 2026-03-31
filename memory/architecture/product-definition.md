# SEPA Instant Payment System — Product Definition

## Overview

A full-stack demo system simulating SEPA Instant Credit Transfer payments at retail point-of-sale terminals. A retailer (Lidl) initiates a payment, displays a QR code, the customer scans it on their phone, enters their IBAN, confirms, and the backend orchestrates the payment through a mock bank simulating SEPA pain.001 processing.

## Components

| Component | Technology | Port |
|---|---|---|
| Payment Backend | Spring Boot 3, Java 17, MongoDB | 8080 |
| Mock Bank API | Spring Boot 3, Java 17 | 8081 |
| Retailer App (POS Terminal) | React 18, TypeScript, Vite | 5173 |
| Customer Web App (Mobile) | React 18, TypeScript, Vite | 5174 |
| MongoDB | MongoDB 7 (Docker) | 27017 |

## Package Base

`ro.accesa.payment` (backend), `ro.accesa.payment.bankmock` (mock bank)

## Domain Model

### Merchant
- merchantCode, name, iban, bic, address, apiKeyHash, active

### PaymentTransaction
- transactionRef (unique, URL-safe), merchantId, amount (BigDecimal), currency (EUR), description
- status (state machine), creditor/debtor info (embedded), bankIntegration info (embedded)
- statusHistory (embedded list for full audit trail)
- TTL: 5 minutes from creation

### Transaction Status State Machine
```
INITIATED → QR_GENERATED → CUSTOMER_OPENED → CUSTOMER_CONFIRMED → PROCESSING → APPROVED/REJECTED
                                                                                  ↗
Any non-terminal state can → CANCELLED (merchant), EXPIRED (system), FAILED (system)
```

Terminal states: APPROVED, REJECTED, FAILED, EXPIRED, CANCELLED

## API Contract (7 endpoints)

### Merchant Endpoints (require X-API-Key header)
1. `POST /api/v1/transactions/initiate` — Create payment, returns QR payload
2. `GET /api/v1/transactions/{id}/status` — Poll status (every 500ms)
3. `POST /api/v1/transactions/{id}/cancel` — Cancel payment

### Customer Endpoints (public)
4. `GET /api/v1/payment/{ref}` — Load payment details (side-effect: status → CUSTOMER_OPENED)
5. `POST /api/v1/payment/{ref}/confirm` — Submit IBAN + consent, triggers bank call
6. `GET /api/v1/payment/{ref}/result` — Poll result (every 1s)

### Internal (Backend → Mock Bank)
7. `POST /bank-mock/sepa-instant/initiate` — pain.001-like payload, returns ACCP/RJCT

## Payment Flow

1. Cashier enters amount on Retailer App → POST initiate → QR code displayed
2. Customer scans QR → opens Customer Web App → sees payment details
3. Customer enters IBAN, gives consent → POST confirm
4. Backend builds pain.001, sends to Mock Bank
5. Mock Bank simulates processing (200-800ms delay), returns ACCP or RJCT
6. Both Retailer App (polling 500ms) and Customer App (polling 1s) show final result

## Mock Bank Rejection Rules
- IBAN ending in `9999` → INSUFFICIENT_FUNDS
- IBAN ending in `8888` → ACCOUNT_BLOCKED
- Amount > 15,000 EUR → AMOUNT_EXCEEDS_LIMIT
- Otherwise → Approved (ACCP)

## Security
- API key authentication for merchant endpoints via `X-API-Key` header
- Customer endpoints are public (accessed via QR scan)
- CORS configured for both frontend origins
- IBAN validation: country-specific length + mod-97 checksum (ISO 13616)

## Infrastructure
- Docker Compose for all services + MongoDB
- MongoDB seed script with test merchant (LIDL_RO_0001, API key: `sk_test_lidl_001`)
- Multi-stage Dockerfiles (Maven+JRE for Java, npm+nginx for React)
- Nginx reverse proxy for frontend → backend API calls in Docker

## Key Design Decisions
- **MongoDB** over SQL: schema flexibility, embedded documents for status history, TTL indexes, JSON-native
- **Separate Mock Bank** service: clean architecture, simulates real bank integration boundary
- **Polling over WebSocket**: simpler to implement/debug, acceptable at 500ms/1s intervals for demo scale
- **transactionRef vs transactionId**: short ref for customer-facing URLs, MongoDB ObjectId for internal use
- **Creditor info copied at initiation**: historical transactions retain original merchant data for audit
- **Synchronous bank call**: acceptable for demo (bank mock responds in <1s); production would use async
- **Scheduled expiration**: every 10s scan for stale transactions, transition to EXPIRED
