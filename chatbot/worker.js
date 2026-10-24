/**
 * 카카오톡 채널 뉴스 챗봇 (카카오 i 오픈빌더 "스킬 서버")
 *
 * Cloudflare Workers 무료 플랜에서 돌아갑니다. 이 파일 내용을 통째로
 * Cloudflare Workers 코드 편집 화면에 붙여 넣고 [Deploy] 를 누르면 됩니다.
 *
 * 동작: 친구가 채팅방에서 "정치", "경제" 처럼 말하면
 * 매일 아침 GitHub Pages 에 올라오는 news.json 을 읽어 기사 목록 카드로 답합니다.
 */
const NEWS_JSON_URL = "https://lshxxn.github.io/SH_TEST/news.json";

const MAX_ITEMS = 5;          // 리스트 카드 한 장에 넣을 기사 수
const MAX_QUICK_REPLIES = 10; // 바로가기 버튼 최대 개수

export default {
  async fetch(request) {
    if (request.method !== "POST") {
      return new Response("뉴스 챗봇 스킬 서버가 동작 중입니다.", {
        headers: { "content-type": "text/plain; charset=utf-8" },
      });
    }
    let utterance = "";
    try {
      const body = await request.json();
      utterance = String(body?.userRequest?.utterance ?? "");
    } catch (_) {
      // 본문이 비어 있어도 기본 응답을 돌려준다
    }
    const news = await loadNews();
    return Response.json(buildReply(news, utterance));
  },
};

async function loadNews() {
  try {
    // 5분 단위로 주소를 바꿔 오래된 캐시를 피한다. 카카오는 5초 안에 답을 받아야 하므로 3초에서 끊는다.
    const url = NEWS_JSON_URL + "?t=" + Math.floor(Date.now() / 300000);
    const res = await fetch(url, { signal: AbortSignal.timeout(3000) });
    return res.ok ? await res.json() : null;
  } catch (_) {
    return null;
  }
}

export function buildReply(news, utterance) {
  const sections = Array.isArray(news?.sections) ? news.sections.filter((s) => s?.articles?.length) : [];
  if (sections.length === 0) {
    return simpleText("오늘의 뉴스를 준비하고 있어요. 잠시 후 다시 시도해 주세요.");
  }
  const index = Math.max(0, findSection(sections, utterance));
  const section = sections[index];

  const card = {
    header: { title: truncate(`📰 ${news.dateLabel ?? ""} ${section.name}`.trim(), 30) },
    items: section.articles.slice(0, MAX_ITEMS).map((a) => {
      const item = { title: truncate(a.title, 50), link: { web: a.link } };
      if (a.meta) item.description = truncate(a.meta, 40);
      return item;
    }),
  };
  if (news.pageUrl) {
    card.buttons = [{ label: "전체 뉴스 보기", action: "webLink", webLinkUrl: `${news.pageUrl}#s${index + 1}` }];
  }

  return {
    version: "2.0",
    template: {
      outputs: [{ listCard: card }],
      quickReplies: sections.slice(0, MAX_QUICK_REPLIES).map((s) => ({
        label: truncate(s.name, 14),
        action: "message",
        messageText: s.name,
      })),
    },
  };
}

/** "정치 뉴스 알려줘" → 정치, "IT" 나 "과학" → IT·과학. 못 찾으면 -1 (첫 섹션을 보여 준다). */
export function findSection(sections, utterance) {
  const said = String(utterance ?? "").replace(/\s+/g, "").toLowerCase();
  if (!said) return -1;
  return sections.findIndex((s) =>
    String(s.name)
      .split(/[·\s/,]+/)
      .map((t) => t.trim().toLowerCase())
      .filter((t) => t && t !== "뉴스")
      .some((t) => said.includes(t)),
  );
}

function simpleText(text) {
  return { version: "2.0", template: { outputs: [{ simpleText: { text } }] } };
}

function truncate(s, max) {
  const chars = Array.from(String(s ?? ""));
  return chars.length <= max ? chars.join("") : chars.slice(0, max - 1).join("") + "…";
}
