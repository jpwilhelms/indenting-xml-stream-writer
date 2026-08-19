# Contributing

Issues and pull requests are welcome.

## Building

```sh
mvn verify
```

Requires JDK 17+. The build compiles the module, runs the JUnit 5 test suite, and attaches a sources jar.

## Guidelines

- Keep the main library free of runtime dependencies; test-only dependencies (JUnit, Woodstox, JAXB) belong in `<scope>test</scope>`.
- Add or update tests for any behavioral change, especially around mixed-content detection and indentation.
- Match the existing code style (the codebase favors compact, one-method-per-line declarations).

## Reporting bugs

Please include a minimal reproduction: the sequence of `XMLStreamWriter` calls and the expected vs. actual output.
