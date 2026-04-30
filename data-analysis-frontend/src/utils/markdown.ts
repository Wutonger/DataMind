import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true
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
