package com.example.newsbot.site;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.example.newsbot.delivery.DigestTextFormatter;
import com.example.newsbot.delivery.MessageText;
import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;
import org.springframework.util.StringUtils;

/**
 * 카카오톡 안에서 열기 좋은 모바일용 뉴스 페이지 (외부 파일 없이 HTML 하나).
 */
public class NewsPageRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

    private final SiteProperties site;
    private final ZoneId zone;

    public NewsPageRenderer(SiteProperties site, ZoneId zone) {
        this.site = site;
        this.zone = zone;
    }

    public String render(NewsDigest digest, Instant updatedAt) {
        String dateLabel = MessageText.dateLabel(digest.date());
        String pageTitle = dateLabel + " " + site.title();
        String description = digest.sections().stream()
                .flatMap(s -> s.articles().stream())
                .limit(3)
                .map(NewsArticle::title)
                .collect(Collectors.joining(" · "));

        StringBuilder nav = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < digest.sections().size(); i++) {
            NewsSection section = digest.sections().get(i);
            String id = "s" + (i + 1);
            nav.append("<a href=\"#").append(id).append("\">").append(esc(section.name())).append("</a>");
            body.append("<section id=\"").append(id).append("\"><h2>").append(esc(section.name())).append("</h2><ol>");
            for (NewsArticle a : section.articles()) {
                String meta = MessageText.sourceLine(a.source(), a.publishedAt(), zone);
                body.append("<li><a href=\"").append(esc(safeUrl(a.link())))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                        .append(esc(a.title())).append("</a>");
                if (meta != null) {
                    body.append("<span class=\"meta\">").append(esc(meta)).append("</span>");
                }
                body.append("</li>");
            }
            body.append("</ol></section>");
        }

        String copyText = DigestTextFormatter.format(digest, true, Integer.MAX_VALUE).text()
                + (StringUtils.hasText(site.pageUrl()) ? "\n\n전체 보기: " + site.pageUrl() : "");
        String channelButton = StringUtils.hasText(site.channelUrl())
                ? "<a class=\"btn primary\" href=\"" + esc(safeUrl(site.channelUrl())) + "\">채널 추가하고 매일 받기</a>"
                : "";

        Map<String, String> values = Map.of(
                "TITLE", esc(pageTitle),
                "SITE_TITLE", esc(site.title()),
                "DESCRIPTION", esc(description),
                "DATE", esc(dateLabel),
                "UPDATED", esc(UPDATED.format(updatedAt.atZone(zone)) + " 업데이트"),
                "COUNT", String.valueOf(digest.articleCount()),
                "NAV", nav.toString(),
                "CHANNEL_BUTTON", channelButton,
                "SECTIONS", body.toString(),
                "COPY_TEXT", esc(copyText));
        return fill(TEMPLATE, values);
    }

    /** 템플릿을 한 번만 훑어 치환한다. 기사 제목에 {{...}} 가 있어도 다시 치환되지 않는다. */
    static String fill(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() * 2);
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalStateException("템플릿 값 누락: " + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** RSS 에 javascript: 같은 링크가 섞여 와도 페이지에 넣지 않는다. */
    static String safeUrl(String url) {
        if (url == null) {
            return "#";
        }
        String u = url.trim();
        return u.startsWith("https://") || u.startsWith("http://") ? u : "#";
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static final String TEMPLATE = """
            <!doctype html>
            <html lang="ko">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>{{TITLE}}</title>
            <meta name="description" content="{{DESCRIPTION}}">
            <meta property="og:type" content="website">
            <meta property="og:title" content="{{TITLE}}">
            <meta property="og:description" content="{{DESCRIPTION}}">
            <style>
            :root{--bg:#f6f7f9;--card:#fff;--text:#1d1f23;--muted:#6b7280;--line:#e6e8ec;--accent:#3c5bd8;--chip:#eef1fb;--kakao:#fee500;--kakao-text:#191919}
            @media (prefers-color-scheme:dark){:root{--bg:#121316;--card:#1b1d22;--text:#eceef2;--muted:#9aa1ad;--line:#2a2d34;--accent:#8ea2ff;--chip:#232838}}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font:16px/1.55 -apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo","Noto Sans KR","Malgun Gothic",sans-serif;-webkit-text-size-adjust:100%}
            .wrap{max-width:680px;margin:0 auto;padding:20px 16px 40px}
            header h1{margin:0;font-size:1.4rem;letter-spacing:-.02em;word-break:keep-all}
            header .sub{margin:4px 0 0;color:var(--muted);font-size:.9rem}
            .actions{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0 4px}
            .btn{appearance:none;border:1px solid var(--line);background:var(--card);color:var(--text);border-radius:999px;padding:9px 16px;font:inherit;font-size:.9rem;cursor:pointer;text-decoration:none}
            .btn.primary{background:var(--kakao);color:var(--kakao-text);border-color:var(--kakao);font-weight:600}
            nav{position:sticky;top:0;z-index:1;display:flex;gap:6px;overflow-x:auto;padding:12px 0;margin:0 -16px;padding-left:16px;padding-right:16px;background:var(--bg);scrollbar-width:none}
            nav::-webkit-scrollbar{display:none}
            nav a{flex:none;background:var(--chip);color:var(--accent);text-decoration:none;border-radius:999px;padding:6px 14px;font-size:.9rem;font-weight:600}
            section{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:6px 16px 8px;margin-top:14px;scroll-margin-top:64px}
            h2{font-size:1.05rem;margin:12px 0 4px}
            ol{list-style:none;margin:0;padding:0;counter-reset:n}
            li{counter-increment:n;padding:12px 0 12px 30px;position:relative;border-top:1px solid var(--line)}
            li:first-child{border-top:0}
            li::before{content:counter(n);position:absolute;left:0;top:12px;width:20px;height:20px;border-radius:6px;background:var(--chip);color:var(--accent);font-size:.75rem;font-weight:700;display:flex;align-items:center;justify-content:center}
            li a{color:var(--text);text-decoration:none;font-weight:600;word-break:keep-all;overflow-wrap:anywhere}
            li a:active{color:var(--accent)}
            .meta{display:block;color:var(--muted);font-size:.82rem;margin-top:2px}
            footer{color:var(--muted);font-size:.78rem;margin-top:24px;line-height:1.6}
            .toast{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:#111;color:#fff;padding:10px 16px;border-radius:999px;font-size:.88rem;opacity:0;transition:opacity .2s;pointer-events:none}
            .toast.show{opacity:.92}
            textarea{position:absolute;left:-9999px;top:0}
            </style>
            </head>
            <body>
            <div class="wrap">
            <header>
            <h1>📰 {{DATE}} {{SITE_TITLE}}</h1>
            <p class="sub">{{UPDATED}} · 기사 {{COUNT}}건</p>
            <div class="actions">{{CHANNEL_BUTTON}}<button class="btn" id="share" type="button">공유하기</button><button class="btn" id="copy" type="button">전체 복사</button></div>
            </header>
            <nav>{{NAV}}</nav>
            {{SECTIONS}}
            <footer>기사 제목과 원문 링크만 모아 보여 줍니다. 기사의 저작권은 각 언론사에 있습니다.<br>매일 아침 자동으로 새로 만들어집니다.</footer>
            </div>
            <textarea id="copy-text" readonly aria-hidden="true" tabindex="-1">{{COPY_TEXT}}</textarea>
            <div class="toast" id="toast" role="status"></div>
            <script>
            (function(){
              var toastEl=document.getElementById('toast'),timer;
              function toast(m){toastEl.textContent=m;toastEl.classList.add('show');clearTimeout(timer);timer=setTimeout(function(){toastEl.classList.remove('show')},1800)}
              function fallbackCopy(text){var t=document.getElementById('copy-text');var old=t.value;t.value=text;t.select();t.setSelectionRange(0,text.length);var ok=false;try{ok=document.execCommand('copy')}catch(e){}t.value=old;return ok}
              function copy(text,done){if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(text).then(function(){toast(done)},function(){toast(fallbackCopy(text)?done:'복사하지 못했어요')})}else{toast(fallbackCopy(text)?done:'복사하지 못했어요')}}
              var pageUrl=location.href.split('#')[0];
              document.getElementById('share').addEventListener('click',function(){
                if(navigator.share){navigator.share({title:document.title,url:pageUrl}).catch(function(){})}
                else{copy(pageUrl,'링크를 복사했어요')}
              });
              document.getElementById('copy').addEventListener('click',function(){copy(document.getElementById('copy-text').value,'뉴스 목록을 복사했어요')});
            })();
            </script>
            </body>
            </html>
            """;
}
