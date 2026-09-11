import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const vuePath = path.join(__dirname, '../src/pages/LocationMapPage.vue')
const cssPath = path.join(__dirname, 'LocationMapPage.styles.css')

const raw = fs.readFileSync(vuePath, 'utf8')
const start = raw.indexOf('<style scoped>') + '<style scoped>'.length
const end = raw.lastIndexOf('</style>')
let css = raw.slice(start, end).trim()
css = css.replace(/content: '\?';/, "content: '件';")

fs.writeFileSync(cssPath, css, 'utf8')
console.log('Extracted styles:', css.length, 'chars')
