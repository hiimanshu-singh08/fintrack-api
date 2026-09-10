# PROMPTS.md

This document records the prompt history for the Claude Code session(s) that built the
`fintrack-api` Transaction, Auth, and Expense Splitting features, in chronological order. For
each prompt it notes the Claude Code feature(s) invoked and the prompting/agentic technique(s)
used to fulfill it.

## Session Prompt History

### 1. Initial Transaction module

> Generate a Transaction model and a Transaction service with create, get-by-user, and delete-all functions. Use a database

- **Feature used:** Direct execution (no `/plan`, no subagents) — the project directory was empty, so this ran as a plain generate-from-scratch request using `Write`.
- **Technique:** Scope inference from `CLAUDE.md` project standards (Java/Spring Boot/Maven/H2/Hibernate) to bootstrap a minimal but complete Maven project (`pom.xml`, `application.properties`) around the two files literally requested, since neither could compile/run in isolation without it.

### 2. Code review request (superseded before completion)

> Analyze the code. Perform a detailed code review focusing on: 1. Security (SQL injection, authorization). 2. Architectural flaws (missing layers). 3. Errors. Output the result to `REVIEW.md`. Ensure you include a specific section at the end titled: 'Issues Claude Code Introduced That Required Human Judgment'.

- **Feature used:** **Plan Mode.** A plan file was drafted (findings across security/architecture/errors, plus the requested self-critique section) and `ExitPlanMode` was invoked to request approval.
- **Technique:** Structured critique against a fixed rubric (security / architecture / errors) sourced directly from re-reading the actual files rather than relying on memory of having written them.
- **Outcome:** ⚠️ **Never completed.** The user interrupted before approving the plan (see prompt 3, which arrived in the same turn as the interruption) and `REVIEW.md` was never written. This is called out explicitly here since it could otherwise look like an oversight.

### 3. Full layered refactor + JWT security

> Refactor the code to match the standards in `CLAUDE.md`. Implement a layered architecture (model -\> repository -\> service -\> controller/route). Replace raw database drivers with an ORM. Ensure strict input validation, authorization (users only access own transactions), and structured logging.

- **Feature used:** **Plan Mode** (superseding the interrupted review plan) + **`AskUserQuestion`** (two rounds: authentication mechanism / whether to include tests, then JWT vs. session-based auth / whether to scaffold a `User` entity with register-login endpoints) + a background **`Agent`** call (`subagent_type: Plan`) to design the full Spring Security + layered-architecture approach + **`ExitPlanMode`**.
- **Technique:** **Decomposition and delegation** — rather than designing the security architecture inline, the detailed design (dependency list, file layout, authorization mechanism, validation/logging strategy, test plan, build order) was delegated to a Plan subagent with a large, self-contained brief, then reviewed and copied into the plan file. **Clarifying-question elicitation** was used twice to resolve genuinely ambiguous product decisions (placeholder vs. real auth; JWT vs. sessions) before committing to a design, rather than guessing.
- Implementation followed the approved plan exactly: `pom.xml`/`application.properties` updates, `User`/`Role`/`UserRepository`, DTOs, `GlobalExceptionHandler`, the JWT security package (`JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig`, entry points), `UserService`, `AuthController`, the `Transaction` entity/service refactor, `TransactionController`, and a full JUnit test suite (unit + `@WebMvcTest` + a full `@SpringBootTest` cross-user-isolation integration test).

### 4. Continuation nudge

> Continue from where you left off.

- **Feature used:** Plain continuation prompt — no new tool invoked; the prior task was already complete at that point, so no further action was taken.
- **Technique:** N/A (session-management prompt, not a task prompt).

### 5. Expense Splitting feature

> Implement the 'Expense Splitting' feature on top of the existing Transaction module. Ensure the implementation adheres to the project standards defined in CLAUDE.md.
> 1. Shared Expense Model: creator, description, total amount, split type (equal/custom), list of participants (with share amounts), and created date.
> 2. Balance Calculation Service: equal/custom splits, exact-sum validation, net balance computation.
> 3. API Endpoints: create a shared expense; retrieve all pending balances for a user.
> 4. Architecture & Standards: layered architecture, strict type safety/validation (Zod or similar), existing DB/repository pattern, JSDoc on public methods.

