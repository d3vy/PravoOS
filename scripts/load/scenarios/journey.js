// Горячий путь 1: логин → дашборд → список дел → задачи дела.
// Вход вынесен в отдельный сценарий с низкой интенсивностью: bcrypt(12) в user-service
// стоит сотни миллисекунд CPU и иначе перекрывает собой стоимость чтения в ai-service.
import http from "k6/http";
import { check, group } from "k6";
import { Trend } from "k6/metrics";
import { BASE_URL, DURATION, VUS, params, userEmail } from "./lib/config.js";
import { login, tokenFor, tokenPool } from "./lib/auth.js";

const loginDuration = new Trend("pravoos_login_duration", true);
const dashboardDuration = new Trend("pravoos_dashboard_duration", true);
const caseListDuration = new Trend("pravoos_case_list_duration", true);
const caseTasksDuration = new Trend("pravoos_case_tasks_duration", true);

export const options = {
  // setup() логинит весь пул юристов, а bcrypt(12) стоит сотни миллисекунд на вход.
  setupTimeout: "10m",
  scenarios: {
    login: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.LOAD_LOGIN_RPS || 2),
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: "loginOnly",
    },
    browse: {
      executor: "constant-vus",
      vus: VUS,
      duration: DURATION,
      exec: "browse",
    },
  },
  thresholds: {
    "http_req_failed{endpoint:dashboard}": ["rate<0.01"],
    "http_req_failed{endpoint:case_list}": ["rate<0.01"],
    "http_req_failed{endpoint:case_tasks}": ["rate<0.01"],
    "http_req_failed{endpoint:auth_login}": ["rate<0.01"],
    pravoos_dashboard_duration: ["p(95)<1500", "p(99)<3000"],
    pravoos_case_list_duration: ["p(95)<800", "p(99)<1500"],
    pravoos_login_duration: ["p(95)<2000"],
  },
};

export function setup() {
  return { tokens: tokenPool() };
}

export function loginOnly() {
  const result = login(userEmail(__VU + __ITER), "auth_login");
  loginDuration.add(result.response.timings.duration);
  check(result, { "login 200": (r) => r.status === 200 });
}

export function browse(data) {
  const token = tokenFor(data.tokens);

  group("dashboard", () => {
    const response = http.get(
      `${BASE_URL}/api/ai/dashboard`,
      params(token, { endpoint: "dashboard" })
    );
    dashboardDuration.add(response.timings.duration);
    check(response, {
      "dashboard 200": (r) => r.status === 200,
      "dashboard has pipeline": (r) => r.status === 200 && r.json("pipeline") !== undefined,
    });
  });

  let caseId = null;
  group("case_list", () => {
    const response = http.get(
      `${BASE_URL}/api/ai/cases?page=0&size=20`,
      params(token, { endpoint: "case_list" })
    );
    caseListDuration.add(response.timings.duration);
    const ok = check(response, {
      "case list 200": (r) => r.status === 200,
      "case list not empty": (r) => r.status === 200 && r.json().length > 0,
    });
    if (ok) {
      const items = response.json();
      caseId = items[__ITER % items.length].id;
    }
  });

  if (caseId === null) {
    return;
  }

  group("case_tasks", () => {
    const response = http.get(
      `${BASE_URL}/api/ai/cases/${caseId}/tasks`,
      params(token, { endpoint: "case_tasks" })
    );
    caseTasksDuration.add(response.timings.duration);
    check(response, { "case tasks 200": (r) => r.status === 200 });
  });
}
