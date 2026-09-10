# REVIEW.md — Post-Mortem: Transaction Module (commit `f5c2996`, "feat: add unreviewed module")

Senior developer review of the initial, unreviewed AI-generated Transaction module, conducted
before it was remediated into the current layered architecture (commit `804ca1f`).

## 1. Table of Issues

| # | Issue | Location | Severity | Fintech Data-Integrity Impact |
|---|---|---|---|---|
| 1 | No authentication/authorization layer exists anywhere; every service method trusts a caller-supplied `userId` at face value | `TransactionService.createTransaction/getTransactionsByUser/deleteAllByUser` | **High** | A direct IDOR into financial records: any caller could read, fabricate, or **bulk-delete** another user's entire transaction history simply by passing their numeric id. There is no code path that even checks who is calling. |
| 2 | No Controller/Route layer exists at all | missing `controller/` package | **High** | The feature is unreachable over HTTP, and `CLAUDE.md`'s mandated layering (Model → Repo → Service → Controller) is only 3/4 built — there is no boundary left for auth or request validation to attach to once exposed. |
| 3 | No `@SpringBootApplication` entry point anywhere in the module | missing main class | **High** | The application literally cannot start. This was merged as a working "feature" while being non-deployable — a basic runnability check would have caught it in seconds. |
| 4 | Bean-validation annotations (`@NotNull`, `@DecimalMin`, `@Size`) are declared directly on the JPA entity, but nothing in the call path ever invokes `@Valid` against them | `model/Transaction.java` | **Medium** | Looks like input validation is enforced, but isn't reliably: a malformed amount either passes through silently or fails late as a raw `ConstraintViolationException` at Hibernate flush time, not as a clean rejection at the API boundary. Inconsistent guarantees on money fields are unacceptable in a ledger. |
| 5 | No global exception handling | missing | **Medium** | Once any controller is added, an unhandled exception (e.g. #4's `ConstraintViolationException`) would serialize as a raw 500 with a full stack trace by default — an information-disclosure risk on a financial API. |
| 6 | No automated tests despite `CLAUDE.md` mandating JUnit with 100% coverage of business logic | missing `src/test` | **Medium** | No regression safety net on logic that directly moves/represents money; a future change could silently break `deleteAllByUser` or amount handling with nothing to catch it. |
| 7 | Redundant `@Repository` annotation on a `JpaRepository` interface | `repository/TransactionRepository.java` | **Low** | No functional effect (Spring Data auto-detects these) — a pure consistency/code-cleanliness nit. |
| 8 | No correlation/audit logging of *who* accessed *whose* data | `TransactionService` (ad hoc `logger.info` only) | **Low** | Limits forensic traceability of suspicious access patterns after the fact — relevant for any financial audit trail, though moot as a standalone issue until #1 is fixed. |

## 2. Detection Methodology

This was found by **manual code inspection against the `CLAUDE.md` standards checklist**
(layered architecture, ORM-only access, input validation, authorization, logging, testing), not
by failing test cases — because there were no tests to fail. Concretely:

- Read all four source files line-by-line and traced every call path from "how would an HTTP
  request reach this service" — which immediately surfaced that no such path exists (#2, #3).
- Checked whether `@Valid`/`@Validated` was wired anywhere between the (nonexistent) controller
  and the entity's validation annotations — it wasn't, which exposed #4 as validation-in-appearance-only.
- Attempted to actually build/run the module to sanity-check it as a deliverable, which is what
  surfaced #3 (no entry point) rather than assuming the presence of a `pom.xml` meant the app worked.
- Applied a standard IDOR/OWASP mental model ("does this method's caller identity ever get
  checked against the resource it's touching?") to every service method, which is what surfaced #1
  as the most severe finding, not just a style gap.

## 3. Fix Summary

The remediation (commit `804ca1f`) completed the layered architecture end-to-end rather than
patching individual symptoms:

- **Controller layer added**: `TransactionController` now exposes `/transactions` (create/get/delete),
  completing Model → Repository → Service → Controller.
- **Authorization made structural, not checked**: a full Spring Security + stateless JWT stack
  was added (`SecurityConfig`, `JwtAuthenticationFilter`, `JwtUtil`, `AppUserPrincipal`, a `User`
  entity, and `AuthController` for register/login). `TransactionController` and `TransactionService`
  now **only ever** resolve `userId` from the verified JWT principal — no endpoint, path variable,
  or request body field accepts a client-supplied user id anywhere. Fix #1 by construction: there
  is no longer a code path for one user's request to touch another user's data.
- **Validation moved to the API boundary**: validation annotations were stripped off the entity
  and moved onto a dedicated `TransactionRequest` DTO, validated via `@Valid` in the controller —
  fixing #4 by making the previously-decorative annotations actually enforced on every request.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) added to turn validation failures, auth
  failures, not-found, and unexpected exceptions into structured JSON responses instead of raw
  stack traces — fixing #5.
- **`FintrackApiApplication`** entry point added so the service is actually runnable — fixing #3.
- **JUnit test suite added**: unit tests for `TransactionService`/`UserService`, `@WebMvcTest`
  slice tests for both controllers, and a full `@SpringBootTest` integration test that registers
  two real users and asserts one can never see the other's transactions — directly fixing #6 and
  giving #1's fix a regression test, not just a design claim.

## 4. Issues Claude Code Introduced That Required Human Judgment

1. **Trusting a client-supplied `userId` as the security boundary (Issue #1).** The AI was asked
   for "create, get-by-user, and delete-all functions" and delivered exactly that — methods whose
   signatures literally take a `userId` parameter, which satisfies the prompt as written. What it
   didn't — and structurally couldn't — do is question *where that id would come from* once wired
   to a real caller, because the prompt gave it no authentication context to reason about. An AI
   optimizing for "implement the requested functions" has no signal that a parameter named
   `userId` is actually a trust boundary rather than an ordinary input, especially in a codebase
   with no auth system yet. This is exactly the kind of judgment a human with product/security
   context has to supply: recognizing that "any function taking a userId" in a multi-tenant
   financial system is a de facto authorization gate, whether or not it was asked to be one.

2. **Declaring validation without ever verifying it fires (Issue #4).** The generated entity had
   all the *right-looking* annotations (`@NotNull`, `@DecimalMin`, `@Size`) — a pattern match for
   "input validation," which is exactly what was asked for. But the AI never executed an actual
   request through the system (there was no controller to send one through), so it had no feedback
   loop to discover the annotations were inert without `@Valid` wiring. This matters specifically
   for fintech: a check that "looks implemented" on inspection but doesn't actually constrain a
   transaction amount at runtime is more dangerous than an *obviously* missing check, because it
   passes a superficial code review. Only a human tracing the real request path — or a test that
   actually posts a malformed amount and checks the response — would catch that the safety net was
   decorative.

3. **Treating "the files compile" as "the feature is done" (Issue #3).** The module shipped with
   no way to actually start the application, yet was committed as "feat: add unreviewed module" —
   a complete-sounding deliverable. The AI's implicit definition of "done" was bounded by the
   literal artifact requested (a model and a service), not by "is this a working system a human
   could run." This is a recurring failure mode worth calling out explicitly: agentic coding tools
   are very good at satisfying the letter of a prompt and much weaker at independently asking "does
   the *system* this belongs to actually function," unless a human explicitly broadens the
   definition of the task (or later prompts, as happened here, force the gap into view). In a
   fintech setting where "it compiles" is nowhere close to "it's safe to handle money," that gap
   between literal task completion and systemic correctness is precisely why every AI-authored
   change here went through a human-gated review and refactor before being trusted.
