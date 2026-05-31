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

## JS-сайты (kad.arbitr.ru и подобные)

Поставь `render_js: true` для такого сайта в `sites.yaml`. Тогда страница
рендерится headless-браузером Chromium через Playwright. Требует установленного
`playwright` + `python -m playwright install chromium`. Учти: kad.arbitr.ru
агрессивно защищён от ботов, может потребоваться ручная подкладка файлов.
