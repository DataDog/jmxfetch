---
name: start-dev-cycle
description: Starts a new jmxfetch development cycle by bumping to the next SNAPSHOT version in pom.xml, README.md, and test.yml, and adding a new CHANGELOG.md placeholder. Pass the next snapshot version as the argument (e.g. /start-dev-cycle 0.52.1-SNAPSHOT).
allowed-tools: Read Edit Bash(git add *) Bash(git commit *) Bash(git push *) Bash(gh pr create *)
argument-hint: <next-snapshot-version>
---

You are starting a new jmxfetch development cycle. The new SNAPSHOT version is: **$ARGUMENTS**

## Step 1 — Read current state

Read these four files to confirm the current version references before changing them:

1. **`pom.xml`** — current `<version>` tag (~line 8)
2. **`README.md`** — jar filename in the "Running" section (~line 184)
3. **`.github/workflows/test.yml`** — jar filename in the JDK 7 verification step (~line 65)
4. **`CHANGELOG.md`** — the top heading (first `# X.Y.Z / ...` line)

## Step 2 — Update all four files

### `pom.xml`
Replace the current `<version>` with the new SNAPSHOT:
```xml
<version>$ARGUMENTS</version>
```

### `README.md`
Replace the jar filename in the "Running" section:
```
jmxfetch-$ARGUMENTS-jar-with-dependencies.jar
```

### `.github/workflows/test.yml`
Replace the jar filename in the JDK 7 `docker run` command:
```
jmxfetch-$ARGUMENTS-jar-with-dependencies.jar
```

### `CHANGELOG.md`
Add a new placeholder section at the very top (after `Changelog\n=========`):
```
# X.Y.Z / TBC

```
Where `X.Y.Z` is the version from `$ARGUMENTS` with `-SNAPSHOT` stripped.

## Step 3 — Commit, push and open PR

Stage and commit all four files:
```bash
git add pom.xml README.md .github/workflows/test.yml CHANGELOG.md
git commit -m "chore: Starting dev cycle by returning version to a SNAPSHOT"
```

Push the branch and open a PR against `master`:
```bash
git push -u origin HEAD
gh pr create \
  --title "chore: Starting dev cycle by returning version to a SNAPSHOT" \
  --body "Bumps version to \`$ARGUMENTS\` and adds \`CHANGELOG.md\` placeholder for the next release."
```
