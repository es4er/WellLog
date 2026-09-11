export function downloadCsv(filename, headers, rows) {
  const escapeCell = (value) => {
    const text = String(value ?? '')
    if (/[",\r\n]/.test(text)) {
      return '"' + text.replace(/"/g, '""') + '"'
    }
    return text
  }

  const csv = [headers, ...rows]
    .map((row) => row.map(escapeCell).join(','))
    .join('\r\n')
  const blob = new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
