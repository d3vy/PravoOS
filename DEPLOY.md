# Деплой PravoOS на VPS (production)

## Требования

- Ubuntu 22.04 / 24.04 (или Debian)
- Docker Engine + Compose v2.23+
- Домен с A-записью на IP сервера (`pravoos.ru` → `77.110.116.203`)
- A-запись для `www.pravoos.ru` (тот же IP или CNAME на `pravoos.ru`)
- Домен проксируется через Cloudflare (оранжевое облако) — порты **80/443** открыты только с диапазонов Cloudflare (см. раздел «Закрытие origin»)

## 1. Подготовка сервера (один раз)

```bash
ssh root@YOUR_SERVER_IP
```

Скопируй репозиторий на сервер (git clone или `rsync`/`scp`):

```bash
git clone <your-repo-url> /opt/pravoos
cd /opt/pravoos
chmod +x scripts/*.sh
sudo ./scripts/setup-server.sh
```

`setup-server.sh` установит Docker, `gettext-base` (envsubst), настроит ufw: 22 отовсюду, 80/443 — только с диапазонов Cloudflare (через `lockdown-origin.sh`).

## 2. Конфигурация `.env`

```bash
cp .env.example .env
nano .env
```

Обязательно заполни:

| Переменная | Описание |
|------------|----------|
| `SERVER_IP` | IP VPS |
| `SERVER_DOMAIN` | Домен без `https://` |
| `ACME_EMAIL` | Email для Let's Encrypt |
| `JWT_SECRET` | Случайная строка ≥ 64 символов |
| `DB_*`, `MONGO_*` | Сильные пароли |
| `OPENAI_API_KEY` | Ключ API |
| `TELEGRAM_*` | Бот и chat id админа |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Первый админ |

Для production оставь пустым:

```env
VITE_API_URL=
ALLOWED_ORIGINS=
```

Сгенерировать секрет:

```bash
openssl rand -base64 64
```

## 3. Деплой

```bash
./scripts/deploy.sh
```

Скрипт:

1. Проверит `.env`
2. Соберёт JAR на сервере (`mvn package`) — `.m2` кэшируется на хосте
3. Соберёт Docker-образы из готовых JAR (~1 мин)
4. Поднимет стек (БД, Kafka, сервисы, nginx)
5. Получит SSL-сертификат Let's Encrypt
6. Включит HTTPS и перезапустит frontend

После успеха:

- **https://pravoos.ru** — основной вход
- Доступ по голому IP закрыт намеренно: nginx отвечает `444` на запросы без известного Host, ufw пускает 80/443 только с Cloudflare

## 4. Обновление версии

```bash
cd /opt/pravoos
git pull
./scripts/deploy.sh
```

## 5. Полезные команды

```bash
# Статус
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

# Логи
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f api-gateway
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f frontend

# Остановить
docker compose -f docker-compose.yml -f docker-compose.prod.yml down

# Продлить SSL (добавь в cron: 0 3 * * *)
/opt/pravoos/scripts/renew-ssl.sh
```

Cron для автообновления сертификата:

```bash
crontab -e
```

```
0 3 * * * /opt/pravoos/scripts/renew-ssl.sh >> /var/log/pravoos-ssl-renew.log 2>&1
```

## 6. Закрытие origin (Cloudflare-only доступ)

Origin недостижим напрямую — только через Cloudflare. Два слоя:

**Слой 1 — ufw (сеть).** `sudo ./scripts/lockdown-origin.sh` тянет актуальные диапазоны с cloudflare.com/ips, разрешает 80/443 только с них и удаляет широкие allow-правила. Идемпотентен; предупреждает о дрейфе относительно `docker/nginx/cloudflare-realip.conf`. Прогонять при изменении диапазонов Cloudflare (можно в cron раз в месяц).

**Слой 2 — Authenticated Origin Pulls (mTLS).** Nginx требует клиентский сертификат Cloudflare на TLS-хендшейке — защищает даже при смене IP/дырке в ufw. Порядок включения строго такой:

1. Cloudflare dashboard → SSL/TLS → Origin Server → **Authenticated Origin Pulls: On** (и режим SSL — Full (strict))
2. В `.env`: `CF_ORIGIN_PULL=on`
3. `./scripts/render-nginx.sh prod && docker compose -f docker-compose.yml -f docker-compose.prod.yml exec frontend nginx -s reload`

Если включить nginx-часть раньше тумблера в Cloudflare — весь трафик получит 400. CA-сертификат уже в репо (`docker/nginx/cloudflare-origin-pull-ca.pem`, истекает 2029-11-01).

**Первичная выдача сертификата Let's Encrypt:** HTTP-01 challenge проходит через Cloudflare-прокси, поэтому работает при закрытом ufw — но только если домен уже проксируется (оранжевое облако). Если прокси ещё выключен — временно `ufw allow 80/tcp`, после выдачи `sudo ./scripts/lockdown-origin.sh`.

**IP сервера засвечен в старых конфигах/git-истории** — после включения обоих слоёв желательно сменить IP у хостера и обновить `SERVER_IP` в `.env`.

## 7. Локальная разработка

На машине разработчика (без prod-оверрайда):

```bash
docker compose up -d
cd frontend && npm run dev
```

API: `http://localhost:8080`, фронт: `http://localhost:3000`.

## 8. Troubleshooting

**Сертификат не выдаётся**

- DNS должен указывать на сервер до запуска `deploy.sh`
- Домен проксируется через Cloudflare (иначе порт 80 закрыт ufw — см. раздел 6)
- Домен в `.env` совпадает с DNS

**Все запросы падают с 400 «No required SSL certificate was sent»**

`CF_ORIGIN_PULL=on` в nginx, но тумблер Authenticated Origin Pulls в Cloudflare выключен. Включи тумблер или откати `CF_ORIGIN_PULL=off` + re-render + reload.

**502 / API не отвечает**

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs user-service ai-service api-gateway
```

**CORS в браузере**

При работе через `https://домен` запросы идут с того же origin — CORS не нужен. Для dev используется `CORS_INCLUDE_LOCALHOST=true` (в prod отключено).

**Docker Compose: `!reset` unknown**

Обнови Docker: `apt install docker-compose-plugin` или используй Docker 24+.
