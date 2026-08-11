# Application Development Rules

## Purpose

These rules define the engineering standards to follow when developing
an application from scratch. The goal is to produce maintainable,
testable, extensible, and production-ready code while avoiding
unnecessary assumptions.

------------------------------------------------------------------------

## 1. Clarify Requirements Before Implementation

-   Do not make assumptions when a requirement is ambiguous, incomplete,
    or open to multiple valid implementations.
-   Ask focused clarification questions before implementing anything
    that materially affects:
    -   Architecture
    -   API contracts
    -   Data models
    -   Business rules
    -   Error handling
    -   Persistence
    -   Concurrency
    -   Security
    -   External integrations
    -   Design-pattern selection
-   Prefer asking one high-value question at a time rather than making
    several speculative assumptions.
-   If the acceptance criteria are incomplete or contradictory, ask for
    clarification before coding.
-   Clearly identify any information that is required to proceed.

### Design-pattern clarification

For every use case:

1.  Identify the design pattern or architectural approach that appears
    appropriate.
2.  Briefly explain why it fits the use case.
3.  If more than one pattern is reasonably applicable, do not
    arbitrarily choose one.
4.  Ask the user to confirm the preferred pattern when the choice
    materially affects the implementation.
5.  Do not force a design pattern where a simpler solution is more
    appropriate.

Examples of patterns that may be considered when relevant:

-   Strategy
-   Factory / Abstract Factory
-   Builder
-   Adapter
-   Facade
-   Template Method
-   Chain of Responsibility
-   Observer
-   State
-   Command
-   Repository
-   Dependency Injection
-   Circuit Breaker
-   Saga
-   Event-driven architecture

The selected pattern must solve an actual problem; patterns must not be
introduced merely for the sake of using patterns.

------------------------------------------------------------------------

## 2. Use Feign Client for HTTP Integrations

-   Use **Feign Client** for outbound HTTP/REST service-to-service
    communication.
-   Do not manually construct HTTP requests when a Feign client can
    represent the integration cleanly.
-   Do not use low-level HTTP clients directly unless there is a
    specific technical requirement that Feign cannot satisfy.
-   Keep external-service communication behind dedicated client
    interfaces.
-   Do not mix HTTP communication logic with business logic.
-   Define clear request and response models for external APIs.
-   Configure:
    -   Connection/read timeouts
    -   Retry behavior where appropriate
    -   Error handling
    -   Authentication
    -   Required headers
    -   Logging/observability
-   Do not blindly retry non-idempotent operations.
-   Handle external-service failures explicitly.

### Client structure

Prefer:

``` text
Business Service
      |
      v
Feign Client Interface
      |
      v
External Service
```

Avoid:

``` text
Business Service
      |
      +--> manually build HTTP request
      +--> construct headers
      +--> parse response
      +--> handle HTTP errors
```

------------------------------------------------------------------------

## 3. Acceptance Criteria Are the Source of Truth

Before considering an implementation complete:

1.  Read and identify every acceptance criterion.
2.  Map each criterion to an implementation behavior.
3.  Implement the required behavior.
4.  Verify the implementation against every criterion.
5.  Identify edge cases that can cause an acceptance criterion to fail.
6.  Do not mark the solution complete if any acceptance criterion has
    not been verified.

### Verification checklist

For each acceptance criterion, verify:

-   Happy path
-   Invalid input
-   Boundary conditions
-   Null/empty values where applicable
-   Failure scenarios
-   External dependency failures where applicable
-   Expected response/output
-   Expected side effects
-   Error handling

If a requirement cannot be verified because required information is
missing, ask for clarification instead of assuming the expected
behavior.

------------------------------------------------------------------------

## 4. Unit Testing

### Default rule

Every functionality must have an appropriate JUnit test.

Tests should cover:

-   Successful execution
-   Failure scenarios
-   Boundary conditions
-   Validation rules
-   Business rules
-   Exception handling
-   Important branching logic
-   External dependency interactions

