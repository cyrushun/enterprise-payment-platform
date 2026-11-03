# ADR-0005: Java 21 and Spring Boot 4 Baseline

- Status: Accepted
- Date: 2026-11-01

## Context

The project needs a modern, supported Java LTS baseline and an enterprise application framework.

## Decision

Use Java 21 LTS, Spring Boot 4.0.0, and Maven for the initial repository baseline. Keep versions centrally managed in the Maven parent and dependency properties.

## Consequences

Positive:
- Modern Java language/runtime baseline with broad ecosystem support.
- Spring Boot dependency management simplifies compatible dependency versions.
- Maven provides a familiar, reproducible build.

Negative:
- Spring Boot 4 dependency compatibility must be checked when adding third-party libraries.
- Upgrade versions deliberately and rerun all tests.
