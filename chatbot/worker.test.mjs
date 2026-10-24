// 실행: node --test chatbot/
import { test } from "node:test";
import assert from "node:assert/strict";
import worker, { buildReply, findSection } from "./worker.js";

const news = {
  date: "2026-10-24",
  dateLabel: "10월 24일(토)",
  pageUrl: "https://lshxxn.github.io/SH_TEST/",
  sections: [
    { name: "주요 뉴스", articles: [{ title: "반도체 수출 증가", link: "https://n/1", meta: "연합뉴스 · 08:12" }] },
    { name: "정치", articles: Array.from({ length: 7 }, (_, i) => ({ title: `정치 기사 ${i + 1}`, link: `https://n/p${i}`, meta: "KBS" })) },
    { name: "IT·과학", articles: [{ title: "새 스마트폰 공개", link: "https://n/t", meta: null }] },
  ],
};

test("말한 분야를 찾는다", () => {
  assert.equal(findSection(news.sections, "정치"), 1);
  assert.equal(findSection(news.sections, "정치 뉴스 알려줘"), 1);
  assert.equal(findSection(news.sections, "과학"), 2);
  assert.equal(findSection(news.sections, "it"), 2);
  assert.equal(findSection(news.sections, "뉴스"), -1);
  assert.equal(findSection(news.sections, ""), -1);
});

test("분야 기사를 리스트 카드로, 분야 목록을 바로가기 버튼으로 답한다", () => {
  const reply = buildReply(news, "정치");
  assert.equal(reply.version, "2.0");
  const card = reply.template.outputs[0].listCard;
  assert.equal(card.header.title, "📰 10월 24일(토) 정치");
  assert.equal(card.items.length, 5); // 최대 5개
  assert.deepEqual(card.items[0], { title: "정치 기사 1", link: { web: "https://n/p0" }, description: "KBS" });
  // 출처가 없는 기사는 설명 칸을 넣지 않는다
  const it = buildReply(news, "IT").template.outputs[0].listCard.items[0];
  assert.deepEqual(it, { title: "새 스마트폰 공개", link: { web: "https://n/t" } });
  assert.deepEqual(card.buttons, [
    { label: "전체 뉴스 보기", action: "webLink", webLinkUrl: "https://lshxxn.github.io/SH_TEST/#s2" },
  ]);
  assert.deepEqual(reply.template.quickReplies.map((q) => q.messageText), ["주요 뉴스", "정치", "IT·과학"]);
  assert.ok(reply.template.quickReplies.every((q) => q.action === "message" && q.label.length <= 14));
});

test("모르는 말에는 주요 뉴스를 보여 준다", () => {
  const card = buildReply(news, "안녕하세요").template.outputs[0].listCard;
  assert.equal(card.header.title, "📰 10월 24일(토) 주요 뉴스");
});

test("뉴스 파일을 못 읽으면 안내 문구로 답한다", () => {
  const reply = buildReply(null, "정치");
  assert.match(reply.template.outputs[0].simpleText.text, /준비하고 있어요/);
});

test("카카오 요청을 받아 JSON 으로 답한다", async () => {
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    assert.match(String(url), /^https:\/\/lshxxn\.github\.io\/SH_TEST\/news\.json\?t=\d+$/);
    return new Response(JSON.stringify(news), { headers: { "content-type": "application/json" } });
  };
  try {
    const kakaoRequest = new Request("https://bot.example.workers.dev/", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ userRequest: { utterance: "경제는?", user: { id: "u1" } }, action: { params: {} } }),
    });
    const res = await worker.fetch(kakaoRequest);
    assert.equal(res.status, 200);
    assert.match(res.headers.get("content-type"), /application\/json/);
    const body = await res.json();
    // "경제" 섹션이 없으면 주요 뉴스
    assert.equal(body.template.outputs[0].listCard.header.title, "📰 10월 24일(토) 주요 뉴스");
  } finally {
    globalThis.fetch = realFetch;
  }
});

test("브라우저로 열면 동작 중이라고 알려 준다", async () => {
  const res = await worker.fetch(new Request("https://bot.example.workers.dev/"));
  assert.equal(res.status, 200);
  assert.match(await res.text(), /동작 중/);
});
