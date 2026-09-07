// @vitest-environment jsdom
import {describe, expect, it} from 'vitest'
import {renderMarkdown, stripSourceMarkers} from './markdown'

describe('Markdown rendering', () => {
    it('renders common Markdown syntax', () => {
        const html = renderMarkdown([
            '# 标题',
            '',
            '这是 **重点** 和 `inlineCode`。',
            '',
            '- 第一项',
            '- 第二项',
            '',
            '| 名称 | 值 |',
            '| --- | --- |',
            '| A | B |',
            '',
            '```ts',
            'const value = 1',
            '```',
        ].join('\n'))

        expect(html).toContain('<h1>标题</h1>')
        expect(html).toContain('<strong>重点</strong>')
        expect(html).toContain('<code>inlineCode</code>')
        expect(html).toContain('<ul>')
        expect(html).toContain('<table>')
        expect(html).toContain('<pre><code class="language-ts">')
    })

    it('keeps allowed links and rejects unsafe links', () => {
        const html = renderMarkdown('[安全链接](https://example.com) [邮件](mailto:test@example.com) [站内](/docs) [相对路径](docs/guide) [危险](javascript:alert(1)) [协议相对](//evil.example)')

        expect(html).toContain('href="https://example.com"')
        expect(html).toContain('href="mailto:test@example.com"')
        expect(html).toContain('href="/docs"')
        expect(html).toContain('href="docs/guide"')
        expect(html).not.toContain('href="javascript:')
        expect(html).not.toContain('href="//evil.example"')
        expect(html).toContain('危险')
    })

    it('removes raw HTML and common XSS vectors', () => {
        const html = renderMarkdown('<img src=x onerror=alert(1)><script>alert(1)</script><a href="javascript:alert(1)" onclick="alert(1)">链接</a> ![x](data:text/html,<script>alert(1)</script>)')
        const document = new DOMParser().parseFromString(html, 'text/html')

        expect(document.querySelector('script, a')).toBeNull()
        expect(document.querySelector('img[src^="data:"]')).toBeNull()
        expect([...document.body.querySelectorAll('*')].some(element => [...element.attributes].some(attribute => /^on/i.test(attribute.name)))).toBe(false)
        expect(html).toContain('&lt;img')
        expect(html).toContain('&lt;script&gt;')
        expect(html).toContain('&lt;a href="javascript:alert(1)"')
    })

    it('removes internal source markers before rendering', () => {
        const markdown = '答案内容【来源:12-34】\n[source:56-78]'

        expect(stripSourceMarkers(markdown)).toBe('答案内容')
        expect(renderMarkdown(markdown)).not.toMatch(/来源|source:56-78/i)
    })
})
