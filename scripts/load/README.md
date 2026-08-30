# Нагрузочный профиль (проход E)

Полный runbook — [`docs/load-testing.md`](../../docs/load-testing.md).

```bash
cp scripts/load/.env.load.example scripts/load/.env.load
scripts/load/stack.sh up
scripts/load/seed/seed.sh
scripts/load/run.sh journey
scripts/load/run.sh search
scripts/load/run.sh chat-stream
```

Отчёты складываются в `scripts/load/report/<дата>-<сценарий>/`.
