# CI/CD — безопасные релизы

Пайплайн: **PR → merge в `main` → образы в GHCR → staging → smoke → (аппрув) → prod**,
с авто-откатом на предыдущий рабочий тег при провале smoke-теста.

## Потоки

| Workflow | Триггер | Что делает |
|----------|---------|------------|
| `ci.yml` | PR, push в любую ветку кроме `main` | `_verify` (mvn verify + фронт + bash-тесты) + сборка всех образов через bake (без публикации) |
| `release.yml` | push в `main` | `_verify` → `publish` (bake `--push` в GHCR, теги `latest` + `<sha>`) → `deploy-staging` → `deploy-production` (за ручным аппрувом environment) |
| `rollback.yml` | вручную (`workflow_dispatch`) | откат окружения на предыдущий записанный тег (или заданный) |
| `_verify.yml` | `workflow_call` | единственное определение «зелёной сборки», переиспользуется ci и release |

`_verify` = три джобы: **backend** (`mvn -B verify` — юнит + Testcontainers-IT + `ModularityTests`-гейт),
**frontend** (`lint` + `vitest` + `build`), **scripts** (`shellcheck` + `scripts/cicd/tests/run.sh`).

## Образы

- Реестр: `ghcr.io/<owner>/pravoos/<service>` (5 бэкендов + `frontend`).
- Сборка — `docker buildx bake` по `docker-bake.hcl`: пять бэкенд-таргетов наследуют общий
  maven-stage из `docker/Dockerfile`, поэтому реактор компилируется **один раз**, а не 5.
- Тег — неизменяемый `github.sha`; `latest` двигается на последний прод-релиз.

## Деплой на хост

`scripts/cicd/deploy-remote.sh` выполняется НА сервере (по SSH из `ssh-deploy`):

1. `git checkout <sha>` — compose-файлы и скрипты синхронны с образами.
2. `deploy_state_promote` записывает новый тег как `CURRENT`, предыдущий → `PREVIOUS` (`.deploy/state.env`).
3. `docker compose … pull && up -d --wait` с оверлеем `docker-compose.images.yml` (образы, не сборка).
4. `smoke-test.sh` — ждёт `healthy` у всех контейнеров + опционально `curl` публичного URL.
5. Провал smoke → авто-откат на `PREVIOUS` и `exit 1`.

Ручной откат: workflow **Rollback** или на сервере `./scripts/cicd/rollback-remote.sh [<tag>]`.

## Единый источник правды

Список деплой-сервисов задан в `scripts/cicd/lib/registry.sh` и продублирован (по необходимости)
в `docker-bake.hcl` и `docker-compose.images.yml`. `scripts/cicd/tests/consistency.sh`
падает в CI, если списки разъехались.

## Настройка (один раз)

### GitHub → Settings → Environments: `staging`, `production`
Для `production` включить **Required reviewers** — это и есть ручной гейт перед проддеплоем.

### Secrets (repo или environment)
| Secret | Назначение |
|--------|-----------|
| `DEPLOY_SSH_KEY` | приватный SSH-ключ деплой-пользователя |
| `DEPLOY_SSH_USER` | SSH-пользователь на хостах |
| `STAGING_HOST` / `PRODUCTION_HOST` | IP/DNS хостов |

`GITHUB_TOKEN` (встроенный) используется для push в GHCR и pull приватных образов — отдельный PAT не нужен.

### Variables
| Variable | Пример |
|----------|--------|
| `DEPLOY_PATH` | `/opt/pravoos` |
| `STAGING_DOMAIN` | `staging.pravoos.ru` |
| `PRODUCTION_DOMAIN` | `pravoos.ru` |

### Хост (staging и prod)
```bash
sudo scripts/setup-server.sh          # docker, ufw, jdk
git clone https://github.com/d3vy/PravoOS.git /opt/pravoos
cd /opt/pravoos && cp .env.example .env && nano .env
# первый bootstrap (SSL + сборка) — как раньше, ручным деплоем:
./scripts/deploy.sh
```
Дальше релизы едут сами через `release.yml`. `scripts/deploy.sh` остаётся как fallback
(собирает и поднимает стек локально на VPS без реестра).
