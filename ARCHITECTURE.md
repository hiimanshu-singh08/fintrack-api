# Architecture

fintrack-api follows a strict layered architecture (Model → Repository → Service → Controller),
as mandated by `CLAUDE.md`: each layer only calls downward (controllers never touch repositories
directly, services never touch the database directly), keeping business logic, persistence, and
HTTP concerns independently testable and replaceable.

**Transaction** and **SharedExpense** are separate, loosely-coupled modules that both reference
users only by a plain `Long userId`/`creatorId` rather than a JPA relationship — deliberately, since
that id is always server-derived from the JWT principal (never client input), so no relationship
navigation is needed for authorization, only equality filtering. `SharedExpense` doesn't depend on
`Transaction` at all; it models a different concern (money owed *between users*) on the same user
identity, and could later post a settlement as a `Transaction` without either module depending on
the other's internals.

**Hibernate/JPA** (via Spring Data repositories) was chosen over raw SQL/JDBC per `CLAUDE.md`'s
security mandate — it eliminates SQL-injection risk by construction and lets query logic (e.g.
`findByUserId`, the fetch-joined `findInvolvingUser`) scale by adding indexes/derived queries
without hand-written SQL. Repositories are the *only* data-access boundary: this isolates the ORM
and schema from services, so swapping H2 for another RDBMS, or optimizing a query, never requires
touching business logic — a direct scalability and maintainability win as both modules grow.
