import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const filePath = path.join(__dirname, '../src/pages/LocationMapPage.vue')

const scriptBody = fs.readFileSync(path.join(__dirname, 'LocationMapPage.script.setup.js'), 'utf8')
const script = `<script setup>\n${scriptBody}\n</script>`
const template = fs.readFileSync(path.join(__dirname, 'LocationMapPage.template.html'), 'utf8')
const styles = fs.readFileSync(path.join(__dirname, 'LocationMapPage.styles.css'), 'utf8')
const stylePart = `<style scoped>\n${styles}\n</style>`

fs.writeFileSync(filePath, `${script}\n\n${template}\n\n${stylePart}`, 'utf8')
console.log('Rebuilt LocationMapPage.vue with UTF-8 encoding')
