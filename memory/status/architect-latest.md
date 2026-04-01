# Status: architect
Date: 2026-03-31
Task: Design testability refactorings for TDD introduction
Result: SUCCESS
Output: memory/architecture/2026-03-31-tdd-testability-architecture.md
Notes: Designed 7 production code changes (Clock injection in 5 classes, RestClient.Builder in BankIntegrationService, configurable delay in bank-mock) and 14 infrastructure setup items across all 4 services. All changes are minimal -- no new abstractions beyond JDK Clock. Implementation order: backend refactoring first, then backend test infra, then frontend test infra (independent).
