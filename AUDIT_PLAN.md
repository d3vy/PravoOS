# Аудит-пасс 3 + фичи (2026-07-02)

> Пассы 1–2 закрыты (см. git: 48c31bb, da8537a, 9876cc7, 80ebb3d). Ниже — новые находки.
> Статус: ⬜ не начато · 🔄 в работе · ✅ готово

## Фаза 1 — критично (утечки данных, обход защиты)

1. ✅ **Origin доступен в обход Cloudflare.** ufw открывает 80/443 всему интернету, IP сервера засвечен (git-история, конфиги). Атакующий бьёт напрямую в origin — мимо CF WAF/DDoS-защиты. Фикс: ufw allow 80/443 только с CF-диапазонов (cloudflare.com/ips, синхронно с `cloudflare-realip.conf`) + включить Authenticated Origin Pulls (mTLS) в nginx; в идеале — сменить IP после закрытия.
   *Сделано:* `scripts/lockdown-origin.sh` (ufw → только CF-диапазоны, drift-check против realip.conf), AOP в `app.prod.conf.template` (`ssl_verify_client ${CF_ORIGIN_PULL}`, CA-серт `docker/nginx/cloudflare-origin-pull-ca.pem` в репо), plain-HTTP блок по IP заменён на `return 444` default_server. DEPLOY.md §6. **На сервере вручную:** прогнать `lockdown-origin.sh`, включить тумблер AOP в CF-дашборде → `CF_ORIGIN_PULL=on` → re-render+reload; затем сменить IP у хостера.
2. ✅ **Файлы юристов на диске в открытом виде.** Уже закрыто ранее в блоке C7 (коммит 047ce31): `FileCryptoService` AES-256-GCM, ключ `FILE_ENCRYPTION_KEY`, magic `POS1`, legacy plaintext читается прозрачно.
3. ✅ **Нет антивирус-проверки загрузок.** Уже закрыто ранее в блоке C10 (коммит 047ce31): контейнер `clamav/clamav:1.3`, `MalwareScanClient` (clamd INSTREAM по TCP), скан в `DocumentService.upload` до сохранения, `CLAMAV_FAIL_OPEN=false`, гейт по `CLAMAV_HOST`.
