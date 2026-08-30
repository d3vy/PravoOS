// Нагрузочный сид переписок в pravoos_chat.
// Переменные задаются через --eval перед --file: LAWYERS, ORGS, CONVERSATIONS_PER_LAWYER,
// MESSAGES_PER_CONVERSATION, UUID_MODE ('java-legacy' | 'standard').
//
// Spring Boot 3.5 по умолчанию пишет java.util.UUID как BinData(3) в java-legacy порядке
// байт. Сид обязан повторить это байт-в-байт, иначе приложение не найдёт ни одной
// переписки, а нагрузочный профиль молча выродится в пустые ответы.

const lawyers = typeof LAWYERS === "undefined" ? 50 : LAWYERS;
const orgs = typeof ORGS === "undefined" ? 5 : ORGS;
const conversationsPerLawyer =
  typeof CONVERSATIONS_PER_LAWYER === "undefined" ? 40 : CONVERSATIONS_PER_LAWYER;
const messagesPerConversation =
  typeof MESSAGES_PER_CONVERSATION === "undefined" ? 8 : MESSAGES_PER_CONVERSATION;
const uuidMode = typeof UUID_MODE === "undefined" ? "java-legacy" : UUID_MODE;

const TOPICS = [
  "Банкротство контрагента",
  "Взыскание неустойки по договору подряда",
  "Аренда помещения и досрочное расторжение",
  "Корпоративный спор об исключении участника",
  "Оспаривание сделки должника",
  "Возмещение убытков по статье 393 ГК РФ",
  "Защита деловой репутации",
  "Субсидиарная ответственность руководителя",
];

const ANSWERS = [
  "Согласно статье 309 ГК РФ обязательства исполняются надлежащим образом.",
  "Претензионный порядок обязателен, срок ответа — тридцать календарных дней.",
  "Неустойка может быть снижена судом по статье 333 ГК РФ.",
  "Заявление подаётся в арбитражный суд по месту нахождения должника.",
];

const lawyerIds = typeof LAWYER_IDS === "undefined" ? [] : LAWYER_IDS;
const orgIds = typeof ORG_IDS === "undefined" ? [] : ORG_IDS;

if (lawyerIds.length === 0) {
  throw new Error("LAWYER_IDS is empty: seed.sh must pass ids from Postgres");
}

function uuidBinary(hexString) {
  const clean = hexString.replace(/-/g, "").toLowerCase();
  const bytes = [];
  for (let i = 0; i < 32; i += 2) {
    bytes.push(parseInt(clean.substr(i, 2), 16));
  }
  if (uuidMode === "standard") {
    return BinData(4, toBase64(bytes));
  }
  const legacy = bytes.slice(0, 8).reverse().concat(bytes.slice(8, 16).reverse());
  return BinData(3, toBase64(legacy));
}

function toBase64(bytes) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const b1 = i + 1 < bytes.length ? bytes[i + 1] : 0;
    const b2 = i + 2 < bytes.length ? bytes[i + 2] : 0;
    out += alphabet[b0 >> 2];
    out += alphabet[((b0 & 3) << 4) | (b1 >> 4)];
    out += i + 1 < bytes.length ? alphabet[((b1 & 15) << 2) | (b2 >> 6)] : "=";
    out += i + 2 < bytes.length ? alphabet[b2 & 63] : "=";
  }
  return out;
}

const chat = db.getSiblingDB("pravoos_chat");

const existing = chat.conversations.findOne({ lawyerId: { $exists: true }, title: { $not: /^Нагрузочный/ } });
if (existing && existing.lawyerId && existing.lawyerId.sub_type !== undefined) {
  const appSubtype = existing.lawyerId.sub_type;
  const seedSubtype = uuidMode === "standard" ? 4 : 3;
  if (appSubtype !== seedSubtype) {
    throw new Error(
      "UUID representation mismatch: приложение пишет BinData(" +
        appSubtype +
        "), сид пишет BinData(" +
        seedSubtype +
        "). Запусти с UUID_MODE=" +
        (appSubtype === 4 ? "standard" : "java-legacy")
    );
  }
}

const staleIds = [];
chat.conversations
  .find({ title: /^Нагрузочный диалог/ }, { _id: 1 })
  .forEach((doc) => staleIds.push(doc._id));
if (staleIds.length > 0) {
  chat.messages.deleteMany({ conversationId: { $in: staleIds } });
  chat.conversations.deleteMany({ _id: { $in: staleIds } });
}

let conversationCount = 0;
let messageCount = 0;

for (let l = 0; l < Math.min(lawyers, lawyerIds.length); l += 1) {
  const lawyerBinary = uuidBinary(lawyerIds[l]);
  const orgBinary = uuidBinary(orgIds[l % orgIds.length]);
  const conversations = [];
  const messages = [];

  for (let c = 1; c <= conversationsPerLawyer; c += 1) {
    const id = new ObjectId().toString();
    const createdAt = new Date(Date.now() - (c * 3600 + l * 60) * 1000);
    conversations.push({
      _id: id,
      lawyerId: lawyerBinary,
      orgId: orgBinary,
      title: "Нагрузочный диалог: " + TOPICS[c % TOPICS.length] + " №" + c,
      caseId: null,
      documentId: null,
      createdAt: createdAt,
      updatedAt: createdAt,
      deletedAt: null,
      _class: "com.pravoos.ai.core.internal.model.mongo.Conversation",
    });

    for (let m = 1; m <= messagesPerConversation; m += 1) {
      const isUser = m % 2 === 1;
      messages.push({
        _id: new ObjectId().toString(),
        conversationId: id,
        role: isUser ? "USER" : "ASSISTANT",
        content:
          "[load] " +
          (isUser ? "Вопрос: " + TOPICS[(c + m) % TOPICS.length] : ANSWERS[m % ANSWERS.length]),
        sources: [],
        toolSteps: [],
        rating: null,
        ratingComment: null,
        createdAt: new Date(createdAt.getTime() + m * 1000),
        _class: "com.pravoos.ai.core.internal.model.mongo.Message",
      });
    }
  }

  chat.conversations.insertMany(conversations, { ordered: false });
  chat.messages.insertMany(messages, { ordered: false });
  conversationCount += conversations.length;
  messageCount += messages.length;
}

print("conversations=" + conversationCount + " messages=" + messageCount + " uuidMode=" + uuidMode);
