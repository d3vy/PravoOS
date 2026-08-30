// Горячий путь 3: чат со стримом (SSE).
// k6 не читает поток по событиям, зато http_req_waiting — это время до первого байта,
// то есть до первого события SSE; http_req_duration — удержание соединения целиком.
// Ступенчатый ramp ищет потолок: chatStreamExecutor в ai-service работает с AbortPolicy
// (503 при переполнении), llmStreamExecutor в llm-service — тоже, а Hikari отдаёт
// соединения тем же запросам. Что упрётся первым, покажут hikaricp_connections_pending
// и pravoos.agent.streams.active из snapshot.sh.
import http from "k6/http";
import { check } from "k6";
import { Counter, Trend } from "k6/metrics";
import { BASE_URL, RAMP_MAX_VUS, params } from "./lib/config.js";
import { tokenFor, tokenPool } from "./lib/auth.js";

const timeToFirstEvent = new Trend("pravoos_stream_ttfb", true);
const streamDuration = new Trend("pravoos_stream_duration", true);
const streamRejected = new Counter("pravoos_stream_rejected");
const streamIncomplete = new Counter("pravoos_stream_incomplete");

const QUESTIONS = [
  "Какой срок исковой давности по договору подряда?",
  "Как оспорить сделку должника при банкротстве?",
  "Можно ли снизить неустойку по статье 333 ГК РФ?",
  "Что входит в претензионный порядок по арбитражному спору?",
  "Какие основания для субсидиарной ответственности руководителя?",
  "Как расторгнуть договор аренды досрочно?",
];

export const options = {
  // setup() логинит весь пул юристов, а bcrypt(12) стоит сотни миллисекунд на вход.
  setupTimeout: "10m",
  scenarios: {
    stream: {
      executor: "ramping-vus",
      startVUs: 1,
      stages: [
        { duration: "1m", target: Math.max(2, Math.round(RAMP_MAX_VUS * 0.1)) },
        { duration: "2m", target: Math.max(4, Math.round(RAMP_MAX_VUS * 0.3)) },
        { duration: "2m", target: Math.max(8, Math.round(RAMP_MAX_VUS * 0.6)) },
        { duration: "2m", target: RAMP_MAX_VUS },
        { duration: "1m", target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    pravoos_stream_ttfb: ["p(95)<5000"],
    "http_req_failed{endpoint:chat_stream}": ["rate<0.05"],
  },
};

export function setup() {
  return { tokens: tokenPool() };
}

export default function (data) {
  const streamParams = params(tokenFor(data.tokens), { endpoint: "chat_stream" }, {
    Accept: "text/event-stream",
  });
  streamParams.timeout = __ENV.LOAD_STREAM_TIMEOUT || "180s";
  const message = `${QUESTIONS[__ITER % QUESTIONS.length]} (прогон ${__VU}-${__ITER})`;

  const response = http.post(
    `${BASE_URL}/api/ai/chat/stream`,
    JSON.stringify({ message: message }),
    streamParams
  );

  if (response.status === 503 || response.status === 429) {
    streamRejected.add(1);
    return;
  }

  timeToFirstEvent.add(response.timings.waiting);
  streamDuration.add(response.timings.duration);

  const completed = check(response, {
    "stream 200": (r) => r.status === 200,
    "stream finished with done": (r) => r.body !== null && r.body.includes("event:done"),
    "stream carried tokens": (r) => r.body !== null && r.body.includes("event:token"),
  });
  if (!completed) {
    streamIncomplete.add(1);
  }
}
