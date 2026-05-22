import { marked } from 'marked'
import type { RendererObject } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true
})

const escapeHtml = (value: string): string =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const markdownRenderer: RendererObject = {
  code({ text, lang }) {
    const language = lang?.trim().match(/^[\w-]+/)?.[0] || ''
    const languageClass = language ? ` class="language-${language}"` : ''
    const languageAttr = language ? ` data-language="${escapeHtml(language)}"` : ''

    return `
      <div class="markdown-code-block">
        <span
          class="markdown-code-copy"
          role="button"
          tabindex="0"
          aria-label="复制代码"
          title="复制代码"
          data-feedback="复制"
        >
          <span class="markdown-code-copy-icon" aria-hidden="true">
            <svg viewBox="0 0 16 16" fill="none">
              <path
                d="M5.25 3.75A1.5 1.5 0 0 1 6.75 2.25h5a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5h-5a1.5 1.5 0 0 1-1.5-1.5z"
                stroke="currentColor"
                stroke-width="1.25"
                stroke-linejoin="round"
              />
              <path
                d="M3.75 5.75v5.5a1 1 0 0 0 1 1h5.5"
                stroke="currentColor"
                stroke-width="1.25"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <span class="markdown-code-check-icon" aria-hidden="true">
            <svg viewBox="0 0 16 16" fill="none">
              <path
                d="m3.5 8.5 3 3 6-7"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </span>
        </span>
        <pre${languageAttr}><code${languageClass}>${escapeHtml(text)}</code></pre>
      </div>
    `
  }
}

marked.use({
  renderer: markdownRenderer
})

const normalizeHtmlTableBlock = (html: string): string =>
  html
    .replace(/\r?\n/g, '')
    .replace(/>\s+</g, '><')
    .replace(/<(\/?)p>/gi, '')

const normalizeRawHtmlTables = (content: string): string =>
  content.replace(/<table[\s\S]*?<\/table>/gi, (match) => `\n\n${normalizeHtmlTableBlock(match)}\n\n`)

const parseTableRow = (line: string): string[] =>
  line.replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())

const parseCellContent = (content: string): string => {
  try {
    const html = marked.parse(content, { async: false })
    return String(html).replace(/^<p>(.*?)<\/p>$/s, '$1')
  } catch {
    return content
  }
}

const parseMarkdownTable = (tableText: string): string => {
  const lines = tableText.trim().split('\n').filter((line) => line.trim())
  if (lines.length < 2) {
    return tableText
  }

  const headers = parseTableRow(lines[0])
  const dataRows = lines.slice(2).map((row) => parseTableRow(row))

  let html = '<table><thead><tr>'
  headers.forEach((header) => {
    html += `<th>${parseCellContent(header)}</th>`
  })
  html += '</tr></thead><tbody>'

  dataRows.forEach((row) => {
    html += '<tr>'
    row.forEach((cell) => {
      html += `<td>${parseCellContent(cell)}</td>`
    })
    html += '</tr>'
  })

  html += '</tbody></table>'
  return html
}

export const renderMarkdownContent = (content: string): string => {
  if (!content) {
    return ''
  }

  try {
    const cleanText = normalizeRawHtmlTables(content)
      .replace(/\\n/g, '\n')
      .split('\n')
      .map((line) => (/^\s*\|/.test(line) ? line.trimStart() : line))
      .join('\n')

    const lines = cleanText.split('\n')
    let inTable = false
    let currentTableLines: string[] = []
    const resultLines: string[] = []

    for (const line of lines) {
      if (line.trim().startsWith('|')) {
        if (!inTable) {
          inTable = true
          currentTableLines = []
        }
        currentTableLines.push(line)
        continue
      }

      if (inTable) {
        inTable = false
        resultLines.push(
          currentTableLines.length >= 2
            ? parseMarkdownTable(currentTableLines.join('\n'))
            : currentTableLines.join('\n')
        )
        currentTableLines = []
      }

      resultLines.push(line)
    }

    if (inTable) {
      resultLines.push(
        currentTableLines.length >= 2
          ? parseMarkdownTable(currentTableLines.join('\n'))
          : currentTableLines.join('\n')
      )
    }

    return String(marked.parse(resultLines.join('\n'), { async: false }))
  } catch (error) {
    console.error('Failed to render markdown content', error)
    return content
  }
}
