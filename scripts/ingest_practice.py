#!/usr/bin/env python3
"""
Наполнение базы практики PravoOS.

Скрипт берёт документы из двух источников и заливает их в ai-service через
существующий ADMIN-эндпоинт POST /api/ai/documents (текст извлекается,
нарезается и эмбеддится автоматически):

  1. Прямые ссылки из --urls-файла (по одной ссылке в строке).
     Подходит для источников без анти-бот защиты (pravo.gov.ru, vsrf.ru).
  2. Локальные файлы из каталога --sources-dir.
     Сюда вручную кладут всё, что не качается автоматом (например определения
     с kad.arbitr.ru, скачанные через браузер).

Поддерживаемые форматы: PDF, DOCX (ограничение upload-эндпоинта ai-service).

Пример:
  python3 ingest_practice.py \\
      --base-url https://pravoos.ru \\
      --email admin@example.com --password '***' \\
      --urls urls.txt --sources-dir practice_sources
"""

import argparse
import mimetypes
import os
import sys
import tempfile
from pathlib import Path
from urllib.parse import urlparse

import requests

ALLOWED_EXTENSIONS = {".pdf", ".docx"}
USER_AGENT = "Mozilla/5.0 (compatible; PravoOS-Ingest/1.0)"
REQUEST_TIMEOUT = 60


def parse_args():
    parser = argparse.ArgumentParser(description="Наполнение базы практики PravoOS")
    parser.add_argument("--base-url", default="http://localhost:8080",
                        help="Базовый URL API (через api-gateway). По умолчанию localhost:8080")
    parser.add_argument("--email", required=True, help="Email ADMIN-аккаунта")
    parser.add_argument("--password", required=True, help="Пароль ADMIN-аккаунта")
    parser.add_argument("--sources-dir", default="practice_sources",
                        help="Каталог с локальными PDF/DOCX файлами")
    parser.add_argument("--urls", default=None,
                        help="Файл со списком прямых URL (по одной ссылке в строке)")
    return parser.parse_args()


def login(base_url, email, password):
    response = requests.post(
        f"{base_url}/api/auth/login",
        json={"email": email, "password": password},
        timeout=REQUEST_TIMEOUT,
    )
    if response.status_code != 200:
        raise SystemExit(f"Не удалось войти: HTTP {response.status_code} {response.text}")
    token = response.json().get("token")
    if not token:
        raise SystemExit("В ответе логина нет токена")
    return token


def read_url_list(urls_file):
    if not urls_file:
        return []
    path = Path(urls_file)
    if not path.is_file():
        print(f"[warn] Файл со ссылками не найден: {urls_file}", file=sys.stderr)
        return []
    urls = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            urls.append(stripped)
    return urls


def download(url, dest_dir):
    try:
        response = requests.get(url, headers={"User-Agent": USER_AGENT},
                                timeout=REQUEST_TIMEOUT, stream=True)
    except requests.RequestException as exc:
        print(f"[skip] {url}: ошибка запроса ({exc})", file=sys.stderr)
        return None

    if response.status_code != 200:
        print(f"[skip] {url}: HTTP {response.status_code}", file=sys.stderr)
        return None

    file_name = resolve_file_name(url, response)
    if Path(file_name).suffix.lower() not in ALLOWED_EXTENSIONS:
        print(f"[skip] {url}: неподдерживаемый формат ({file_name})", file=sys.stderr)
        return None

    dest_path = Path(dest_dir) / file_name
    with open(dest_path, "wb") as out:
        for chunk in response.iter_content(chunk_size=8192):
            out.write(chunk)
    return dest_path


def resolve_file_name(url, response):
    name = os.path.basename(urlparse(url).path) or "document"
    if Path(name).suffix.lower() in ALLOWED_EXTENSIONS:
        return name
    content_type = response.headers.get("Content-Type", "").split(";")[0].strip()
    extension = mimetypes.guess_extension(content_type) or ""
    if content_type == "application/pdf":
        extension = ".pdf"
    elif content_type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
        extension = ".docx"
    return name + extension


def collect_local_files(sources_dir):
    path = Path(sources_dir)
    if not path.is_dir():
        return []
    return [f for f in sorted(path.iterdir())
            if f.is_file() and f.suffix.lower() in ALLOWED_EXTENSIONS]


def upload(base_url, token, file_path):
    title = Path(file_path).stem
    with open(file_path, "rb") as handle:
        response = requests.post(
            f"{base_url}/api/ai/documents",
            headers={"Authorization": f"Bearer {token}"},
            files={"file": (Path(file_path).name, handle)},
            data={"title": title},
            timeout=REQUEST_TIMEOUT,
        )
    if response.status_code in (200, 201):
        print(f"[ok]   {Path(file_path).name} -> {response.json().get('id')}")
        return True
    print(f"[fail] {Path(file_path).name}: HTTP {response.status_code} {response.text}", file=sys.stderr)
    return False


def main():
    args = parse_args()
    base_url = args.base_url.rstrip("/")
    token = login(base_url, args.email, args.password)
    print("[ok]   Аутентификация ADMIN успешна")

    uploaded = 0
    failed = 0

    with tempfile.TemporaryDirectory() as tmp_dir:
        for url in read_url_list(args.urls):
            downloaded = download(url, tmp_dir)
            if downloaded is None:
                failed += 1
                continue
            if upload(base_url, token, downloaded):
                uploaded += 1
            else:
                failed += 1

        for local_file in collect_local_files(args.sources_dir):
            if upload(base_url, token, local_file):
                uploaded += 1
            else:
                failed += 1

    print(f"\nГотово. Загружено: {uploaded}, ошибок/пропусков: {failed}")
    sys.exit(1 if uploaded == 0 else 0)


if __name__ == "__main__":
    main()