- **Feature used:** **Plan Mode** (reusing the existing plan file) + a background **`Agent`** call (`subagent_type: Plan`) specifically to stress-test the balance-netting algorithm and equal-split rounding before committing to a design + **`ExitPlanMode`**.
- **Technique:** **Consistency-by-pattern-matching** — the design deliberately mirrored the existing `Transaction` module's conventions (plain `Long` FKs, DTO-only validation, structural JWT-based authorization) rather than introducing new patterns. **Explicit assumption substitution**: the prompt asked for "Zod or similar" and "JSDoc," both JS/TS-specific tools that don't exist in this Java/Spring codebase — this was flagged directly rather than silently ignored, substituting Jakarta Bean Validation and Javadoc as the stated equivalents. **Edge-case enumeration via delegation**: the subagent was asked to specifically stress-test BigDecimal exact-sum comparisons, the equal-split remainder-distribution algorithm (worked numeric examples), a JPQL fetch-join correctness bug (filtered fetch-join dropping sibling rows), and the netting algorithm against the exact "$30 one way / $10 the other → net $20" example from the prompt, tracing it from both users' perspectives for symmetry.

### 6. Test suite in `tests/`

> Create a test suite in `tests/` using the test framework defined in `CLAUDE.md`. Include exactly these 6 cases: 1. Equal split among 3 participants. 2. Custom split with valid total. 3. Custom split that fails validation (sum != total). 4. Net balance calculation (multiple expenses). 5. Edge case: Expense with 1 participant. 6. Unauthorized access attempt (test that a user cannot access another user's balance).

- **Feature used:** Direct execution (no Plan Mode this time — the request was concrete and unambiguous) using `Read`/`Bash` (to inspect the existing `tests/` directory and test conventions) then `Edit`/`Write`.
- **Technique:** **Acceptance-criteria-driven generation** — each of the 6 requested cases was mapped to exactly one `@Test` method (no more, no fewer), built as a full HTTP-level acceptance test (`@SpringBootTest` + `MockMvc`) rather than a unit test, so case 6 ("a user cannot access another user's balance") could be demonstrated structurally through real JWTs across two real registered users instead of asserted abstractly. Because `tests/` isn't a Maven-default source root, a `build-helper-maven-plugin` execution was added to `pom.xml` to register it as an additional test source, so the literal folder placement requested would still actually run under `mvn test`. Balance arithmetic for the multi-expense netting case was hand-traced against the real service algorithm before finalizing the assertions.

### 7. This document

> Generate `PROMPTS.md`. Document our session history. List every prompt we used in order. For each, describe the Claude Code feature used (e.g., /plan) and the technique (e.g., decomposition). Include a section called 'Post-Generation Corrections' and document the manual changes I made to your output.

- **Feature used:** `Bash` (`git log`, `git log -p`, `git diff --stat`) to reconstruct what was actually generated versus what ended up committed, before writing this file.
- **Technique:** **Verification-before-claiming** — rather than asserting from memory whether any manual corrections were made, the actual git history was diffed against what was generated in-session (see below) before writing the corrections section.

## Post-Generation Corrections

To answer this honestly rather than guessing, the following was checked directly:

- `git log --stat` across all three commits (`f5c2996`, `804ca1f`, `dc82e6f`) — the file lists and line counts in each commit match, file-for-file, what was generated via `Write`/`Edit` in this session.
- `git log -p -- src/main/resources/application.properties` — the diff introducing the JWT secret/expiration properties matches exactly what was written in-session, with no additional edits layered on top.
- `git diff HEAD -- pom.xml` (the one currently uncommitted change) — matches exactly the `build-helper-maven-plugin` block added in-session for the `tests/` directory, with no further modification.
- The untracked `tests/` directory contains exactly the one file (`SharedExpenseTestSuite.java`) generated in-session — nothing added, removed, or edited on top of it.

**Result: no manual corrections to Claude Code's output were found in the repository.** Every
commit and the current uncommitted diff match what was generated verbatim during this session.

This check is necessarily limited to what's visible in `git` — it cannot detect edits made and
then discarded, changes made outside version control, or configuration/IDE-level tweaks that
were never staged. If corrections were in fact made that aren't reflected here, let me know what
they were and this section will be updated to document them accurately.
