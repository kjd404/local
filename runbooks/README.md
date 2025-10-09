# Runbooks

This directory contains Markdown runbooks that describe manual or semi-automated
integration scenarios. Author new runbooks using the pattern
`<service>-<scenario>-runbook.md` so files stay discoverable and sortable.

Each runbook should outline the scenario's purpose, prerequisites, execution
steps, validation guidance, and teardown instructions. Keep commands relative to
the repository root and prefer Bazel entry points where available.
