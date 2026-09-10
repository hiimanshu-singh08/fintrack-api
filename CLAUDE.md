# Project Standards
- Technology Stack: [Java, SpringBoot, Maven, H2 database, Hibernate ORM, HTML, CSS, JS]
- Architecture: Layered pattern (Model -> Repository -> Service -> Controller/Route).
- Coding Standards: Follow clean, consitent, readable and maintainable code.
- Security: No raw SQL (use ORM), input validation, authorization check (users can only access own data).
- Testing: Junit. Naming convention: 100% coverage for business logic.
- Logging: Use inbuilt java logging (log4j/ slf4j).
- Constraints: Never modify the database directly in the service layer. Always use the Repository.

