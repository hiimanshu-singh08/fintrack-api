# PR Summary: Expense Splitting Feature Implementation

## Summary
This PR adds an **Expense Splitting** feature (`SharedExpense`/`ExpenseParticipant`) on top of a
**remediated Transaction module**, both following a strict layered architecture
(Model → Repository → Service → Controller) and full JWT-based Spring Security. The Transaction
module started with no controller and no authorization enforcement at all — any caller could pass
an arbitrary `userId`. It was remediated to derive identity exclusively from a verified JWT
principal, move validation onto request DTOs, and add a real `User`/auth flow. Expense Splitting
was then built the same way: a `SharedExpense` (creator, total, split type, participants) with a
`SharedExpenseService` that validates equal/custom splits and computes net pending balances
between users. In a fintech context this matters for **data integrity and trust**: money amounts
must never be attributable to the wrong user, split calculations must be exact to the cent (not
approximately correct), and every balance figure must be reproducible/auditable from the same
repository-backed data rather than ad hoc computation — hence the emphasis on ORM-only access,
structural (not just checked) authorization, and exact `BigDecimal` arithmetic throughout.

## AI Tool Disclosure
* **Claude Code Features Used:** Plan Mode (with `ExitPlanMode` approval gates), `AskUserQuestion`
  for clarifying ambiguous product decisions, background `Agent` calls (`subagent_type: Plan`) to
  design and stress-test the security architecture and the balance-netting/rounding algorithm
  before implementation, and direct execution (no plan) for smaller, unambiguous requests. Full
  prompt-by-prompt breakdown is in [PROMPTS.md](PROMPTS.md).
