/**
 * 浏览器打印：打开新窗口写入 HTML 后调用 print。
 */
import { formatLocalizedDateTime, formatLocalizedNumber } from '@/utils/locale'

export function printHtml(title: string, bodyHtml: string) {
  const win = window.open('', '_blank', 'noopener,noreferrer,width=900,height=700')
  if (!win) {
    throw new Error('无法打开打印窗口，请允许浏览器弹窗')
  }
  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>${escapeHtml(title)}</title>
  <style>
    * { box-sizing: border-box; }
    body { font-family: "Microsoft YaHei", SimSun, sans-serif; color: #111; margin: 24px; font-size: 13px; }
    h1 { font-size: 20px; margin: 0 0 8px; text-align: center; }
    .meta { text-align: center; color: #555; margin-bottom: 16px; }
    table { width: 100%; border-collapse: collapse; margin-top: 12px; }
    th, td { border: 1px solid #333; padding: 6px 8px; }
    th { background: #f3f4f6; }
    .info { width: 100%; margin-bottom: 8px; }
    .info td { border: none; padding: 3px 6px; }
    .right { text-align: right; }
    .footer { margin-top: 28px; display: flex; justify-content: space-between; }
    .sign { width: 30%; border-top: 1px solid #333; padding-top: 6px; text-align: center; }
    @media print {
      body { margin: 12mm; }
      .no-print { display: none; }
    }
  </style>
</head>
<body>
  ${bodyHtml}
  <script>
    window.onload = function () {
      setTimeout(function () { window.print(); }, 200);
    };
  </script>
</body>
</html>`
  win.document.open()
  win.document.write(html)
  win.document.close()
}

export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function money(value: unknown): string {
  return formatLocalizedNumber(Number(value ?? 0), {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

export function qty(value: unknown): string {
  return formatLocalizedNumber(Number(value ?? 0), { maximumFractionDigits: 4 })
}

export function buildDocPrintHtml(opts: {
  title: string
  docNo: string
  fields: Array<[string, string]>
  columns: string[]
  rows: string[][]
  totals?: Array<[string, string]>
}): string {
  const fieldRows = opts.fields
    .map(([k, v]) => `<tr><td style="width:18%"><b>${escapeHtml(k)}</b></td><td>${escapeHtml(v)}</td></tr>`)
    .join('')
  const head = opts.columns.map((c) => `<th>${escapeHtml(c)}</th>`).join('')
  const body = opts.rows
    .map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`)
    .join('')
  const totals = (opts.totals || [])
    .map(([k, v]) => `<tr><td colspan="${Math.max(opts.columns.length - 1, 1)}" class="right"><b>${escapeHtml(k)}</b></td><td class="right">${escapeHtml(v)}</td></tr>`)
    .join('')
  return `
    <h1>${escapeHtml(opts.title)}</h1>
    <div class="meta">单号：${escapeHtml(opts.docNo)} · 打印时间：${escapeHtml(formatLocalizedDateTime(new Date()))}</div>
    <table class="info">${fieldRows}</table>
    <table>
      <thead><tr>${head}</tr></thead>
      <tbody>${body}${totals}</tbody>
    </table>
    <div class="footer">
      <div class="sign">制单</div>
      <div class="sign">审核</div>
      <div class="sign">签收</div>
    </div>
  `
}
