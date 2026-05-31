# Источники базы практики

Кладите сюда **PDF** и **DOCX** файлы с правовыми материалами — они будут
загружены в базу практики скриптом `../ingest_practice.py`.

Что класть:
- Тексты законов (например 127-ФЗ), скачанные с pravo.gov.ru / consultant.ru
- Обзоры судебной практики ВС РФ (PDF с vsrf.ru)
- Определения и решения судов, скачанные вручную с kad.arbitr.ru
  (kad.arbitr защищён анти-ботом — автоматически не парсится, только вручную)

Файлы из этого каталога в git не коммитятся (см. `.gitignore`).

## Запуск ингеста

```bash
cd scripts
pip install -r requirements.txt
python3 ingest_practice.py \
    --base-url http://localhost:8080 \
    --email admin@example.com --password '***' \
    --sources-dir practice_sources \
    --urls urls.txt        # опционально: прямые ссылки на PDF
```

`urls.txt` — по одной прямой ссылке в строке (строки с `#` игнорируются).
Подходит для источников без анти-бот защиты (pravo.gov.ru, vsrf.ru).
