#!/usr/bin/env python3
"""
Краулер базы практики PravoOS.

Обходит страницы сайтов-источников (НЕ прямые ссылки на файлы), сам находит
на них PDF/DOCX — в том числе ссылки без расширения (определяет тип по
Content-Type) — скачивает и загружает в production через API.

Возможности:
  * BFS-обход страниц с ограничением по домену, префиксу пути и глубине;
  * обнаружение документов в <a>, <iframe>, <embed> и ссылках-редиректах;
  * дедупликация по SHA-256 содержимого и по URL (инкрементальные прогоны);
  * состояние в JSON — повторный запуск грузит только новые документы;
  * опциональный рендеринг JS-страниц через Playwright (kad.arbitr.ru и т.п.);
  * параллельные скачивание и загрузка, вежливые задержки, retry.

Конфигурация сайтов — в YAML (см. sites.example.yaml).

Пример:
  python3 crawl_practice.py \\
      --config sites.yaml \\
      --base-url https://pravoos.ru \\
      --email admin@example.com --password '***'
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import mimetypes
import re
import sys
import tempfile
import threading
import time
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urljoin, urlparse, urldefrag
from urllib.robotparser import RobotFileParser

import requests
import yaml
from bs4 import BeautifulSoup
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry


USER_AGENT = "Mozilla/5.0 (compatible; PravoOS-Crawler/2.0; +https://pravoos.ru)"
REQUEST_TIMEOUT = 60
DOWNLOAD_CHUNK = 8192
MAX_FILE_BYTES = 100 * 1024 * 1024

CONTENT_TYPE_EXTENSIONS = {
    "application/pdf": "pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
}
ALLOWED_EXTENSIONS = {"pdf", "docx"}
DOCUMENT_LINK_HINT = re.compile(r"\.(pdf|docx)(\?|#|$)", re.IGNORECASE)


class CrawlError(Exception):
    pass


class AuthError(CrawlError):
    pass


@dataclass
class SiteConfig:
    name: str
    seeds: list[str]
    allowed_domains: set[str]
    allowed_path_prefixes: list[str]
    max_depth: int = 2
    render_js: bool = False
    max_pages: int = 500
    mode: str = "documents"
    min_text_chars: int = 400

    @staticmethod
    def from_dict(raw: dict) -> "SiteConfig":
        seeds = raw.get("seeds") or []
        if not seeds:
            raise CrawlError(f"Сайт '{raw.get('name')}' не имеет seeds")
        domains = raw.get("allowed_domains")
        if not domains:
            domains = sorted({urlparse(seed).netloc for seed in seeds})
        mode = (raw.get("mode") or "documents").lower()
        if mode not in ("documents", "text", "both"):
            raise CrawlError(f"Сайт '{raw.get('name')}': mode должен быть documents|text|both")
        return SiteConfig(
            name=raw.get("name") or urlparse(seeds[0]).netloc,
            seeds=seeds,
            allowed_domains={d.lower() for d in domains},
            allowed_path_prefixes=raw.get("allowed_path_prefixes") or ["/"],
            max_depth=int(raw.get("max_depth", 2)),
            render_js=bool(raw.get("render_js", False)),
            max_pages=int(raw.get("max_pages", 500)),
            mode=mode,
            min_text_chars=int(raw.get("min_text_chars", 400)),
        )


@dataclass
class DocumentLink:
    url: str
    site_name: str
    page_title: str = ""
    kind: str = "file"
    text_content: str | None = None


@dataclass
class CrawlStats:
    pages_visited: int = 0
    documents_found: int = 0
    uploaded: int = 0
    skipped_duplicate: int = 0
    failed: int = 0
    lock: threading.Lock = field(default_factory=threading.Lock)

    def bump(self, attr: str, amount: int = 1) -> None:
        with self.lock:
            setattr(self, attr, getattr(self, attr) + amount)


class StateStore:
    def __init__(self, path: Path):
        self.path = path
        self.lock = threading.Lock()
        self.uploaded_urls: dict[str, dict] = {}
        self.content_hashes: set[str] = set()
        self._load()

    def _load(self) -> None:
        if not self.path.is_file():
            return
        data = json.loads(self.path.read_text(encoding="utf-8"))
        self.uploaded_urls = data.get("uploaded_urls", {})
        self.content_hashes = set(data.get("content_hashes", []))

    def is_url_done(self, url: str) -> bool:
        return url in self.uploaded_urls

    def is_hash_done(self, content_hash: str) -> bool:
        return content_hash in self.content_hashes

    def record(self, url: str, content_hash: str, document_id: str | None) -> None:
        with self.lock:
            self.uploaded_urls[url] = {
                "sha256": content_hash,
                "document_id": document_id,
                "uploaded_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
            }
            self.content_hashes.add(content_hash)
            self._flush()

    def reserve_hash(self, content_hash: str) -> bool:
        with self.lock:
            if content_hash in self.content_hashes:
                return False
            self.content_hashes.add(content_hash)
            return True

    def _flush(self) -> None:
        payload = {
            "uploaded_urls": self.uploaded_urls,
            "content_hashes": sorted(self.content_hashes),
        }
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2),
                             encoding="utf-8")


class PravoOsClient:
    def __init__(self, base_url: str, session: requests.Session):
        self.base_url = base_url.rstrip("/")
        self.session = session
        self.token: str | None = None

    def login(self, email: str, password: str) -> None:
        response = self.session.post(
            f"{self.base_url}/api/auth/login",
            json={"email": email, "password": password},
            timeout=REQUEST_TIMEOUT,
        )
        if response.status_code != 200:
            raise AuthError(f"Логин не удался: HTTP {response.status_code} {response.text}")
        token = response.json().get("token")
        if not token:
            raise AuthError("В ответе логина нет поля token")
        self.token = token

    def upload(self, file_path: Path, title: str) -> str | None:
        with open(file_path, "rb") as handle:
            response = self.session.post(
                f"{self.base_url}/api/ai/documents",
                headers={"Authorization": f"Bearer {self.token}"},
                files={"file": (file_path.name, handle)},
                data={"title": title},
                timeout=REQUEST_TIMEOUT,
            )
        if response.status_code in (200, 201):
            return response.json().get("id")
        raise CrawlError(f"Загрузка отклонена: HTTP {response.status_code} {response.text}")


def build_session() -> requests.Session:
    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT
    retry = Retry(
        total=3,
        backoff_factor=1,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET", "HEAD", "POST"],
    )
    adapter = HTTPAdapter(max_retries=retry, pool_connections=20, pool_maxsize=20)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


class RobotsCache:
    def __init__(self, session: requests.Session, enabled: bool):
        self.session = session
        self.enabled = enabled
        self.parsers: dict[str, RobotFileParser | None] = {}

    def allowed(self, url: str) -> bool:
        if not self.enabled:
            return True
        parsed = urlparse(url)
        root = f"{parsed.scheme}://{parsed.netloc}"
        if root not in self.parsers:
            self.parsers[root] = self._fetch(root)
        parser = self.parsers[root]
        if parser is None:
            return True
        return parser.can_fetch(USER_AGENT, url)

    def _fetch(self, root: str) -> RobotFileParser | None:
        try:
            response = self.session.get(f"{root}/robots.txt", timeout=REQUEST_TIMEOUT)
            if response.status_code != 200:
                return None
            parser = RobotFileParser()
            parser.parse(response.text.splitlines())
            return parser
        except requests.RequestException:
            return None


class Crawler:
    def __init__(self, session: requests.Session, robots: RobotsCache,
                 delay: float, render_timeout: int):
        self.session = session
        self.robots = robots
        self.delay = delay
        self.render_timeout = render_timeout
        self._playwright_page = None

    def discover(self, site: SiteConfig, stats: CrawlStats) -> list[DocumentLink]:
        visited: set[str] = set()
        documents: dict[str, DocumentLink] = {}
        queue: deque[tuple[str, int]] = deque((seed, 0) for seed in site.seeds)

        while queue and len(visited) < site.max_pages:
            url, depth = queue.popleft()
            url, _ = urldefrag(url)
            if url in visited or not self._in_scope(url, site):
                continue
            visited.add(url)
            if not self.robots.allowed(url):
                continue

            kind, payload = self._fetch(url, site)
            if kind is None:
                continue
            if self.delay:
                time.sleep(self.delay)

            if kind == "document":
                if url not in documents:
                    documents[url] = DocumentLink(url, site.name)
                continue

            stats.bump("pages_visited")
            page_links, page_title = self._extract_links(url, payload)

            if site.mode in ("text", "both") and url not in documents:
                text = self._extract_clean_text(payload)
                if len(text) >= site.min_text_chars:
                    documents[url] = DocumentLink(url, site.name, page_title,
                                                  kind="text", text_content=text)

            for link in page_links:
                if site.mode in ("documents", "both") and self._looks_like_document(link):
                    if link not in documents:
                        documents[link] = DocumentLink(link, site.name, page_title)
                elif depth < site.max_depth and self._in_scope(link, site):
                    queue.append((link, depth + 1))

        return list(documents.values())

    def _in_scope(self, url: str, site: SiteConfig) -> bool:
        parsed = urlparse(url)
        if parsed.scheme not in ("http", "https"):
            return False
        if parsed.netloc.lower() not in site.allowed_domains:
            return False
        return any(parsed.path.startswith(prefix) for prefix in site.allowed_path_prefixes)

    def _fetch(self, url: str, site: SiteConfig) -> tuple[str | None, str | None]:
        if site.render_js:
            rendered = self._fetch_rendered(url)
            return ("html", rendered) if rendered is not None else (None, None)
        try:
            response = self.session.get(url, timeout=REQUEST_TIMEOUT, stream=True)
        except requests.RequestException as exc:
            print(f"[skip] {url}: {exc}", file=sys.stderr)
            return None, None
        try:
            if response.status_code != 200:
                return None, None
            content_type = response.headers.get("Content-Type", "")
            if resolve_extension(url, content_type) is not None:
                return "document", content_type
            if "html" in content_type.lower():
                return "html", response.text
            return None, None
        finally:
            response.close()

    def _fetch_rendered(self, url: str) -> str | None:
        page = self._ensure_playwright()
        try:
            page.goto(url, timeout=self.render_timeout * 1000, wait_until="networkidle")
            return page.content()
        except Exception as exc:
            print(f"[skip] {url}: render failed ({exc})", file=sys.stderr)
            return None

    def _ensure_playwright(self):
        if self._playwright_page is not None:
            return self._playwright_page
        try:
            from playwright.sync_api import sync_playwright
        except ImportError as exc:
            raise CrawlError(
                "render_js=true требует Playwright. Установи: "
                "pip install playwright && python -m playwright install chromium"
            ) from exc
        self._pw = sync_playwright().start()
        self._browser = self._pw.chromium.launch(headless=True)
        context = self._browser.new_context(user_agent=USER_AGENT)
        self._playwright_page = context.new_page()
        return self._playwright_page

    def close(self) -> None:
        if self._playwright_page is not None:
            self._browser.close()
            self._pw.stop()
            self._playwright_page = None

    @staticmethod
    def _extract_links(base_url: str, html: str) -> tuple[list[str], str]:
        soup = BeautifulSoup(html, "lxml")
        title = soup.title.string.strip() if soup.title and soup.title.string else ""
        links: set[str] = set()
        for tag, attr in (("a", "href"), ("iframe", "src"), ("embed", "src")):
            for element in soup.find_all(tag):
                value = element.get(attr)
                if value:
                    absolute, _ = urldefrag(urljoin(base_url, value.strip()))
                    if absolute.startswith(("http://", "https://")):
                        links.add(absolute)
        return list(links), title

    @staticmethod
    def _looks_like_document(url: str) -> bool:
        return bool(DOCUMENT_LINK_HINT.search(url))

    @staticmethod
    def _extract_clean_text(html: str) -> str:
        soup = BeautifulSoup(html, "lxml")
        for tag in soup(["script", "style", "noscript", "nav", "header",
                         "footer", "aside", "form", "svg"]):
            tag.decompose()
        raw = soup.get_text(separator="\n")
        lines = [line.strip() for line in raw.splitlines()]
        return "\n".join(line for line in lines if line)


def resolve_extension(url: str, content_type: str) -> str | None:
    path = urlparse(url).path
    suffix = Path(path).suffix.lower().lstrip(".")
    if suffix in ALLOWED_EXTENSIONS:
        return suffix
    base_type = content_type.split(";")[0].strip().lower()
    if base_type in CONTENT_TYPE_EXTENSIONS:
        return CONTENT_TYPE_EXTENSIONS[base_type]
    guessed = mimetypes.guess_extension(base_type) or ""
    guessed = guessed.lstrip(".").lower()
    return guessed if guessed in ALLOWED_EXTENSIONS else None


def resolve_title(document: DocumentLink, file_path: Path) -> str:
    if document.page_title:
        return document.page_title[:255]
    if file_path.suffix.lower() == ".pdf":
        extracted = extract_pdf_title(file_path)
        if extracted:
            return extracted[:255]
    return file_path.stem


def extract_pdf_title(file_path: Path) -> str | None:
    try:
        from pypdf import PdfReader
    except ImportError:
        return None
    try:
        reader = PdfReader(str(file_path))
        if not reader.pages:
            return None
        lines = [line.strip() for line in (reader.pages[0].extract_text() or "").splitlines()]
        meaningful = [line for line in lines if len(line) > 3]
        return " ".join(meaningful[:6]) if meaningful else None
    except Exception:
        return None


def download_document(session: requests.Session, document: DocumentLink,
                      dest_dir: Path) -> tuple[Path, str] | None:
    try:
        response = session.get(document.url, timeout=REQUEST_TIMEOUT, stream=True)
    except requests.RequestException as exc:
        print(f"[skip] {document.url}: {exc}", file=sys.stderr)
        return None
    if response.status_code != 200:
        print(f"[skip] {document.url}: HTTP {response.status_code}", file=sys.stderr)
        return None

    content_type = response.headers.get("Content-Type", "")
    extension = resolve_extension(document.url, content_type)
    if extension is None:
        print(f"[skip] {document.url}: не PDF/DOCX ({content_type})", file=sys.stderr)
        return None

    hasher = hashlib.sha256()
    file_name = (Path(urlparse(document.url).path).name or "document")
    if not file_name.lower().endswith("." + extension):
        file_name = f"{file_name}.{extension}"
    dest_path = dest_dir / f"{abs(hash(document.url))}_{file_name}"

    total = 0
    with open(dest_path, "wb") as out:
        for chunk in response.iter_content(chunk_size=DOWNLOAD_CHUNK):
            if not chunk:
                continue
            total += len(chunk)
            if total > MAX_FILE_BYTES:
                print(f"[skip] {document.url}: превышен лимит размера", file=sys.stderr)
                out.close()
                dest_path.unlink(missing_ok=True)
                return None
            out.write(chunk)
            hasher.update(chunk)

    if total == 0:
        dest_path.unlink(missing_ok=True)
        return None
    return dest_path, hasher.hexdigest()


def write_text_document(document: DocumentLink, dest_dir: Path) -> tuple[Path, str]:
    payload = document.text_content.encode("utf-8")
    content_hash = hashlib.sha256(payload).hexdigest()
    dest_path = dest_dir / f"{abs(hash(document.url))}.txt"
    dest_path.write_bytes(payload)
    return dest_path, content_hash


def process_document(document: DocumentLink, session: requests.Session,
                     client: PravoOsClient, state: StateStore, stats: CrawlStats,
                     dest_dir: Path, dry_run: bool) -> None:
    if state.is_url_done(document.url):
        stats.bump("skipped_duplicate")
        return

    if document.kind == "text":
        result = write_text_document(document, dest_dir)
    else:
        result = download_document(session, document, dest_dir)
    if result is None:
        stats.bump("failed")
        return
    file_path, content_hash = result

    if state.is_hash_done(content_hash) or not state.reserve_hash(content_hash):
        stats.bump("skipped_duplicate")
        file_path.unlink(missing_ok=True)
        state.record(document.url, content_hash, None)
        return

    title = resolve_title(document, file_path)
    if dry_run:
        print(f"[dry]  {document.url} -> '{title}'")
        stats.bump("uploaded")
        file_path.unlink(missing_ok=True)
        return

    try:
        document_id = client.upload(file_path, title)
        state.record(document.url, content_hash, document_id)
        stats.bump("uploaded")
        print(f"[ok]   {document.url} -> {document_id}")
    except CrawlError as exc:
        stats.bump("failed")
        print(f"[fail] {document.url}: {exc}", file=sys.stderr)
    finally:
        file_path.unlink(missing_ok=True)


def load_sites(config_path: Path) -> list[SiteConfig]:
    if not config_path.is_file():
        raise CrawlError(f"Конфиг не найден: {config_path}")
    raw = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    sites = raw.get("sites") if isinstance(raw, dict) else None
    if not sites:
        raise CrawlError("В конфиге нет секции 'sites'")
    return [SiteConfig.from_dict(item) for item in sites]


def parse_args():
    parser = argparse.ArgumentParser(description="Краулер базы практики PravoOS")
    parser.add_argument("--config", required=True, help="YAML с описанием сайтов")
    parser.add_argument("--base-url", default="http://localhost:8080",
                        help="Базовый URL API (через api-gateway)")
    parser.add_argument("--email", help="Email ADMIN-аккаунта (не нужен при --dry-run)")
    parser.add_argument("--password", help="Пароль ADMIN-аккаунта")
    parser.add_argument("--state", default="ingest_state.json",
                        help="Файл состояния для инкрементальных запусков")
    parser.add_argument("--workers", type=int, default=4,
                        help="Параллельных скачиваний/загрузок")
    parser.add_argument("--delay", type=float, default=0.5,
                        help="Пауза между запросами страниц (сек)")
    parser.add_argument("--render-timeout", type=int, default=30,
                        help="Таймаут рендера JS-страницы (сек)")
    parser.add_argument("--ignore-robots", action="store_true",
                        help="Игнорировать robots.txt")
    parser.add_argument("--dry-run", action="store_true",
                        help="Не загружать в API — только найти и показать документы")
    return parser.parse_args()


def main():
    args = parse_args()
    session = build_session()
    client = PravoOsClient(args.base_url, session)

    if not args.dry_run:
        if not args.email or not args.password:
            raise SystemExit("Нужны --email и --password (или используй --dry-run)")
        client.login(args.email, args.password)
        print("[ok]   Аутентификация ADMIN успешна")

    sites = load_sites(Path(args.config))
    state = StateStore(Path(args.state))
    robots = RobotsCache(session, enabled=not args.ignore_robots)
    crawler = Crawler(session, robots, args.delay, args.render_timeout)
    stats = CrawlStats()

    try:
        all_documents: list[DocumentLink] = []
        for site in sites:
            print(f"\n=== Обход '{site.name}' ===")
            found = crawler.discover(site, stats)
            stats.bump("documents_found", len(found))
            print(f"    найдено документов: {len(found)}")
            all_documents.extend(found)
    finally:
        crawler.close()

    with tempfile.TemporaryDirectory() as tmp_dir:
        dest_dir = Path(tmp_dir)
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [
                pool.submit(process_document, document, session, client,
                            state, stats, dest_dir, args.dry_run)
                for document in all_documents
            ]
            concurrent.futures.wait(futures)

    print(
        f"\nГотово. Страниц: {stats.pages_visited}, документов найдено: "
        f"{stats.documents_found}, загружено: {stats.uploaded}, "
        f"дубликатов пропущено: {stats.skipped_duplicate}, ошибок: {stats.failed}"
    )
    sys.exit(1 if stats.uploaded == 0 and not args.dry_run else 0)


if __name__ == "__main__":
    main()
