const http = require("http");

const port = Number(process.env.MOCK_PORT || 8090);
const ttftMs = Number(process.env.MOCK_TTFT_MS || 400);
const chunkMs = Number(process.env.MOCK_CHUNK_MS || 25);
const chunkCount = Number(process.env.MOCK_CHUNKS || 60);
const completionMs = Number(process.env.MOCK_COMPLETION_MS || 250);
const embeddingMs = Number(process.env.MOCK_EMBEDDING_MS || 40);
const embeddingDimensions = Number(process.env.MOCK_EMBEDDING_DIMENSIONS || 1536);
const jitterPct = Number(process.env.MOCK_JITTER_PCT || 20);
const guardMaxTokens = Number(process.env.MOCK_GUARD_MAX_TOKENS || 8);

const stats = { chat: 0, stream: 0, guard: 0, embeddings: 0, aborted: 0 };

const CHUNK_TEXT =
  "Согласно статье 309 ГК РФ обязательства должны исполняться надлежащим образом. ";

function jittered(baseMs) {
  if (jitterPct <= 0) {
    return baseMs;
  }
  const spread = (baseMs * jitterPct) / 100;
  return Math.max(0, Math.round(baseMs - spread + Math.random() * spread * 2));
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let raw = "";
    request.on("data", (piece) => {
      raw += piece;
    });
    request.on("end", () => {
      try {
        resolve(raw.length === 0 ? {} : JSON.parse(raw));
      } catch (error) {
        reject(error);
      }
    });
    request.on("error", reject);
  });
}

function sendJson(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "Content-Type": "application/json",
    "Content-Length": Buffer.byteLength(body),
  });
  response.end(body);
}

function isGuardRequest(payload) {
  return Number(payload.max_tokens || 0) > 0 && Number(payload.max_tokens) <= guardMaxTokens;
}

function usage(completionTokens) {
  const promptTokens = 900;
  return {
    prompt_tokens: promptTokens,
    completion_tokens: completionTokens,
    total_tokens: promptTokens + completionTokens,
  };
}

async function handleCompletion(payload, response) {
  const guard = isGuardRequest(payload);
  stats[guard ? "guard" : "chat"] += 1;
  await sleep(jittered(guard ? Math.min(completionMs, 120) : completionMs));
  const content = guard ? "YES" : CHUNK_TEXT.repeat(3).trim();
  sendJson(response, 200, {
    id: "chatcmpl-load",
    object: "chat.completion",
    model: payload.model || "mock",
    choices: [{ index: 0, message: { role: "assistant", content }, finish_reason: "stop" }],
    usage: usage(guard ? 1 : 120),
  });
}

async function handleStream(payload, response) {
  stats.stream += 1;
  response.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });

  let clientGone = false;
  response.on("close", () => {
    if (!response.writableEnded) {
      clientGone = true;
      stats.aborted += 1;
    }
  });

  const write = (chunk) => response.write(`data: ${JSON.stringify(chunk)}\n\n`);

  await sleep(jittered(ttftMs));
  for (let index = 0; index < chunkCount && !clientGone; index += 1) {
    write({
      id: "chatcmpl-load",
      object: "chat.completion.chunk",
      model: payload.model || "mock",
      choices: [{ index: 0, delta: { content: CHUNK_TEXT }, finish_reason: null }],
    });
    await sleep(jittered(chunkMs));
  }
  if (clientGone) {
    return;
  }
  write({
    id: "chatcmpl-load",
    object: "chat.completion.chunk",
    model: payload.model || "mock",
    choices: [{ index: 0, delta: {}, finish_reason: "stop" }],
    usage: usage(chunkCount * 12),
  });
  response.write("data: [DONE]\n\n");
  response.end();
}

async function handleEmbeddings(payload, response) {
  stats.embeddings += 1;
  await sleep(jittered(embeddingMs));
  const inputs = Array.isArray(payload.input) ? payload.input : [payload.input || ""];
  const data = inputs.map((text, index) => ({
    object: "embedding",
    index,
    embedding: pseudoVector(String(text)),
  }));
  sendJson(response, 200, {
    object: "list",
    data,
    model: payload.model || "mock-embedding",
    usage: { prompt_tokens: inputs.length * 40, total_tokens: inputs.length * 40 },
  });
}

function pseudoVector(text) {
  let seed = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    seed = Math.imul(seed ^ text.charCodeAt(index), 16777619) >>> 0;
  }
  const vector = new Array(embeddingDimensions);
  let magnitude = 0;
  for (let index = 0; index < embeddingDimensions; index += 1) {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    const value = seed / 4294967295 - 0.5;
    vector[index] = value;
    magnitude += value * value;
  }
  const norm = Math.sqrt(magnitude) || 1;
  return vector.map((value) => Number((value / norm).toFixed(6)));
}

const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/healthz") {
    sendJson(response, 200, { status: "UP" });
    return;
  }
  if (request.method === "GET" && request.url === "/stats") {
    sendJson(response, 200, stats);
    return;
  }
  if (request.method !== "POST") {
    sendJson(response, 404, { error: { message: "not found" } });
    return;
  }

  let payload;
  try {
    payload = await readBody(request);
  } catch (error) {
    sendJson(response, 400, { error: { message: `bad json: ${error.message}` } });
    return;
  }

  try {
    if (request.url.endsWith("/chat/completions")) {
      await (payload.stream === true
        ? handleStream(payload, response)
        : handleCompletion(payload, response));
      return;
    }
    if (request.url.endsWith("/embeddings")) {
      await handleEmbeddings(payload, response);
      return;
    }
    sendJson(response, 404, { error: { message: `unmapped path ${request.url}` } });
  } catch (error) {
    if (!response.headersSent) {
      sendJson(response, 500, { error: { message: error.message } });
    } else {
      response.end();
    }
  }
});

server.keepAliveTimeout = 120000;
server.headersTimeout = 125000;
server.requestTimeout = 0;
server.listen(port, "0.0.0.0", () => {
  process.stdout.write(
    `mock-openai listening on ${port}: ttft=${ttftMs}ms chunk=${chunkMs}ms x${chunkCount}\n`
  );
});
