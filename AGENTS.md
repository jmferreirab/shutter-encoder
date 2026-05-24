# Build Validation Rules

After modifying Java code:
- Prefer running `./gradlew classes testClasses`
- Avoid `./gradlew build` unless explicitly requested
- Use `--configuration-cache`
- Use `--parallel`
- Prefer targeted test execution when possible
- Never run integration tests automatically
- If only one package changed, run package-scoped tests

Project:
- Java 25
- Gradle 9.5.1