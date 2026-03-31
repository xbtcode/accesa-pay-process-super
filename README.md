# SEPA Instant Payment System

A full-stack demo system for SEPA Instant Credit Transfer payments at retail point-of-sale terminals.

## Architecture

| Component | Technology | Port |
|---|---|---|
| Payment Backend | Spring Boot 3, Java 17, MongoDB | 8080 |
| Mock Bank API | Spring Boot 3, Java 17 | 8081 |
| Retailer App (POS) | React 18, TypeScript, Vite | 5173 |
| Customer Web App | React 18, TypeScript, Vite | 5174 |
| MongoDB | MongoDB 7 | 27017 |

## Quick Start

### With Docker Compose

```bash
docker-compose up --build
```

### Local Development

1. Start MongoDB:
```bash
docker-compose up mongo
```

2. Start the Bank Mock:
```bash
cd bank-mock && mvn spring-boot:run
```

3. Start the Payment Backend:
```bash
cd payment-backend && mvn spring-boot:run
```

4. Start the Retailer App:
```bash
cd retailer-app && npm run dev
```

5. Start the Customer App:
```bash
cd customer-app && npm run dev
```

## Payment Flow

1. Cashier enters amount on Retailer App and clicks "Pay with SEPA Instant"
2. QR code is displayed on the POS terminal
3. Customer scans QR code with their phone, opening the Customer Web App
4. Customer enters their IBAN and confirms payment
5. Backend submits SEPA pain.001 to Mock Bank
6. Both terminal and customer see the result (approved/rejected)

## API Endpoints

| Method | Endpoint | Consumer |
|---|---|---|
| POST | `/api/v1/transactions/initiate` | Retailer App |
| GET | `/api/v1/transactions/{id}/status` | Retailer App |
| POST | `/api/v1/transactions/{id}/cancel` | Retailer App |
| GET | `/api/v1/payment/{ref}` | Customer App |
| POST | `/api/v1/payment/{ref}/confirm` | Customer App |
| GET | `/api/v1/payment/{ref}/result` | Customer App |
| POST | `/bank-mock/sepa-instant/initiate` | Internal |

## Test API Key

Merchant API key for testing: `sk_test_lidl_001`

Pass it as `X-API-Key` header for merchant endpoints.

## Mock Bank Rejection Rules

- IBAN ending in `9999` → INSUFFICIENT_FUNDS
- IBAN ending in `8888` → ACCOUNT_BLOCKED
- Amount > 15,000 EUR → AMOUNT_EXCEEDS_LIMIT
- Otherwise → Approved
