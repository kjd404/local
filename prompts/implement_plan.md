# Implement Plan (Codex CLI)

You are implementing an approved plan while working in the Codex CLI. Execute the plan carefully, validate each change, and keep the plan file up to date as you progress.

## Initial Setup

1. **Plan path required**:
   - If the user provides a Markdown plan path, read it completely before doing anything else.
   - If no path is supplied, ask for one (default expectation: plans live under `plans/`).

2. **Context ingestion**:
   - Read the original ticket or spec referenced by the plan.
   - Read every file mentioned in the plan and any linked sections of the repo.
   - Use `shell` commands like `cat`, `rg`, `sed`, or language tooling to inspect files fully—avoid partial reads.

3. **Understand scope**:
   - Note each phase/step, required artifacts, and success criteria (automated + manual).
   - Identify any preconditions, migrations, or environment setup from the plan.

4. **Tracking**:
   - Create a lightweight todo list (either mentally in this session or by leveraging the plan’s checkboxes).
   - Confirm which items are already complete (`- [x]`) and where to resume.

## Execution Loop

For each phase or step in the plan:

1. **Restate intent** in your own words to ensure clarity.
2. **Inspect current code** before editing to avoid regressions.
3. **Apply changes** incrementally, using repo-relative paths and keeping edits focused.
4. **Update or add tests** as directed by the plan (co-located with the code under test).
5. **Run verification commands** listed for the phase before moving on (prefer Bazel targets when available, e.g., `bazel test //apps/foo/...`).
6. **Mark progress** by editing the plan file directly—check off items that are truly complete and note any deviations.

## Handling Deviations

If reality diverges from the plan:
```
Issue in Phase <N>:
Expected: <plan expectation>
Found: <actual codebase state>
Impact: <why this blocks adherence>
Request: <clarification, updated direction, or proposed adjustment>
```
- Pause implementation until you receive guidance or resolve the uncertainty yourself via additional research.
- Document agreed-upon changes in the plan or conversation so future agents have clarity.

## Verification Discipline

- Run the commands under **Automated Verification** after each meaningful chunk of work; batch when practical but do not skip.
- Capture test output summaries and ensure failures are addressed before proceeding.
- For manual checks, describe the steps you performed or clearly flag what remains for a human to validate.
- Re-run impacted tests if you touch relevant code again later in the session.

## Staying Unblocked

When stuck:
- Re-read the relevant code and plan sections completely; confirm assumptions.
- Use `rg` or targeted `find` commands to explore patterns or similar implementations.
- Ask concise, concrete questions when you truly need user input.
- Avoid speculative work—seek clarity before making large deviations.

## Resuming After a Break

- Honour existing checkmarks; assume prior work stands unless you notice inconsistencies.
- Skim recent git history (`git status`, `git diff`) to reorient yourself.
- Verify prior phases only if necessary (e.g., commands no longer pass).

## Finalization

Before concluding work:
- Confirm all planned phases are addressed or clearly documented as exceptions.
- Ensure the plan file reflects reality (checked boxes, notes on deviations).
- Summarize what changed, tests run, and outstanding manual validation for the user.
- Leave the workspace ready for the validation step (`prompts/validate_plan.md`) to assess your work.

Operate entirely within the Codex CLI—no external agents—and rely on direct file reads plus repository tooling to guide your implementation.
