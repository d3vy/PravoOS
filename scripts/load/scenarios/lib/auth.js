import http from "k6/http";
import { fail } from "k6";
import { BASE_URL, LAWYERS, PASSWORD, userEmail } from "./config.js";

export function login(email, tag) {
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" }, tags: { endpoint: tag || "auth_login" } }
  );
  if (response.status !== 200) {
    return { status: response.status, token: null, response };
  }
  const body = response.json();
  return { status: 200, token: body.accessToken, response };
}

// Пул токенов готовится один раз в setup(): бэкенд считает bcrypt(12) на каждый
// логин, и без пула сценарий чтения мерил бы стоимость входа, а не свои эндпоинты.
export function tokenPool() {
  const tokens = [];
  for (let index = 0; index < LAWYERS; index += 1) {
    const result = login(userEmail(index), "setup_login");
    if (result.token === null) {
      fail(
        `setup: логин ${userEmail(index)} вернул ${result.status}. ` +
          "Сид не выполнен или сработал лимитер логинов (ip_rate:login:*)."
      );
    }
    tokens.push(result.token);
  }
  return tokens;
}

export function tokenFor(tokens) {
  return tokens[(__VU + __ITER) % tokens.length];
}
