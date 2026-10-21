"""Section a sanitised guide without hiding content from non-JavaScript readers."""
import re
from html.parser import HTMLParser

from markupsafe import Markup

from .markup import render


class _Headings(HTMLParser):
    def __init__(self, source):
        HTMLParser.__init__(self, convert_charrefs=True)
        self.offsets = [0]
        for line in source.splitlines(True):
            self.offsets.append(self.offsets[-1] + len(line))
        self.stack = []
        self.heading = None
        self.headings = []

    def position(self):
        line, column = self.getpos()
        return self.offsets[line - 1] + column

    def handle_starttag(self, tag, attrs):
        if tag == 'h2' and not self.stack:
            self.heading = {'start': self.position(), 'text': []}
        if tag not in ('br', 'hr', 'img'):
            self.stack.append(tag)

    def handle_endtag(self, tag):
        if self.stack and tag in self.stack:
            self.stack = self.stack[:len(self.stack) - 1 - self.stack[::-1].index(tag)]
        if tag == 'h2' and self.heading is not None and not self.stack:
            self.heading['end'] = self.position() + len('</h2>')
            self.heading['title'] = ''.join(self.heading.pop('text')).strip()
            self.headings.append(self.heading)
            self.heading = None

    def handle_data(self, data):
        if self.heading is not None:
            self.heading['text'].append(data)


def sections(body):
    source = str(render(body))
    parser = _Headings(source)
    parser.feed(source)
    starts = parser.headings
    chunks = []
    if not starts or source[:starts[0]['start']].strip():
        chunks.append(('At a glance', source[:starts[0]['start']] if starts else source))
    for index, heading in enumerate(starts):
        end = starts[index + 1]['start'] if index + 1 < len(starts) else len(source)
        chunks.append((heading['title'], source[heading['end']:end]))
    result = []
    for index, (title, html) in enumerate(chunks):
        slug = re.sub(r'[^a-z0-9]+', '-', title.lower()).strip('-') or 'section'
        html = html.replace('<table>', '<div class="guide-table" role="region" aria-label="Reference table" tabindex="0"><table>')
        html = html.replace('</table>', '</table></div>').replace('<th>', '<th scope="col">')
        result.append({'id': 'guide-%s-%d' % (slug, index + 1), 'title': title, 'html': Markup(html)})
    return result
