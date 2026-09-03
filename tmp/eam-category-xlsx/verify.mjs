import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';
import fs from 'node:fs/promises';
const wb=await SpreadsheetFile.importXlsx(await FileBlob.load('outputs/eam-category-config/中世健EAM分类配置-统一初始属性.xlsx'));
const categories=wb.worksheets.getItem('分类').getRange('A2:J39').values;
const fields=wb.worksheets.getItem('字段').getRange('A2:I70').values;
const categoryIndex=new Map(categories.map((row,index)=>[row[0],index]));
const invalidOrder=categories.filter((row,index)=>row[2] && (!categoryIndex.has(row[2]) || categoryIndex.get(row[2])>=index));
if (categories.length!==38 || fields.length!==69 || invalidOrder.length>0) {
  throw new Error(JSON.stringify({categories:categories.length,fields:fields.length,invalidOrder}));
}
for (const s of ['分类','字段']) { console.log((await wb.inspect({kind:'table',sheetId:s,range:s==='分类'?'A1:J8':'A1:I10',include:'values,formulas',tableMaxRows:12,tableMaxCols:12,maxChars:12000})).ndjson); const b=await wb.render({sheetName:s,autoCrop:'all',scale:1,format:'png'}); await fs.writeFile(`outputs/eam-category-config/${s}.png`,new Uint8Array(await b.arrayBuffer())); }
console.log((await wb.inspect({kind:'match',searchTerm:'#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A',options:{useRegex:true,maxResults:100}})).ndjson);
console.log(JSON.stringify({categories:categories.length,fields:fields.length,invalidParentOrder:invalidOrder.length}));
process.exit(0);
