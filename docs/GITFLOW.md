# Basic Gitflow

This project uses a simple flow with these branch types:

- `main`
- `develop`
- `feat/*`
- `fix/*`
- `chore/*`
- `hotfix/*`

The goal is to keep the main line stable, organize the team's work, and ease quick fixes.

## Overview

- `main`: production-ready code only.
- `develop`: main development and integration branch.
- `feat/*`: new features.
- `fix/*`: bug fixes.
- `chore/*`: maintenance, tooling, and infrastructure work (no feature/fix scope).
- `hotfix/*`: urgent fixes that must reach production fast.

> Note: some legacy branches use `feature/*` as an alias for `feat/*`. Prefer `feat/*` for new work; `feature/*` is accepted only for historical compatibility.

Expected flow:

```text
main
  └── develop
       ├── feat/autenticacao-keycloak
       ├── feat/cadastro-consumidor
       └── fix/correcao-validacao-usuario
```

## Branch Rules

### `main`

- Reflects the most stable version of the system.
- No direct commits.
- Merges only from `develop`, once a validated set of deliverables is ready.
- Every change must go through review.

### `develop`

- Base branch for daily development.
- All features and fixes branch off it; a `hotfix/*` may branch from `main` instead (see below).
- Receives merges from `feat/*`, `fix/*`, `chore/*`, and `hotfix/*` after review and validation.
- Must stay functional enough for team integration.

### `feat/*`

For new features.

Naming pattern:

```bash
feat/nome-curto-da-feature
```

Examples:

```bash
feat/autenticacao-keycloak
feat/cadastro-produtor
feat/listagem-admin-usuarios
```

Rules:

- Always branch from `develop`.
- Contain only the feature's scope.
- Open a PR to `develop` when done.

### `fix/*`

For bugs found during development.

Naming pattern:

```bash
fix/nome-curto-do-bug
```

Examples:

```bash
fix/validacao-email-usuario
fix/erro-role-keycloak
fix/endpoint-me
```

Rules:

- Generally branch from `develop`.
- Contain only the fix.
- Open a PR to `develop` when done.

### `chore/*`

For maintenance, tooling, and infrastructure work (no feature or bug-fix scope).

Naming pattern:

```bash
chore/nome-curto-da-tarefa
```

Examples:

```bash
chore/terraform-infra
chore/aws-deploy-env-aware
chore/cleanup
```

Rules:

- Branch from `develop`.
- Contain only the maintenance scope.
- Open a PR to `develop` when done.

### `hotfix/*`

For urgent fixes that must reach production quickly.

Naming pattern:

```bash
hotfix/nome-curto-do-hotfix
```

Examples:

```bash
hotfix/auth
hotfix/sprint_3
```

Rules:

- Branch from `main` (the production line).
- Contain only the urgent fix.
- Open a PR to `main`, then merge the same change back into `develop` so both lines stay in sync.

## Day-to-Day Flow

### 1. Update the local branch

Before starting any task:

```bash
git checkout develop
git pull origin develop
```

### 2. Create a working branch

Feature:

```bash
git checkout -b feat/minha-feature
```

Fix:

```bash
git checkout -b fix/meu-ajuste
```

### 3. Develop with small commits

Example:

```bash
git add .
git commit -m "feat: adiciona integracao inicial com keycloak"
```

Suggested commit prefixes:

- `feat:`
- `fix:`
- `refactor:`
- `test:`
- `docs:`
- `chore:`

### 4. Sync your branch with `develop`

If `develop` moves forward during the task:

```bash
git checkout develop
git pull origin develop
git checkout feat/minha-feature
git merge develop
```

Resolve any conflicts before continuing.

### 5. Push the branch to the remote

```bash
git push -u origin feat/minha-feature
```

or

```bash
git push -u origin fix/meu-ajuste
```

### 6. Open a Pull Request to `develop`

Before opening the PR:

- review your own code
- ensure the branch builds without errors
- validate the task scope
- sync with `develop` if needed

When you open the PR, fill in the structured [PR template](../.github/pull_request_template.md)
(Description, Related task, Type of change, How to validate, Notes, Checklist).

CI runs automatically on every PR to `main` and `develop`
(see [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)) and must pass before merge:

- `mvn test`
- Checkstyle (`mvn checkstyle:check`)
- SpotBugs (`mvn spotbugs:check`)
- a Docker Compose build (`docker compose -f docker-compose.test.yml up --build`)

### 7. Merge into `develop`

After approval:

- merge the branch into `develop`
- delete the remote branch, if the team follows that practice

## Promoting to `main`

When `develop` is validated and ready for release:

```bash
git checkout main
git pull origin main
git merge develop
git push origin main
```

Recommendations:

- promote only validated deliverables to `main`
- avoid mixing in incomplete code
- clearly record what went into the merge

## Best Practices

- No direct commits to `main`.
- No direct commits to `develop` without team alignment.
- One branch per task.
- Keep PRs small and focused.
- Avoid branches covering multiple topics.
- Use clear branch names.
- Write descriptive commits.
- Always review before merging.

## Full Example

```bash
git checkout develop
git pull origin develop
git checkout -b feat/autenticacao-keycloak

# make code changes

git add .
git commit -m "feat: adiciona validacao de grupos do keycloak"
git push -u origin feat/autenticacao-keycloak
```

Then:

- open a PR from `feat/autenticacao-keycloak` to `develop`
- review
- approve
- merge into `develop`

## Quick Summary

- `main`: production
- `develop`: team integration
- `feat/*`: new features (legacy alias: `feature/*`)
- `fix/*`: fixes
- `chore/*`: maintenance / tooling / infra
- `hotfix/*`: urgent production fixes

Main rule:

```text
Branch feat/*, fix/*, and chore/* from develop; branch hotfix/* from main
Open PRs from feat/*, fix/*, and chore/* to develop
Open hotfix/* PRs to main, then merge back into develop
Promote develop to main only when stable
```
