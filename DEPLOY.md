# Деплой PravoOS на VPS (production)

## Требования

- Ubuntu 22.04 / 24.04 (или Debian)
- Docker Engine + Compose v2.23+
- Домен с A-записью на IP сервера (`pravoos.ru` → `77.110.116.203`)
- A-запись для `www.pravoos.ru` (тот же IP или CNAME на `pravoos.ru`)
- Порты **80** и **443** открыты (ufw / панель хостинга)

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

`setup-server.sh` установит Docker, `gettext-base` (envsubst), настроит ufw (22, 80, 443).

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
| `DEEPSEEK_API_KEY` | Ключ API |
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
2. Соберёт Docker-образы
3. Поднимет стек (БД, Kafka, сервисы, nginx)
4. Получит SSL-сертификат Let's Encrypt
5. Включит HTTPS и перезапустит frontend

После успеха:

- **https://pravoos.ru** — основной вход
- **http://SERVER_IP** — по IP без TLS (для проверки)

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

## 6. Локальная разработка

На машине разработчика (без prod-оверрайда):

```bash
docker compose up -d
cd frontend && npm run dev
```

API: `http://localhost:8080`, фронт: `http://localhost:3000`.

## 7. Troubleshooting

**Сертификат не выдаётся**

- DNS должен указывать на сервер до запуска `deploy.sh`
- Порт 80 доступен из интернета
- Домен в `.env` совпадает с DNS

**502 / API не отвечает**

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs user-service ai-service api-gateway
```

**CORS в браузере**

При работе через `https://домен` запросы идут с того же origin — CORS не нужен. Для dev используется `CORS_INCLUDE_LOCALHOST=true` (в prod отключено).

**Docker Compose: `!reset` unknown**

Обнови Docker: `apt install docker-compose-plugin` или используй Docker 24+.
