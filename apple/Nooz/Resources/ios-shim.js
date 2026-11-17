// ios-shim.js -- iOS-shell-only. Loaded as a WKUserScript at document start,
// AFTER Readability.js (so `Readability` is a global), into the page the shell
// serves from nooz://app/.
//
// The hosted web reader calls two same-origin serverless helpers, /api/feed and
// /api/article (web/api/*.js). Inside the iOS shell there is no server: the
// shell's WKURLSchemeHandler answers /api/feed natively (URLSession, so no CORS
// and no proxy hop) and /api/page natively (raw, size-capped HTML). This shim
// restores the one thing that cannot happen natively -- Readability extraction
// -- by answering /api/article in the page itself: fetch /api/page, run
// Readability over a DOMParser document, return the SAME JSON shape as
// web/api/article.js. web/ is not modified; the parity test
// (apple/tests/shim-parity.test.mjs) pins the two together.
(function (root) {
  'use strict';

  function injectBase(html, url) {
    var baseTag = '<base href="' + String(url).replace(/"/g, '&quot;') + '">';
    if (/<head[^>]*>/i.test(html)) return html.replace(/<head[^>]*>/i, function (m) { return m + baseTag; });
    if (/<html[^>]*>/i.test(html)) return html.replace(/<html[^>]*>/i, function (m) { return m + '<head>' + baseTag + '</head>'; });
    return baseTag + html;
  }

  function metaContent(document, prop) {
    var el = document.querySelector('meta[property="' + prop + '"]') ||
             document.querySelector('meta[name="' + prop + '"]');
    return el ? el.getAttribute('content') : null;
  }

  function absolute(src, base) {
    if (!src) return null;
    try {
      var u = new URL(src, base);
      return u.protocol === 'https:' ? u.href : null;
    } catch (_e) {
      return null;
    }
  }

  function firstImage(contentHtml, base) {
    var m = contentHtml.match(/<img[^>]+src\s*=\s*["']([^"']+)["']/i);
    return m ? absolute(m[1], base) : null;
  }

  /**
   * Pure extraction step, mirroring the tail of web/api/article.js.
   * `parse(html)` must return a Document (DOMParser in the page, linkedom in tests).
   * Returns the JSON body the reader expects.
   */
  function extract(html, pageUrl, parse, ReadabilityCtor) {
    var document = parse(injectBase(html, pageUrl));
    var ogImage = metaContent(document, 'og:image') || metaContent(document, 'twitter:image');
    var article = new ReadabilityCtor(document, { charThreshold: 200 }).parse();
    if (!article || !article.content) return { error: 'Could not extract article', html: null };
    var leadImage = absolute(ogImage, pageUrl) || firstImage(article.content, pageUrl);
    return {
      title: article.title || null,
      byline: article.byline || null,
      html: article.content,
      leadImage: leadImage || null
    };
  }

  function jsonResponse(body) {
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  /** Wraps fetch so /api/article is answered locally; everything else passes through. */
  function install(win, ReadabilityCtor) {
    var realFetch = win.fetch.bind(win);
    win.fetch = function (input, init) {
      var raw = typeof input === 'string' ? input : (input && input.url) || '';
      var m = /(?:^|\/)api\/article\?url=([^&]+)/.exec(raw);
      if (!m) return realFetch(input, init);
      var target = decodeURIComponent(m[1]);
      return realFetch('/api/page?url=' + encodeURIComponent(target), { cache: 'no-store' })
        .then(function (res) {
          return res.json().then(function (page) {
            // Same contract as api/article.js: per-item failures travel as a
            // normal 200 {error, html:null}, never an HTTP error.
            if (!page || page.html == null) return jsonResponse({ error: (page && page.error) || 'Upstream unreachable', html: null });
            try {
              return jsonResponse(extract(page.html, page.finalUrl || target, function (h) {
                return new win.DOMParser().parseFromString(h, 'text/html');
              }, ReadabilityCtor));
            } catch (_e) {
              return jsonResponse({ error: 'Extraction failed', html: null });
            }
          });
        })
        .catch(function () { return jsonResponse({ error: 'Upstream unreachable', html: null }); });
    };
  }

  var api = { injectBase: injectBase, metaContent: metaContent, absolute: absolute, firstImage: firstImage, extract: extract, install: install };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root && typeof root.Readability === 'function' && root.document && !root.__noozShimInstalled) {
    root.__noozShimInstalled = true;
    install(root, root.Readability);
  }
})(typeof window !== 'undefined' ? window : undefined);