### Current exception

For **this task only**, JUnit test cases may be skipped if explicitly
requested.

For all future application-development tasks, unit tests should be
included unless the user explicitly asks to skip them.

### Testing principles

-   Follow Arrange / Act / Assert.
-   Keep tests independent.
-   Use descriptive test names.
-   Mock external dependencies rather than calling real external systems
    in unit tests.
-   Verify important interactions with mocked dependencies.
-   Avoid tests that only increase coverage without validating behavior.
-   Tests should validate observable behavior, not implementation
    details unnecessarily.

------------------------------------------------------------------------

## 5. SOLID and Interface Segregation

Follow SOLID principles throughout the implementation.

### Single Responsibility Principle

-   A class should have one clear responsibility.
-   Avoid large classes that handle:
    -   Business logic
    -   Persistence
    -   HTTP communication
    -   Validation
    -   Mapping
    -   Logging
    -   Infrastructure concerns all at once.

### Open/Closed Principle

-   Design components so behavior can be extended without unnecessarily
    modifying stable existing logic.
-   Use Strategy, Factory, or other appropriate patterns when multiple
    interchangeable behaviors are expected.

### Liskov Substitution Principle

-   Implementations of an interface must honor the contract defined by
    that interface.
-   Do not create implementations that require callers to know
    implementation-specific behavior.

### Interface Segregation Principle

-   Prefer small, focused interfaces.
-   Do not create large interfaces containing unrelated operations.
-   A class should depend only on the methods it actually needs.

Prefer:

``` text
PaymentReader
PaymentProcessor
PaymentRefundService
```

over:

``` text
PaymentService
  - read()
  - process()
  - refund()
  - generateReport()
  - sendNotification()
```

### Dependency Inversion Principle

-   Business logic should depend on abstractions rather than concrete
    infrastructure implementations.
-   Inject dependencies instead of creating them directly inside
    business classes.
-   Keep infrastructure concerns behind interfaces where appropriate.

------------------------------------------------------------------------

## 6. Layered Responsibility

Keep responsibilities separated.

A typical structure should be similar to:

``` text
Controller / API Layer
        |
        v
Application / Service Layer
        |
        v
Domain / Business Logic
        |
        +----> Repository
        |
        +----> Feign Client
        |
        +----> Other external interfaces
```

Do not put business logic directly into controllers.

Do not put business logic into repositories or Feign clients.

Do not make domain/business classes responsible for HTTP-specific
details.

------------------------------------------------------------------------

## 7. Dependency Injection

-   Use dependency injection instead of manually creating dependencies.
-   Prefer constructor injection.
-   Avoid unnecessary static dependencies.
-   Avoid service locators and hidden dependencies.
-   Dependencies should be explicit from the class constructor or
    supported injection mechanism.

------------------------------------------------------------------------

## 8. Error Handling

-   Handle errors at the appropriate layer.
-   Do not swallow exceptions.
-   Do not use generic exceptions when a meaningful domain/application
    exception is appropriate.
-   Return consistent API error responses.
-   Do not expose internal implementation details or sensitive
    information in API responses.
-   Preserve the original cause when wrapping exceptions.
-   Define behavior for external-service failures.

------------------------------------------------------------------------

## 9. Validation

Validate inputs at the appropriate boundary.

Validation should cover:

-   Required fields
-   Format constraints
-   Range constraints
-   Business constraints
-   Collection limits
-   Invalid combinations of fields

Do not duplicate the same validation logic across multiple unrelated
classes.

Business validations that affect domain behavior should remain available
independently of the transport layer.

------------------------------------------------------------------------

## 10. Logging and Observability

-   Log meaningful events and failures.
-   Do not log passwords, tokens, secrets, credentials, or sensitive
    payloads.
-   Include useful correlation/request identifiers where available.
-   Use appropriate log levels.
-   Avoid excessive logging inside high-frequency loops.
-   External service calls should be observable through appropriate
    metrics/logging/tracing where required.