* **Methodology:**
    * **Plan Mode for multi-file changes:** Yes. Both the Transaction-module refactor (16+ new
      files: security package, DTOs, exception handling, tests) and the Expense Splitting feature
      (14 new files) went through Plan Mode — a written plan was reviewed and approved before any
      code was touched. Single-file or narrowly-scoped requests (the initial Transaction scaffold,
      the `tests/` acceptance suite, this document, `ARCHITECTURE.md`) were executed directly.
    * **`CLAUDE.md` influence:** Directly shaped almost every decision — it mandated the layered
      architecture (so a `TransactionController`/`SharedExpenseController` were added even though
      not explicitly asked for in the first prompt), ORM-only access (no raw SQL anywhere, including
      the balance-lookup JPQL query), "users can only access own data" (implemented structurally via
      JWT principal resolution, not a runtime ownership check), and JUnit as the test framework
      (used even when a later prompt suggested Zod/JSDoc, which don't apply to this Java stack).
    * **Context management:** No `/compact` or `/clear` was explicitly invoked by the user during
      this session. The harness may auto-summarize context as the conversation grows, but that was
      not manually triggered.
* **AI vs. Human Contribution:** ~100% AI-generated. A git-history check (`git log --stat`,
  `git log -p`, `git diff`) across all commits and the current working tree found no edits layered
  on top of what Claude Code generated — see [PROMPTS.md § Post-Generation Corrections](PROMPTS.md).
  This should be read as "no corrections found in git," not as a claim that human review didn't
  happen — it's a disclosure of what's verifiable, not a substitute for review before merge.
* **Overrides:** None found by the same git-history check — no instance where generated code was
  rejected and replaced. This PR has **not yet had a human security/architecture review**; see
  Peer Review Simulation below for issues a reviewer should raise before merging.

## Testing Coverage & Gaps
* **Test Cases Implemented (the 6 required, in `tests/java/.../SharedExpenseTestSuite.java`):**
    1. Equal split among 3 participants (verifies exact-sum + per-participant share).
    2. Custom split with a valid total.
    3. Custom split that fails validation (sum ≠ total) → 400.
    4. Net balance calculation across multiple expenses (A owes B $30, B owes A $10 → net $20,
       verified from both users' perspectives).
    5. Edge case: expense with 1 participant (creator only; whole amount, zero debt).
    6. Unauthorized access attempt: a user cannot fetch another user's balance — proven
       structurally (no endpoint/param accepts a target user id) plus a missing/tampered-token 401.
  Additional coverage exists in `src/test/java`: `SharedExpenseServiceTest` (duplicate participants,
  missing custom share, unknown participant, rounding edge cases), `TransactionServiceTest`,
  `SecurityIntegrationTest`, `JwtUtilTest`, `UserServiceTest`, and controller slice tests.
* **Known Gaps:**
    * **Tests have not actually been run in this environment.** The sandbox's Maven install is a
      broken source checkout (`Could not find or load main class ...Launcher`), confirmed multiple
      times across the session. All test logic was hand-traced against the real algorithms, but
      `mvn test` needs to be run by a human before merge to confirm it actually compiles and passes.
    * No test covers concurrent requests (e.g., two simultaneous shared-expense creations, or a
      transaction delete-all racing a create) — none of the services use optimistic locking.
    * No test exercises pagination/large-N behavior — `getTransactionsByUser` and
      `getBalancesForUser` both load their full result set into memory with no limit.
    * No test covers multi-currency or negative-total edge cases beyond what Bean Validation rejects.

## Risks & Trade-offs
* **Risk/Trade-off:** `SharedExpense.userId`/`Transaction.userId` are plain `Long` columns instead
  of `@ManyToOne User` relationships. This prioritized simplicity and avoided lazy-loading/N+1
  complexity, but it means there is **no database-level referential integrity** between a
  transaction/expense and an actual `User` row — orphaned rows are only prevented by application
  code (`UserRepository.existsById` checks), not a foreign key constraint.
* **Risk/Trade-off:** `getBalancesForUser` recomputes net balances from every historical
  `SharedExpense` on every call rather than maintaining a running ledger. This prioritized
  correctness/simplicity of a first implementation over read-time scalability — it will get slower
  as a user's expense history grows, with no caching layer yet.
* **Risk/Trade-off:** `jwt.secret` has a **committed dev-only default**
  (`application.properties`, overridable via the `JWT_SECRET` env var). This makes local
  development frictionless but is a real risk if a deployment ever runs without that env var set —
  see Peer Review Simulation, Comment 1.

## Self-Review Checklist
- [ ] Code passes all tests? — **Not verified.** Could not run `mvn test` in this environment (see
  Known Gaps); must be confirmed by a human before merge.
- [x] Follows `CLAUDE.md` standards? — Layered architecture, ORM-only access, structured slf4j
  logging, and DTO-based validation are all in place. JUnit coverage % was not measured with a
  coverage tool, so "100% of business logic" is unverified, not just unmet.
- [ ] No hardcoded secrets? — **Not fully true.** `jwt.secret` has a real committed fallback string
  in `application.properties` (dev-only by convention, not by enforcement — see Risks above).
- [x] Input validation applied? — Jakarta Bean Validation on all request DTOs, plus service-layer
  checks for cross-field business rules (exact-sum, duplicate participants) that annotations can't express.
- [x] Documentation updated? — `ARCHITECTURE.md`, `PROMPTS.md`, and this file were added; `README.md`
  is still an empty placeholder and was not filled in as part of this work.

## Peer Review Simulation
* **Comment 1:** `src/main/resources/application.properties:13` - Committing even a "dev-only"
  JWT signing secret as a literal fallback string means it ships in every clone of this repo; if a
  deployment ever forgets to set `JWT_SECRET`, every environment silently signs tokens with the
  same publicly-visible key. - Fail startup fast when `JWT_SECRET` is unset outside a `dev` Spring
  profile, instead of silently falling back.
* **Comment 2:** `SharedExpenseService.getBalancesForUser` - Loads a user's *entire* shared-expense
  history and recomputes the full netting map on every single request, with no pagination or
  caching; this is fine at demo scale but degrades linearly as history grows. - Consider maintaining
  a running per-pair balance (updated on write, in the same transaction as expense creation) instead
  of recomputing on every read, or at minimum cap/paginate `findInvolvingUser`.
* **Comment 3 (The "AI-blind spot"):** `SharedExpenseService.resolveEqualShares` - The largest-remainder
  algorithm correctly makes shares sum exactly to the total, but it always hands the extra
  leftover cent(s) to whichever participants happen to be **first in list order** — which in
  practice is usually the creator, since they're commonly listed first. Over many expenses, the
  same person (e.g., whoever always creates the group's expenses) will systematically absorb the
  rounding difference. This is a business-fairness nuance a math-correctness check doesn't surface:
  the implementation is arithmetically correct but was never validated against what "fair"
  rounding should mean for real users splitting bills repeatedly. - Get explicit product input on
  the rounding policy (e.g., rotate who absorbs the remainder, or split it as evenly as possible
  down to fractional cents in a ledger) rather than leaving list-order as the de facto policy.
