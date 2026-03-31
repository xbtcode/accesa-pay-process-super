db = db.getSiblingDB('sepa_payments');

db.merchants.insertOne({
  merchantCode: "LIDL_RO_0001",
  name: "Lidl Romania - Terminal POS #1",
  iban: "RO49AAAA1B31007593840000",
  bic: "ABOROBU2XXX",
  address: {
    street: "Strada Exemplu 10",
    city: "Cluj-Napoca",
    postalCode: "400001",
    country: "RO"
  },
  apiKeyHash: "sk_test_lidl_001",
  active: true,
  createdAt: new Date()
});

print("Seeded merchant: LIDL_RO_0001");
