import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
import fs from 'node:fs/promises';
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load('outputs/eam-category-config/中世健EAM分类配置-统一初始属性.xlsx'));
for (const s of ['分类','字段']) { console.log((await wb.inspect({kind:'table',sheetId:s,range:s==='分类'?'A1:J8':'A1:I10',include:'values,formulas',tableMaxRows:12,tableMaxCols:12,maxChars:12000})).ndjson); const b=await wb.render({sheetName:s,autoCrop:'all',scale:1,format:'png'}); await fs.writeFile(`outputs/eam-category-config/${s}.png`,new Uint8Array(await b.arrayBuffer())); }
console.log((await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A',options:{useRegex:true,maxResults:100}})).ndjson);
