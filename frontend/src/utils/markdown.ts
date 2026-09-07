import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const SOURCE_MARKER = /(?:【(?:来源|source)[:：]\s*\d+\s*-\s*\d+】|\[(?:来源|source)[:：]?\s*\d+\s*-\s*\d+\])/gi
const SAFE_URI_REGEXP = /^(?:(?:https?|mailto):[^\s]+|\/(?!\/)[^\s]*|#[^\s]*|\.{1,2}\/[^\s]*|(?!(?:[a-z][a-z0-9+.-]*):)[^:/?#\s][^?#\s]*)$/i

// Keep links useful while rejecting javascript:, data:, vbscript: and other schemes.
function isSafeLink(url: string): boolean {
    const value = url.trim()
    return !value || !value.startsWith('//') && SAFE_URI_REGEXP.test(value)
}

const markdown = new MarkdownIt({
    html: false,
    breaks: false,
    linkify: false,
    typographer: false,
})
markdown.validateLink = isSafeLink

const defaultLinkOpen = markdown.renderer.rules.link_open
    ?? ((tokens, index, options, _env, self) => self.renderToken(tokens, index, options))

// markdown-it skips invalid links during parsing. This second guard also handles
// links created by custom token sources or future renderer extensions.
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
    const token = tokens[index]
    const href = token.attrGet('href') || ''
    if (isSafeLink(href)) return defaultLinkOpen(tokens, index, options, env, self)

    token.meta = {...(token.meta || {}), unsafeLink: true}
    token.tag = 'span'
    token.attrs = [['class', 'markdown-unsafe-link']]
    return self.renderToken(tokens, index, options)
}

const defaultLinkClose = markdown.renderer.rules.link_close
    ?? ((_tokens, _index) => '</a>')
markdown.renderer.rules.link_close = (tokens, index, options, env, self) => {
    for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
        if (tokens[cursor].type !== 'link_open') continue
        return tokens[cursor].meta?.unsafeLink ? '</span>' : defaultLinkClose(tokens, index, options, env, self)
    }
    return defaultLinkClose(tokens, index, options, env, self)
}

export function stripSourceMarkers(content: string): string {
    return content.replace(SOURCE_MARKER, '').replace(/[ \t]+\n/g, '\n').trim()
}

export function renderMarkdown(content: string): string {
    const source = stripSourceMarkers(content || '')
    const html = markdown.render(source)
    return DOMPurify.sanitize(html, {
        USE_PROFILES: {html: true},
        ALLOWED_URI_REGEXP: SAFE_URI_REGEXP,
        FORBID_TAGS: ['style', 'svg', 'math', 'iframe', 'object', 'embed', 'form', 'base', 'meta', 'link', 'template'],
        FORBID_ATTR: ['style', 'srcdoc'],
    })
}
