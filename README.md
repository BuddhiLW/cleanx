# cleanx

[![Clojars Project](https://img.shields.io/clojars/v/io.github.buddhilw/cleanx.svg)](https://clojars.org/io.github.buddhilw/cleanx)
[![release](https://github.com/BuddhiLW/cleanx/actions/workflows/release.yml/badge.svg)](https://github.com/BuddhiLW/cleanx/actions/workflows/release.yml)

Local opsec auditor. Scans a filesystem tree for leaked secrets and
credential-file permission violations, and wipes the local data AI tooling
leaves behind. Emits structured reports with concrete remedy commands.

Written in Clojure on the `hive-*` libraries (`hive-dsl`, `hive-weave`,
`hive-test`) from the [hive-agi](https://github.com/hive-agi) ecosystem.

## Install

```clojure no-run
;; deps.edn
io.github.buddhilw/cleanx {:mvn/version "RELEASE"}
```

Replace `RELEASE` with the version on the Clojars badge. Run it straight from
Clojars without cloning:

```bash
clojure -Sdeps '{:deps {io.github.buddhilw/cleanx {:mvn/version "RELEASE"}}}' \
  -M -m cleanx.core scan ~/PP/some-project
```

## What it does

**`scan`**

- Secret detection via a port of the [gitleaks](https://github.com/gitleaks/gitleaks)
  rule set (vendored from upstream at a pinned commit, MIT), shipped inside the jar.
- Shannon-entropy gate to cut false positives.
- Filesystem permission audit against a table of expected modes for well-known
  sensitive paths (`~/.ssh/*`, `~/.gnupg/*`, `**/kubeconfig*`, `**/*.pem`, …).
- Markdown, JSON or EDN reports; each finding carries severity, a redacted
  snippet and a copyable remedy.
- `--rules FILE` swaps in any gitleaks-format rules file.

**`wipe`**

- Removes local history and caches left by AI tools: `claude`, `aider`,
  `ollama`.
- `shell` keeps `~/.bash_history` / `~/.zsh_history` but strips the lines
  that match a secret rule.
- Dry run by default; `--apply` acts, after a tar.zst backup unless
  `--no-backup`.

**`report`** re-renders a saved EDN scan result.

Not yet:

- No live verification of found secrets (no outbound HTTP).
- No `git log` history walking (working tree only).

## Usage

From a checkout, the `bb` tasks wrap `clojure -M -m cleanx.core`:

```bash
bb scan ~/PP/some-project          # report to ./cleanx-report-<ts>.md
bb scan ~ --out /tmp/home.md       # audit the whole home directory
bb scan . --format json            # JSON report
bb wipe                            # dry run over every tool
bb wipe claude aider --apply       # back up, then delete
bb test                            # run the test suite
bb repl                            # nREPL on :7921
```

`scan` flags: `--out`, `--format md|json|edn`, `--concurrency` (8),
`--timeout-ms` (10000), `--max-size` (2 MiB), `--rules`. Pass `--help` to any
subcommand for the full list.

## Design

`cli` → `scan/engine` (parallel file walk via `hive-weave` `bounded-pmap`) →
findings → `report/markdown` or `report/json`. `wipe/engine` plans per tool,
then applies with an optional backup. All I/O returns a `hive-dsl.result`
Result; errors are collected, not thrown.

## Releases

Every push to `main` that touches `src/`, `resources/`, `test/`, `deps.edn`,
`version.edn` or the workflow runs the suite, bumps the patch version, tags
`v<VERSION>` and deploys to Clojars ([hive-build](https://github.com/hive-agi/hive-build)).
Documentation-only commits do not release. CI commits `VERSION` and
`CHANGELOG.md`, so pull before your next change.

## Non-goals

- No attempt to compete with gitleaks, trufflehog, or detect-secrets on
  breadth. cleanx is a personal opsec tool, not a CI gate (yet).
- No AGPL-licensed code reuse (rules out trufflehog internals).

## License

Source: EPL-1.0 ([LICENSE](LICENSE)).
Vendored data: `resources/rules/gitleaks.toml` is MIT, upstream notice preserved
in `resources/rules/NOTICE`.