------------------------------------------------------------------------

## 11. External Service Resilience

For Feign/external-service calls, consider the following based on the
actual requirements:

-   Timeout
-   Retry
-   Circuit breaker
-   Rate limiting
-   Bulkhead
-   Fallback
-   Idempotency

Do not add resilience mechanisms blindly.

Before introducing retries, confirm whether the operation is idempotent
and whether retrying is safe.

If the correct resilience strategy is unclear and materially affects
behavior, ask for clarification.

------------------------------------------------------------------------

## 12. Data Access

-   Keep persistence logic inside repository/data-access components.
-   Do not place database queries inside controllers.
-   Do not place database-specific logic inside Feign clients.
-   Keep persistence models separate from API models when the separation
    provides meaningful value.
-   Use transactions where required by the business operation.
-   Define behavior for missing records explicitly.

------------------------------------------------------------------------

## 13. API Design

For every API, clearly define:

-   HTTP method
-   Endpoint
-   Request model
-   Response model
-   Validation rules
-   HTTP status codes
-   Error response structure
-   Authentication/authorization requirements
-   Idempotency behavior where applicable

Do not assume unspecified API behavior.

------------------------------------------------------------------------

## 14. Code Quality

-   Prefer readable code over clever code.
-   Keep methods small and focused.
-   Use meaningful names.
-   Avoid magic numbers and magic strings.
-   Extract constants where appropriate.
-   Avoid premature abstraction.
-   Avoid unnecessary inheritance.
-   Prefer composition when appropriate.
-   Remove dead code.
-   Do not duplicate business logic.
-   Keep public APIs intentionally small.

------------------------------------------------------------------------

## 15. Implementation Workflow

For every new functionality, follow this sequence:

``` text
1. Understand requirements
        |
        v
2. Identify acceptance criteria
        |
        v
3. Identify ambiguities
        |
        v
4. Clarify missing information
        |
        v
5. Identify suitable design pattern
        |
        v
6. Confirm pattern if unclear/material
        |
        v
7. Define interfaces/contracts
        |
        v
8. Implement business logic
        |
        v
9. Implement infrastructure integrations
        |
        v
10. Add unit tests
        |
        v
11. Verify against acceptance criteria
        |
        v
12. Review SOLID/design quality
        |
        v
13. Review edge cases and error handling
```

------------------------------------------------------------------------

## 16. Do Not Over-Engineer

-   Do not introduce a design pattern simply because it exists.
-   Do not create interfaces with no meaningful abstraction.
-   Do not create additional layers unless they provide a clear
    responsibility or extension point.
-   Do not add frameworks/libraries without a concrete requirement.
-   Prefer the simplest design that satisfies the requirements and
    acceptance criteria.

------------------------------------------------------------------------

## 17. Final Review Before Delivery

Before delivering the implementation, verify:

-   [ ] All requirements are addressed.
-   [ ] No unresolved ambiguity was silently assumed.
-   [ ] Appropriate design patterns were considered.
-   [ ] Design-pattern choices are justified.
-   [ ] Feign Client is used for HTTP integrations.
-   [ ] Business logic is separated from infrastructure concerns.
-   [ ] SOLID principles are followed.
-   [ ] Interfaces are small and focused.
-   [ ] Error handling is defined.
-   [ ] Validation is implemented.
-   [ ] Edge cases are considered.
-   [ ] JUnit tests cover each functionality, unless explicitly skipped
    for the current task.
-   [ ] Implementation has been checked against every acceptance
    criterion.
-   [ ] No unnecessary abstraction or over-engineering was introduced.
-   [ ] No unsupported assumptions were made.

## Core Rule

**When information is missing, do not guess. Ask. When a design choice
is ambiguous, explain the options and confirm the choice. When
implementation is complete, verify it against the acceptance criteria
and test the behavior.**
