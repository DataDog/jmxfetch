---
name: prepare-release
description: Prepares a jmxfetch release by bumping the version in pom.xml, README.md, and test.yml, and populating the CHANGELOG.md entry. Pass the new version as the argument (e.g. /prepare-release 0.52.0).
allowed-tools: Read Edit Bash(git log *) Bash(git diff *) Bash(git add *) Bash(git commit *)
argument-hint: <new-version>
---

You are preparing a jmxfetch release. The new version is: **$ARGUMENTS**

## Step 1 — Gather current state

Read these four files to understand what needs to change:

1. **`pom.xml`** — find the current `<version>` tag (line ~8). It will look like `X.Y.Z-SNAPSHOT`.
2. **`CHANGELOG.md`** — find the top `# X.Y.Z / TBC` placeholder heading, review its current entries (if any), and note the last set of `[#NNN]:` link definitions and `[@handle]:` contributor lines at the bottom.
3. **`README.md`** — find the jar filename reference in the "Running" section (~line 184).
4. **`.github/workflows/test.yml`** — find the jar filename in the JDK 7 verification step (~line 65).

Also run:
```bash
git log --oneline $(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD)..HEAD
```
to list all commits since the last tag — use this to identify merged PRs and any external contributors that need CHANGELOG entries and link definitions.

## Step 2 — Update all four files

### `pom.xml`
Replace:
```xml
<version>CURRENT-SNAPSHOT</version>
```
With:
```xml
<version>$ARGUMENTS</version>
```

### `README.md`
Replace the jar filename in the "Running" section:
```
jmxfetch-CURRENT-SNAPSHOT-jar-with-dependencies.jar
```
With:
```
jmxfetch-$ARGUMENTS-jar-with-dependencies.jar
```

### `.github/workflows/test.yml`
Replace the jar filename in the JDK 7 `docker run` command:
```
jmxfetch-CURRENT-SNAPSHOT-jar-with-dependencies.jar
```
With:
```
jmxfetch-$ARGUMENTS-jar-with-dependencies.jar
```

### `CHANGELOG.md`

**Heading**: rename the top placeholder:
```
# X.Y.Z / TBC
```
to (using today's date in `YYYY-MM-DD` format):
```
# $ARGUMENTS / YYYY-MM-DD
```

**Entries**: under the new heading, add one line per user-facing merged PR using this format:
```
* [TYPE] Description [#NNN][]
```
Types: `FEATURE`, `IMPROVEMENT`, `BUGFIX`, `SECURITY`, `OTHER`.
Skip CI, infra, dependency bump, and test-only PRs — only include changes that affect users.
For external contributors add ` (Thanks [@handle][])` at the end of the line.

**Link definitions**: add new `[#NNN]:` entries in the sorted block near the bottom of the file (before the `[@handle]:` lines):
```
[#NNN]: https://github.com/DataDog/jmxfetch/issues/NNN
```
Use `/pull/NNN` instead of `/issues/NNN` for PRs that are pull-request-only (no linked issue).

**Contributor handles**: add new `[@handle]:` entries at the very end of the file:
```
[@handle]: https://github.com/handle
```

## Step 3 — Review and commit

Run:
```bash
git diff
```
to review the full changeset, then stage and commit:
```bash
git add pom.xml README.md .github/workflows/test.yml CHANGELOG.md
git commit -m "chore: Preparing $ARGUMENTS release"
```

## Reference: CHANGELOG entry types

| Type | When to use |
|---|---|
| `FEATURE` | New capability exposed to users |
| `IMPROVEMENT` | Enhancement to existing behaviour or new config option |
| `BUGFIX` | Fixes incorrect behaviour |
| `SECURITY` | Dependency bump or fix for a CVE |
| `OTHER` | Everything else worth noting (deprecations, removals) |
