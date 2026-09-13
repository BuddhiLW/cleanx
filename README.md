# cleanx

Local opsec auditor. Scans a filesystem tree for leaked secrets, credential-file
permission violations, and known-risky patterns. Emits a structured report with
concrete remedy commands.

Written in Clojure. CLI via [Babashka](https://babashka.org/). Reuses the
`hive-*` libraries (`hive-dsl`, `hive-system`, `hive-weave`, `hive-test`) from
the [hive-agi](https://github.com/hive-agi) ecosystem.

## Status

**v0.1 — scanner only.** Ships:

- Secret detection via a port of the [gitleaks](https://github.com/gitleaks/gitleaks)
  rule set (229 regex rules, vendored from upstream at a pinned commit, MIT).
- Shannon-entropy gate to cut false positives.
- Filesystem permission audit against a table of expected modes for well-known
  sensitive paths (`~/.ssh/*`, `~/.gnupg/*`, `**/kubeconfig*`, `**/*.pem`, …).
- Markdown + JSON/EDN reports, each finding tagged with severity, redacted
  snippet, and a copyable remedy.

Not yet:

- No live verification (no outbound HTTP).
- No `git log` history walking (working tree only).
- No wipers (planned for v0.2).

## Usage

```bash
bb scan ~/PP/some-project          # scan a tree, write report to ./cleanx-report-<ts>.md
bb scan ~ --out /tmp/home.md       # audit the whole home directory
bb scan . --format json            # JSON to stdout
bb test                            # run the trifecta suite
bb repl                            # nREPL on :7921
```

## Design

Short version: `cli` → `scan/engine` (uses `hive-weave/bounded-pmap`
and `hive-system/IFilesystem`) → findings → `report/markdown` or `report/json`.
All I/O returns a `hive-dsl.result/Result`; errors are collected, not thrown.

## Non-goals

- No attempt to compete with gitleaks, trufflehog, or detect-secrets on
  breadth. cleanx is a personal opsec tool, not a CI gate (yet).
- No AGPL-licensed code reuse (rules out trufflehog internals).

## License

Source: EPL-1.0 (matching the hive ecosystem).
Vendored data: `resources/rules/gitleaks.toml` is MIT, upstream notice preserved
in `resources/rules/NOTICE`.

