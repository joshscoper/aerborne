(() => {
  const reader = document.querySelector('[data-policy-reader]');
  if (!reader) return;
  const sections = [...reader.querySelectorAll('[data-policy-section]')];
  const links = [...reader.querySelectorAll('[data-policy-section-link]')];
  const tools = reader.querySelector('[data-policy-tools]');
  const position = reader.querySelector('[data-policy-position]');
  const progress = reader.querySelector('[data-policy-progress]');
  const print = reader.querySelector('[data-policy-print]');
  print.hidden = false;
  print.addEventListener('click', () => window.print());
  const contents = reader.querySelector('.policy-contents');
  if (window.matchMedia('(max-width: 900px)').matches && !location.hash.startsWith('#policy-')) contents.open = false;
  if (!sections.length) return;
  tools.hidden = false;
  let scheduled = false;
  const update = () => {
    scheduled = false;
    let index = 0;
    const offset = (parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 0)
      + (parseFloat(getComputedStyle(sections[0]).scrollMarginTop) || 0) + 8;
    sections.forEach((section, i) => { if (section.getBoundingClientRect().top <= Math.max(160, offset)) index = i; });
    links.forEach((link, i) => { if (i === index) link.setAttribute('aria-current', 'location'); else link.removeAttribute('aria-current'); });
    position.textContent = `Section ${index + 1} of ${sections.length} · ${sections[index].querySelector('h2').textContent}`;
    progress.value = Math.round((index + 1) / sections.length * 100);
  };
  window.addEventListener('scroll', () => { if (!scheduled) { scheduled = true; requestAnimationFrame(update); } }, {passive: true});
  update();
})();
