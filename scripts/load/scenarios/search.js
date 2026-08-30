// Горячий путь 2: глобальный поиск.
// Пять источников веером на globalSearchExecutor + LIKE по document_chunks без
// триграммного индекса + до десяти отдельных запросов за сниппетами (N+1).
// Сценарий бьёт по трём режимам: попадание с контентом, попадание без контента, промах.
import http from "k6/http";
import { check } from "k6";
import { Trend } from "k6/metrics";
import { BASE_URL, DURATION, params } from "./lib/config.js";
import { tokenFor, tokenPool } from "./lib/auth.js";

const withContent = new Trend("pravoos_search_with_content", true);
const withoutContent = new Trend("pravoos_search_without_content", true);
const missDuration = new Trend("pravoos_search_miss", true);

const HIT_QUERIES = [
  "банкротство",
  "неустойк",
  "подряд",
  "аренда",
  "Ремстрой",
  "статья 309",
  "А40-1",
  "LOAD-1",
];

const MISS_QUERIES = ["квазиуникальныйзапрос", "zzzнетничего", "оффшорныйтрест"];

export const options = {
  // setup() логинит весь пул юристов, а bcrypt(12) стоит сотни миллисекунд на вход.
  setupTimeout: "10m",
  scenarios: {
    search: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.LOAD_SEARCH_RPS || 10),
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: Number(__ENV.LOAD_RAMP_MAX_VUS || 120),
    },
  },
  thresholds: {
    "http_req_failed{endpoint:search}": ["rate<0.01"],
    pravoos_search_with_content: ["p(95)<2000", "p(99)<4000"],
    pravoos_search_without_content: ["p(95)<1000"],
    pravoos_search_miss: ["p(95)<1500"],
  },
};

export function setup() {
  return { tokens: tokenPool() };
}

export default function (data) {
  const token = tokenFor(data.tokens);
  const mode = __ITER % 3;

  if (mode === 2) {
    const query = MISS_QUERIES[__ITER % MISS_QUERIES.length];
    const response = search(query, true, token, "miss");
    missDuration.add(response.timings.duration);
    return;
  }

  const query = HIT_QUERIES[__ITER % HIT_QUERIES.length];
  const content = mode === 0;
  const response = search(query, content, token, content ? "content" : "titles");
  (content ? withContent : withoutContent).add(response.timings.duration);
  check(response, {
    "search has cases": (r) => r.status === 200 && r.json("cases") !== undefined,
  });
}

function search(query, content, token, mode) {
  const url = `${BASE_URL}/api/ai/search?q=${encodeURIComponent(query)}&content=${content}`;
  const response = http.get(url, params(token, { endpoint: "search", mode: mode }));
  check(response, { "search 200": (r) => r.status === 200 });
  return response;
}
