// onboarding.js -- a brief, skippable first-run walkthrough. Shown once
// (a localStorage flag), plain text on the paper background, four short
// slides on what Nooz is and its three main surfaces. Skip is one tap away
// on every slide -- this never blocks getting to the actual reader.

const STORAGE_KEY = 'nooz-onboarded-v1';

const SLIDES = [
  {
    title: 'Nooz',
    body: 'Your source, your news. Nooz shows you exactly what flowed from the sources you chose, in order — never an algorithm’s pick.',
  },
  {
    title: 'The Paper',
    body: 'Read it as a real newspaper: turn the pages, or scroll it continuously. Full articles, right where you’re reading — not just a headline.',
  },
  {
    title: 'The Loom',
    body: 'What flowed from your sources, woven against what you actually read. Nothing about what you missed is ever hidden.',
  },
  {
    title: 'Your sources, your rules',
    body: 'Add any feed by URL, or switch on a starter. Nothing moves, nothing disappears into another screen.',
  },
];

export function shouldShowOnboarding() {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'yes';
  } catch (_err) {
    return false; // storage blocked (private mode) -- don't nag every load
  }
}

function markOnboarded() {
  try {
    localStorage.setItem(STORAGE_KEY, 'yes');
  } catch (_err) {
    // not worth interrupting the reader over
  }
}

export function showOnboarding() {
  let index = 0;

  const overlay = document.createElement('div');
  overlay.className = 'nooz-onboard';
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-modal', 'true');
  overlay.setAttribute('aria-label', 'Welcome to Nooz');

  const card = document.createElement('div');
  card.className = 'nooz-onboard-card';
  overlay.appendChild(card);

  const skip = document.createElement('button');
  skip.type = 'button';
  skip.className = 'nooz-onboard-skip';
  skip.textContent = 'Skip';
  skip.addEventListener('click', finish);
  card.appendChild(skip);

  const title = document.createElement('h1');
  title.className = 'nooz-onboard-title';
  card.appendChild(title);

  const body = document.createElement('p');
  body.className = 'nooz-onboard-body';
  card.appendChild(body);

  const dots = document.createElement('div');
  dots.className = 'nooz-onboard-dots';
  dots.setAttribute('aria-hidden', 'true');
  card.appendChild(dots);

  const next = document.createElement('button');
  next.type = 'button';
  next.className = 'nooz-button nooz-button--primary nooz-onboard-next';
  next.addEventListener('click', () => {
    if (index < SLIDES.length - 1) {
      index += 1;
      draw();
    } else {
      finish();
    }
  });
  card.appendChild(next);

  function draw() {
    const slide = SLIDES[index];
    title.textContent = slide.title;
    body.textContent = slide.body;
    dots.replaceChildren();
    SLIDES.forEach((_, i) => {
      const dot = document.createElement('span');
      dot.className = 'nooz-onboard-dot' + (i === index ? ' is-active' : '');
      dots.appendChild(dot);
    });
    next.textContent = index === SLIDES.length - 1 ? 'Get started' : 'Next';
  }
  draw();

  function finish() {
    markOnboarded();
    overlay.remove();
  }

  document.body.appendChild(overlay);
}
