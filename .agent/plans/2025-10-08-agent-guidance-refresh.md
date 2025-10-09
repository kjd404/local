# Refresh Agent Guidance Docs

## Overview / Goal
- Perform a coordinated documentation pass so top-level and service-level agent guides reflect the new planning workflow and repo-wide test suite.
- Specifically update `AGENTS.md` (roles + guardrails) and `apps/plutary/AGENTS.md` to drop the README task-list requirement, endorse the `//tests:repo_suite` aggregator, and clarify forthcoming Plutary extensibility.

## Current State
- The restored `AGENTS.md` (from `origin/main`) still requires planners to maintain README checklists (`AGENTS.md:4-9`) and discourages root test aggregators (`AGENTS.md:64-73`).
- `AGENTS.md` lacks any mention of the new `.agent/` workspace or the repo-wide test suite once those are introduced.
- `apps/plutary/AGENTS.md:6-18` implies `plutary.composition` already exists, leading to confusion about missing dependencies.

## Proposed Changes
- [x] **Update Planner & Testing Guidance in `AGENTS.md`**
  - [x] Revise the planner role description to reflect the `.agent/` planning workflow instead of enforcing README task lists.
  - [x] In the Testing & PRs section, replace blanket `bazel test //...` guidance with the newly created `bazel test //tests:repo_suite` command for repo-wide sweeps, while preserving expectations for per-app test runs.
- [x] **Revise Guardrails / Ongoing Tasks**
  - [x] Amend the guardrail that forbids root aggregators to explicitly allow the curated `//tests:repo_suite` target while maintaining the expectation that tests remain colocated with code.
  - [x] Mention the `.agent/` namespace if relevant to planning guardrails.
- [x] **Clarify Plutary Extensibility**
  - [x] In `apps/plutary/AGENTS.md`, adjust the operating-mode bullets to communicate that `plutary.composition` is an upcoming module for injectable collaborators, not an existing dependency.
  - [x] Note that contributors should continue using current injection seams until the module lands.
- [x] **Consistency Check**
  - [x] Ensure terminology for the repo-wide suite and `.agent/` workspace matches other updated docs (plans already created).
  - [x] Proofread for Markdown formatting and check anchors/links after edits.

## Testing & Verification
- [x] Manual review of Markdown rendering.
- [x] Run `rg "bazel test //..." AGENTS.md apps/plutary/AGENTS.md` post-edit to confirm outdated command references are gone.

## Risks & Mitigations
- *Risk:* Divergence between this doc refresh and other concurrent documentation plans.
  - *Mitigation:* Sequence work after the role-doc alignment, Bazel suite creation, and Plutary extensibility clarification plans so language stays synchronized.
- *Risk:* Confusing readers if both repo-wide and per-service commands are mentioned without context.
  - *Mitigation:* Provide short explanations describing when to run the aggregator versus per-app tests.

## Dependencies / Follow-ups
- Depends on: `2025-10-08-role-docs-alignment`, `2025-10-08-bazel-repo-test-suite`, and `2025-10-08-plutary-extensibility-guidance` plans to establish the underlying changes and canonical terminology.
- After completion, validate that the README and other guides echo the same commands to avoid conflicting guidance.

## Completion Notes
- No deviations required; existing docs already referenced parts of the new workflow, and updates now align terminology with the curated repo suite and `.agent/` workspace.
