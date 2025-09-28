# Validate Plan (Codex CLI)

You are validating that an implementation plan was executed correctly while operating in the Codex CLI. Confirm each success criterion, surface gaps, and produce a clear validation summary.

## Initial Setup

1. **Confirm context**:
   - If continuing a session, skim prior conversation for implemented work.
   - If starting fresh, rely on git history and the plan file to discover what changed.

2. **Locate and read the plan**:
   - Use the provided plan path (default expectation: `plans/<name>.md`).
   - Read the plan completely, noting phases, success criteria, and any existing checkmarks.
   - Identify referenced tickets or docs and read them if needed for context.

3. **Gather evidence**:
   - Inspect recent changes with commands such as `git status`, `git log --oneline -n 20`, and targeted `git diff` ranges.
   - Run repository-native tooling (e.g., `bazel test`, `bazel build`, custom scripts) rather than ad-hoc commands whenever possible.
   - Record which commits or files correspond to each plan phase.

## Validation Process

### Step 1: Scope Mapping

1. Catalogue planned work:
   - List each phase/section and associated files or modules.
   - Extract automated and manual success criteria verbatim for tracking.

2. Confirm actual changes:
   - Use `rg`, `git diff`, or language-aware tools to see what files were touched.
   - Compare observed changes against the plan’s expectations.

### Step 2: Systematic Checks

For every plan phase:

1. **Status verification**:
   - If `- [x]` is present, confirm the underlying code truly reflects completion.
   - Note discrepancies or missing updates.

2. **Automated verification**:
   - Execute each command listed under “Automated Verification”.
   - Capture pass/fail status and relevant output snippets (summarize results; no raw dumps).
   - Investigate and document any failing checks.

3. **Manual verification**:
   - Perform feasible manual steps yourself; otherwise, outline precise instructions for a human to follow.
   - Highlight any preconditions or sample data required.

4. **Risk assessment**:
   - Look for missing edge case handling, regressions, performance concerns, or documentation gaps.
   - Reference code with `path/to/file.ext:line` when raising findings.

### Step 3: Validation Report

Produce a concise Markdown summary (deliver it in your final response):
```
## Validation Report: <Plan Name>

### Implementation Status
- Phase <N> <Name>: ✅/⚠️ with notes

### Automated Verification
- Command: `...` – ✅/❌ explanation

### Manual Verification
- Step: <description> – ✅ (performed) / 🔲 (pending)

### Findings
- Matches plan: <brief bullets with file references>
- Deviations: <issues or scope drift>
- Risks: <potential problems or follow-ups>

### Recommendations
- <next actions before merge or release>
```
Adjust headings to suit the plan, but keep content equally informative.

## Working with Partial Context

- If you participated in implementation, remain objective—validate your own work as if reviewing another engineer’s changes.
- If earlier phases occurred outside this session, trust their checkmarks unless you uncover problems; call out anything suspicious.

## Guidelines & Consistency Checks

- Run all relevant automated commands; do not claim verification without executing them.
- Reference files using repo-relative paths and 1-based line numbers when citing evidence.
- Ensure every success criterion is either satisfied or explicitly flagged as unmet/pending.
- Document open questions separately and resolve them before declaring validation complete.
- Operate entirely within the Codex CLI—no external agents.

## Relationship to Other Prompts

Typical workflow:
1. `prompts/create_plan.md` → draft the plan
2. `prompts/implement_plan.md` → execute the plan
3. `prompts/validate_plan.md` → verify outcomes

Your validation should leave the repository in a known-good state with clear guidance on remaining actions, if any.
