# Status: researcher
Date: 2026-03-31
Task: Research TDD principles introduction for SEPA Instant Payment project
Result: SUCCESS
Output: memory/research/2026-03-31-tdd-principles-introduction.md
Notes: All four services analyzed. Zero tests exist today. Backend already has spring-boot-starter-test (JUnit 5 + Mockito + AssertJ). Frontend apps need Vitest + Testing Library + MSW added. Research includes prioritized test targets, concrete TDD examples for all key components, framework recommendations, and a 10-day phased implementation plan. Key refactoring considerations identified: Instant.now() coupling, Thread.sleep in bank-mock, RestClient testability.
