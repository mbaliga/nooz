// starters.js -- a short, pre-checked list of real, currently-live public
// RSS/Atom feeds a new reader can add with one tap, spanning Europe, the
// Americas, Asia, the Middle East, Africa, and Australia/Pacific.
//
// Read this before assuming a starter "doesn't work":
//
// This app has no backend and no proxy -- addStarter()/fetchFeed() call
// fetch(source.url) directly from the reader's own browser (that's the
// point: no account, no sync, nothing routed through a server of ours). A
// feed only comes through if the publisher's server sends an
// Access-Control-Allow-Origin (ACAO) header that permits a browser to read
// the response cross-origin. Most outlets built their public RSS for
// server-side crawlers and native/desktop readers, not direct in-page
// `fetch()`, so a lot of otherwise-solid, currently-live feeds simply don't
// send that header -- and a plain browser fetch of them will fail with a
// CORS error. That is not a bug in fetchFeed(): it reports the failure
// per-source ("CORS blocked or unreachable") instead of hiding it, which is
// this app's whole "never silent" design principle applied to the one part
// of the stack it doesn't control.
//
// So every entry below carries a `// CORS:` note reflecting what was
// actually observed with curl (GET with an Origin header, checking the
// response's Access-Control-Allow-Origin) on 2026-07-19, alongside an
// `// HTTP:` note confirming the feed itself is live right now:
//   CORS: yes         -- server sent `Access-Control-Allow-Origin: *`
//                        (or otherwise unrestricted) on a live request.
//                        A direct browser fetch should work.
//   CORS: restricted  -- server sent an ACAO header, but scoped to one of
//                        its own origins (not `*`) -- it will NOT validate
//                        from this app's origin, so a direct fetch will
//                        still fail in-browser despite the feed being alive.
//   CORS: unconfirmed -- live (HTTP 200, a real RSS/RDF body came back) but
//                        no ACAO header was present at all. Per the default
//                        same-origin policy, a browser fetch will likely be
//                        blocked. Kept in the list anyway (dropping every
//                        feed without a confirmed ACAO would leave almost
//                        nothing, since most publishers simply don't set
//                        it) -- being upfront that it may not load, rather
//                        than silently omitting a real, standard feed, is
//                        the more honest default for this app.
//
// All 26 URLs below were confirmed with curl to return HTTP 200 with a
// parseable RSS 2.0 body on 2026-07-19. Feeds move and outlets change
// policy without notice -- if one goes dark or starts blocking, fetchFeed()
// surfaces that per-source rather than failing silently or crashing.

export const STARTERS = [
  // -- Europe ----------------------------------------------------------
  { title: 'BBC World News', url: 'https://feeds.bbci.co.uk/news/world/rss.xml', region: 'Europe' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'BBC Top Stories', url: 'https://feeds.bbci.co.uk/news/rss.xml', region: 'Europe' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'The Guardian -- World News', url: 'https://www.theguardian.com/world/rss', region: 'Europe' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'DW -- Top Stories', url: 'https://rss.dw.com/xml/rss-en-all', region: 'Europe' }, // HTTP: 200 · CORS: yes (ACAO: *)
  { title: 'France 24 -- English', url: 'https://www.france24.com/en/rss', region: 'Europe' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'RFI -- English', url: 'https://www.rfi.fr/en/rss', region: 'Europe' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'Euronews', url: 'https://www.euronews.com/rss?level=theme&name=news', region: 'Europe' }, // HTTP: 200 · CORS: restricted (ACAO scoped to euronews.com)

  // -- Americas ----------------------------------------------------------
  { title: 'New York Times -- World', url: 'https://rss.nytimes.com/services/xml/rss/nyt/World.xml', region: 'Americas' }, // HTTP: 200 · CORS: yes (ACAO: *)
  { title: 'CBC -- Top Stories', url: 'https://www.cbc.ca/cmlink/rss-topstories', region: 'Americas' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'Buenos Aires Times', url: 'https://www.batimes.com.ar/feed', region: 'Americas' }, // HTTP: 200 · CORS: unconfirmed

  // -- Asia ----------------------------------------------------------
  { title: 'The Japan Times', url: 'https://www.japantimes.co.jp/feed/', region: 'Asia' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'South China Morning Post', url: 'https://www.scmp.com/rss/91/feed', region: 'Asia' }, // HTTP: 200 (redirects to /rss/91/feed/) · CORS: unconfirmed
  { title: 'The Straits Times -- Asia', url: 'https://www.straitstimes.com/news/asia/rss.xml', region: 'Asia' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'The Times of India -- Top Stories', url: 'https://timesofindia.indiatimes.com/rssfeedstopstories.cms', region: 'Asia' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'The Hindu -- National', url: 'https://www.thehindu.com/news/national/feeder/default.rss', region: 'Asia' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'CNA (Channel NewsAsia)', url: 'https://www.channelnewsasia.com/rssfeeds/8395986', region: 'Asia' }, // HTTP: 200 (redirects to an api/v1 endpoint) · CORS: yes (ACAO: *)
  { title: 'Bangkok Post -- Top Stories', url: 'https://www.bangkokpost.com/rss/data/topstories.xml', region: 'Asia' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'Yonhap News Agency -- English', url: 'https://en.yna.co.kr/RSS/news.xml', region: 'Asia' }, // HTTP: 200 · CORS: unconfirmed

  // -- Middle East ----------------------------------------------------------
  { title: 'Al Jazeera English', url: 'https://www.aljazeera.com/xml/rss/all.xml', region: 'Middle East' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'Anadolu Agency -- English', url: 'https://www.aa.com.tr/en/rss/default?cat=live', region: 'Middle East' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'The Jerusalem Post -- Front Page', url: 'https://www.jpost.com/rss/rssfeedsfrontpage.aspx', region: 'Middle East' }, // HTTP: 200 · CORS: unconfirmed

  // -- Africa ----------------------------------------------------------
  { title: 'AllAfrica -- Latest', url: 'https://allafrica.com/tools/headlines/rdf/latest/headlines.rdf', region: 'Africa' }, // HTTP: 200 · CORS: unconfirmed. (URL says "rdf" for historical reasons; body served is actually RSS 2.0, a good honest example of a feed lying about its own format in its filename.)
  { title: 'Africanews', url: 'https://www.africanews.com/feed', region: 'Africa' }, // HTTP: 200 (redirects to /feed/) · CORS: unconfirmed

  // -- Australia / Pacific ----------------------------------------------------------
  { title: 'ABC News (Australia) -- Just In', url: 'https://www.abc.net.au/news/feed/51120/rss.xml', region: 'Australia/Pacific' }, // HTTP: 200 · CORS: unconfirmed
  { title: 'The Sydney Morning Herald', url: 'https://www.smh.com.au/rss/feed.xml', region: 'Australia/Pacific' }, // HTTP: 200 · CORS: yes (ACAO: *)
  { title: 'RNZ (Radio New Zealand) -- National', url: 'https://www.rnz.co.nz/rss/national.xml', region: 'Australia/Pacific' }, // HTTP: 200 · CORS: unconfirmed
];
