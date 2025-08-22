# Apps Guidelines

## Object-Oriented Design

Prefer dependency injection and object composition as described in
*Dependency Injection: Principles, Practices, and Patterns* by Mark Seemann and Steven van Deursen.
Follow the guidelines in Yegor Bugayenko's *Elegant Objects* for small, cohesive classes
and constructor-based immutability.

## Testing

- Development must be test-driven.
- Favor integration tests that exercise full API call chains, using a test DB when appropriate.
- Run `make deps` to install required tooling such as `buf` before building or testing.
- If `./gradlew` fails due to a missing `gradle-wrapper.jar`, generate it locally with `gradle wrapper` (do not commit the jar).

