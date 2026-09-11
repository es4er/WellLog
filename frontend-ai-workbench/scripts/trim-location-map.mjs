import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const filePath = path.join(path.dirname(fileURLToPath(import.meta.url)), '../src/pages/LocationMapPage.vue')
const raw = fs.readFileSync(filePath, 'utf8')
const firstTemplateEnd = raw.indexOf('</template>') + '</template>'.length
const styleStart = raw.lastIndexOf('<style scoped>')
const style = raw.slice(styleStart).replace(
  ".cell-item-qty::after { content: '?;",
  ".cell-item-qty::after { content: '件';"
)
const fixed = raw.slice(0, firstTemplateEnd) + '\n\n' + style
fs.writeFileSync(filePath, fixed, 'utf8')
console.log('trimmed duplicate template, bytes:', fixed.length)
