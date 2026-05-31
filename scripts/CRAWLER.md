# Краулер базы практики (`crawl_practice.py`)

Обходит страницы сайтов-источников, сам находит на них PDF/DOCX и загружает в
production через API (`POST /api/ai/documents`). Заменяет старый
`ingest_practice.py`, который умел только прямые ссылки на файлы.

## Установка

```bash
cd scripts
python3 -m pip install -r requirements.txt
# Только если используешь render_js: true (JS-сайты, напр. kad.arbitr.ru):
python3 -m playwright install chromium
```

## Настройка источников

```bash
cp sites.example.yaml sites.yaml
# отредактируй sites.yaml — впиши реальные стартовые страницы (seeds)
```

`seeds` — это страницы со **списками** документов (раздел практики, поиск,
карточка дела), а НЕ прямые ссылки на PDF. Краулер сам пройдёт по страницам в
пределах `allowed_domains` + `allowed_path_prefixes` до глубины `max_depth` и
соберёт все PDF/DOCX.

## Запуск

Сначала — «холостой» прогон без загрузки, чтобы увидеть, что найдётся:

```bash
python3 crawl_practice.py --config sites.yaml --dry-run
```

Реальная загрузка в production:

```bash
python3 crawl_practice.py \
    --config sites.yaml \
    --base-url https://pravoos.ru \
    --email admin@pravoos.ru --password '***'
```

## Инкрементальные запуски

Состояние пишется в `ingest_state.json` (URL + SHA-256 содержимого). Повторный
запуск с тем же файлом состояния загрузит **только новые** документы и не создаст
дубликатов (даже если один и тот же файл доступен по разным URL). Это и есть
механизм обновления базы — просто запускай по расписанию.

## Ключевые флаги

| Флаг | Назначение |
|------|------------|
| `--dry-run` | найти и показать документы, ничего не грузить (логин не нужен) |
| `--workers N` | параллельных скачиваний/загрузок (по умолчанию 4) |
| `--delay SEC` | пауза между запросами страниц (вежливость к сайту) |
| `--ignore-robots` | игнорировать robots.txt |
| `--state FILE` | путь к файлу состояния (для разных наборов источников) |
| `--render-timeout` | таймаут рендера JS-страницы (сек) |

## Режим text (тексты законов, обзоры)

`mode: text` в `sites.yaml` — извлекает чистый текст каждой страницы (режет
скрипты/меню/футер) и грузит как `.txt`. Бэкенд принимает `.txt` наравне с
PDF/DOCX. Для прямых PDF-ссылок без расширения (напр. vsrf.ru `/documents/all/{id}/`)
краулер определяет тип по Content-Type и тянет заголовок из первой страницы PDF.

Пример — полный текст 127-ФЗ постатейно с consultant.ru:
```yaml
- name: law-127fz
  seeds: [https://www.consultant.ru/document/cons_doc_LAW_39331/]
  allowed_domains: [www.consultant.ru, consultant.ru]
  allowed_path_prefixes: [/document/cons_doc_LAW_39331/]
  max_depth: 1
  mode: text
```

## Ручная подкладка файлов (kad.arbitr.ru, sudrf — анти-бот)

Эти сайты защищены от ботов (captcha/DDoS-Guard) — автоматом не качаются.
Скачай нужные дела/решения вручную в папку и залей:
```bash
python3 crawl_practice.py --sources-dir ./manual_docs \
    --base-url https://pravoos.ru --email "$ADMIN_EMAIL" --password "$ADMIN_PASSWORD"
```
Поддерживаются `.pdf/.docx/.txt`. Заголовок берётся из имени файла, так что
называй осмысленно (напр. `Решение АС Москвы А40-12345-2024.pdf`). Можно
сочетать с `--config` в одном запуске. Дедуп по содержимому работает и тут.

## JS-сайты

`render_js: true` рендерит страницу через Playwright (`pip install playwright &&
python -m playwright install chromium`). Проверено: kad.arbitr.ru даже так
отдаёт captcha/DDoS-Guard, bsr.sudrf.ru недоступен — используй `--sources-dir`.
