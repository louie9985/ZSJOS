import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
const input = await FileBlob.load('backend/yudao-module-eam/src/main/resources/eam/eam-category-config-template.xlsx');
const wb = await SpreadsheetFile.importXlsx(input);
console.log((await wb.inspect({kind:'workbook,sheet,table,region',maxChars:12000,tableMaxRows:20,tableMaxCols:15})).ndjson);
for (const s of ['分类','字段']) { const blob=await wb.render({sheetName:s,autoCrop:'all',scale:1,format:'png'}); const fs=await import('node:fs/promises'); await fs.writeFile(`tmp/eam-category-xlsx/${s}.png`,new Uint8Array(await blob.arrayBuffer())); }
