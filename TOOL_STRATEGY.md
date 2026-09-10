# TOOL_STRATEGY.md

Assessment deliverable documenting how Claude Code's features were used across this session, how
I'd apply specific capabilities to common scenarios, and — as required — concrete instances where
Claude Code's output fell short and how that was caught and fixed.

## 1. Feature Usage Log

| Feature Used | Why this feature? | Result/Observation |
|---|---|---|
| `CLAUDE.md` (project standards file) | Needed a durable, project-wide source of truth (layered architecture, ORM-only, auth, logging, testing standards) that every prompt in the session would be checked against, instead of re-explaining conventions each time. | Shaped essentially every design decision made across all features. Also directly exposed a prompt/stack mismatch: a later prompt asked to "use Zod or similar" and "document with JSDoc" — both JS/TS-only tools — which `CLAUDE.md`'s Java/Spring stack made obviously inapplicable, so Jakarta Bean Validation and Javadoc were substituted instead of followed literally. |
| Plan Mode (`ExitPlanMode` approval gate) | Both the JWT/security refactor and the Expense Splitting feature were large, multi-file, architecturally significant changes — exactly the case where reviewing a plan before code exists is cheaper than reviewing 14+ files after the fact. | Caught a real design bug (see Limitations #1) before any code was written, and gave an explicit approval checkpoint rather than silently committing to a large design. |
| `AskUserQuestion` | Some decisions were genuinely ambiguous and unresolvable by reading code — e.g., whether to build a placeholder auth check or a full Spring Security + JWT stack, and whether to auto-generate JUnit tests now or defer them. | Got explicit, recorded decisions (full Spring Security, JWT over sessions, add a `User` entity, include tests now) instead of guessing and risking a large rewrite later. |
| `Agent` (background Plan-type subagent) | Used to delegate deep, narrow design validation — stress-testing the balance-netting algorithm's arithmetic and the security architecture's file layout — without bloating the main session's context with that scratch work. | The subagent caught a subtle JPA fetch-join bug in my own draft query before implementation (see Limitations #1) and produced exact, hand-verifiable rounding examples for the equal-split algorithm. |
| `Bash` (git inspection + build attempts) | Needed to verify claims against ground truth rather than asserting from memory — e.g., "were any manual corrections made to generated code?" and "does this project actually compile?" | Confirmed via `git log`/`git diff` that no manual edits existed anywhere in the repo's history (an honest, checked disclosure, not an assumption). Also confirmed, repeatedly, that this sandbox's Maven installation is a broken source checkout — a real, disclosed limitation on verifying any of the generated code actually builds. |
| `Read`/`Write`/`Edit` (core file tools) | The primary mechanism for generating and modifying all ~30+ source, test, and documentation files across the session. | Fast and direct, but with zero compiler feedback loop given the broken Maven install — every file's correctness had to be manually traced rather than confirmed by an actual build, which is the root cause behind Limitations #1 and #2 below. |

## 2. Scenario Responses

**Understanding complexity (500-line unfamiliar function):** I'd use the `Explore` subagent (or a general-purpose `Agent` call) to read the function and its call sites and return a summarized control-flow trace with `file:line` references, rather than pasting the whole function into the main conversation. This keeps the expensive reading/searching work out of my own context window while still getting a grounded, citation-backed explanation before I touch anything.

**Refactoring 10 files to a new logging library:** I'd open Plan Mode first to scope the migration (confirm the target API, identify every call-site pattern) and get it approved before editing anything, then use `Grep` to enumerate every call site across the 10 files and `Edit` with `replace_all` where the substitution pattern is uniform. After the edits, I'd run the existing test suite via `Bash` to confirm nothing broke — Plan Mode de-risks the "10 files at once" blast radius, and the test run is the actual verification, not just a read-through.

**Regex edge-case testing:** Rather than eyeballing the pattern and reasoning about it in prose, I'd have Claude Code write a tiny throwaway script and run it via the `Bash` tool against a list of concrete edge-case inputs, printing pass/fail per case. Regex correctness is exactly the kind of thing that should be verified empirically, not asserted from a static read.

**CI/CD integration with no human interaction:** This calls for Claude Code's non-interactive/headless mode (`claude -p` with a fixed `--output-format`) invoked as a pipeline step, combined with permission rules pre-configured in `.claude/settings.json` so it never blocks on an approval prompt. This is a fundamentally different mode from the interactive session used throughout this case study, which assumes a human is present to approve plan/tool-use gates.

**Security review of a teammate's AI-generated auth module:** I'd invoke the dedicated `security-review` skill rather than an ad hoc "check this for bugs" prompt — it's purpose-built to run a structured security pass over pending changes and would systematically surface exactly the class of issue found in this session's own original Transaction module (an IDOR from trusting a caller-supplied `userId`), instead of relying on whatever the model happens to notice unprompted.

**Cross-developer/cross-session consistency:** This is precisely what a checked-in `CLAUDE.md` is for — this session itself demonstrated it, since project standards (layered architecture, ORM-only, JWT auth pattern, slf4j logging, JUnit coverage) were loaded automatically every turn without being re-typed, and every generated file was checked against it. Any developer running Claude Code against this same repo inherits the same binding conventions for free.

**Context degrading after 45 minutes:** I'd use `/compact` to summarize and compress the conversation at a natural checkpoint (e.g., right after a feature lands and is committed), preserving key facts while discarding exploratory scratch work — rather than waiting for the harness's automatic summarization, which triggers reactively rather than at a moment I control. If the task has fully pivoted to something unrelated, `/clear` is the better call since there's no continuity worth preserving.

**Auto-running tests on every file write:** This needs a `PostToolUse` hook in `.claude/settings.json` bound to the `Write`/`Edit` tool events, shelling out to the test runner deterministically. This is a hooks-based automation problem, not a prompting problem — hooks are executed by the harness itself regardless of what the model decides to do in a given turn, which is the reliability guarantee "please remember to run tests" prompting can't give.

## 3. Limitations Encountered

Three real instances from this session, as required — this section is not "zero limitations."

### Instance 1 — A real bug in my own draft design (JPQL fetch-join), caught before implementation

- **Prompt:** "Implement the 'Expense Splitting' feature on top of the existing Transaction module... 2. Balance Calculation Service... Compute net balances..."
- **What went wrong:** My first-draft repository query for fetching a user's shared expenses was
  `LEFT JOIN FETCH se.participants p WHERE se.creatorId = :userId OR p.userId = :userId`. This is a
  genuine, subtle JPA correctness bug: filtering directly on a fetch-joined collection in the
  `WHERE` clause silently returns only the matching child rows, not their siblings — which would
  have **silently corrupted the balance-netting calculation** (missing debt edges) with no
  exception thrown anywhere. It would have looked completely correct on a quick read.
- **How I detected it:** Not by running it — I couldn't (see Instance 2/3's shared root cause).
  Before writing any code, I delegated the design to a background `Agent` (Plan-type subagent)
  specifically briefed to stress-test the query and the netting algorithm; it identified the bug
  and specified the fix (filter via an `IN`/subquery, fetch unconditionally).
