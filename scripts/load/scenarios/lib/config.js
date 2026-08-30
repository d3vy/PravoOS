export const BASE_URL = __ENV.LOAD_BASE_URL || "http://api-gateway:8080";
export const LAWYERS = Number(__ENV.LOAD_LAWYERS || 50);
export const PASSWORD = __ENV.LOAD_USER_PASSWORD || "LoadTest!2026";
export const VUS = Number(__ENV.LOAD_VUS || 20);
export const DURATION = __ENV.LOAD_DURATION || "3m";
export const RAMP_MAX_VUS = Number(__ENV.LOAD_RAMP_MAX_VUS || 120);

export function userEmail(index) {
  return `load-lawyer-${(index % LAWYERS) + 1}@load.pravoos.test`;
}

// k6 0.52 компилирует скрипты через babel без object-spread, поэтому параметры
// запроса собираются функцией, а не рассыпаются точками в каждом сценарии.
export function params(token, tags, extraHeaders) {
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
  if (extraHeaders) {
    Object.keys(extraHeaders).forEach((key) => {
      headers[key] = extraHeaders[key];
    });
  }
  return { headers: headers, tags: tags || {} };
}
