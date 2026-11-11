# Coding standards

What review holds a diff to. The rules live in two places; this file only says which apply.

1. **Every rule in `CLAUDE.md`'s Decisions, Design tokens and Conventions** applies to every diff.
2. **For each feature the diff touches, every rule in its `docs/rules/` file**, once a feature has one.
   A diff touches a feature when it changes that feature's code, tests, wording or data, or code the
   feature calls. When unsure, read the file: a skipped file is a missed rule.

A rule that lint or a test enforces needs no review; the build catches it.