- **How I remediated it:** Adopted the corrected `SharedExpenseRepository.findInvolvingUser` query
  in the plan before any implementation happened — the buggy version was never actually written to
  a file.
- **What I'd do differently:** This bug would not have been caught by the Mockito-based unit tests
  I later wrote for `SharedExpenseServiceTest`, since the repository is mocked there — only a real
  `@SpringBootTest` against a live H2 database would have exercised the actual JPQL. I never got
  to run that integration test (Maven is broken in this sandbox). Next time, getting a working
  build/test-execution loop in place would be a precondition before generating a multi-file
  feature, rather than treating "I can't run this" as an acceptable ongoing constraint to just
  keep disclosing.

### Instance 2 — A self-caught but execution-unverified test-wiring mistake

- **Prompt:** "Refactor the code to match the standards in `CLAUDE.md`... Implement a layered
  architecture... authorization... structured logging."
- **What went wrong:** My first draft of `AuthControllerTest` and `TransactionControllerTest`
  combined `@WebMvcTest(Controller.class)` with an explicit
  `@ContextConfiguration(classes = {Controller.class, GlobalExceptionHandler.class})` on the same
  class. This is a known Spring Test footgun: an explicit `@ContextConfiguration(classes=...)` can
  override `@WebMvcTest`'s automatic slice-scanning behavior, potentially dropping
  auto-configuration the slice normally provides — a bug that wouldn't be obvious from reading the
  annotations, only from watching the test actually fail to boot its context.
- **How I detected it:** By chance, on a self-review re-read of the test files before considering
  that turn's implementation complete — not by running the tests, since Maven doesn't work here.
- **How I remediated it:** Removed the redundant `@ContextConfiguration` annotations from both
  files via a follow-up `Edit` in the same turn, relying on `@WebMvcTest`'s built-in detection of
  `@RestControllerAdvice` beans instead.
- **What I'd do differently:** This was only caught because I happened to re-read the files
  closely; a similarly subtle Spring-wiring mistake could easily have shipped undetected, since
  there is no compiler or test runner in this loop to force the issue to surface. I should
  explicitly flag lower confidence on any Spring-context-wiring detail specifically *because* it's
  untested, rather than presenting test files with the same confidence as logic I can trace by hand.

### Instance 3 — A business-logic fairness gap I didn't self-detect until asked to adversarially review my own work

- **Prompt:** Same Expense Splitting prompt (equal-split requirement): "Implement logic to handle
  both equal and custom splits."
- **What went wrong:** My equal-split implementation (largest-remainder method) is mathematically
  correct — shares always sum exactly to the total — but has an undisclosed bias: leftover cents
  are always assigned to whichever participants happen to be listed **first**, which in practice is
  very often the expense's creator, since callers naturally list themselves first. Over many
  expenses, the same person would systematically absorb the rounding difference. I did not raise
  this as a concern during the implementation turn itself.
- **How I detected it:** I didn't, proactively — it only surfaced later, when a *different* prompt
  explicitly asked me to produce a PR summary including an adversarial "peer review simulation."
  Without that specific later prompt forcing a self-critique, this would have shipped silently as
  a "correct" implementation.
- **How I remediated it:** No code was changed — "fair" rounding is a product decision, not a pure
  correctness bug, so it was disclosed as a named risk/trade-off in `PR_SUMMARY.md` with a
  recommendation to get explicit product sign-off on the rounding policy, rather than me picking a
  different arbitrary rule unilaterally.
- **What I'd do differently:** Business-fairness questions like this should be part of the
  standard design-validation pass at implementation time (e.g., an explicit prompt to the design
  subagent: "does this rule introduce any systemic bias across repeated use?"), not something that
  only gets surfaced days later because a separate prompt happened to ask for adversarial
  self-review. Correctness review and fairness/business review are different lenses, and I only
  applied the second one when specifically told to.
