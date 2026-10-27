// UTF-8. Maintain one simple exception workbook at a fixed path; no versioned exports.
import fs from 'node:fs/promises';
import path from 'node:path';
import {Workbook, SpreadsheetFile} from '@oai/artifact-tool';

const [preparedPath, evidenceDir] = process.argv.slice(2);
if (!preparedPath || !evidenceDir) throw new Error('prepared master JSON and private evidence directory required');
const data = JSON.parse(await fs.readFile(preparedPath, 'utf8'));
const outputPath = path.resolve(data.masterPath);
if (path.basename(outputPath) !== '待确认学员.xlsx') throw new Error('Use the fixed master filename 待确认学员.xlsx');
const workbook = Workbook.create();
const sheet = workbook.worksheets.add('异常主表');
const count = data.rows.length + 1;
sheet.showGridLines = false;
sheet.getRange(`A1:I${count}`).values = [data.headers, ...data.rows];
const grid = sheet.getRange(`A1:I${count}`);
grid.format.font.size = 11;
grid.format.font.color = '#202124';
grid.format.wrapText = true;
grid.format.verticalAlignment = 'top';
grid.format.borders = {preset: 'all', style: 'thin', color: '#D9DEE5'};
const header = sheet.getRange('A1:I1');
header.format.fill = '#E8EEF5';
header.format.font.bold = true;
header.format.rowHeight = 28;
header.format.verticalAlignment = 'center';
const widths = [36, 13, 18, 55, 34, 24, 70, 34, 30];
for (let c=0;c<9;c++) sheet.getRangeByIndexes(0,c,count,1).format.columnWidth=widths[c];
sheet.getRange(`C2:C${count}`).setNumberFormat('@');
function textWidth(value) {return [...String(value ?? '')].reduce((total,ch)=>total+(ch.charCodeAt(0)>255?2:1),0);}
for (let r=0;r<data.rows.length;r++) {
  const lines = Math.max(...data.rows[r].map((value,c)=>String(value ?? '').split('\n').reduce((sum,line)=>sum+Math.max(1,Math.ceil(textWidth(line)/(widths[c]-2))),0)));
  sheet.getRangeByIndexes(r+1,0,1,9).format.rowHeight=Math.max(48,lines*16+10);
}
sheet.freezePanes.freezeRows(1);
sheet.freezePanes.freezeColumns(3);
workbook.recalculate();
await fs.mkdir(evidenceDir,{recursive:true});
for (const [label,range] of [['header','A1:I1'],['reason-layout','A1:A6']]) {
  const png=await workbook.render({sheetName:'异常主表',range,scale:1.2,format:'png'});
  await fs.writeFile(path.join(evidenceDir,`${label}.png`),new Uint8Array(await png.arrayBuffer()));
}
await fs.mkdir(path.dirname(outputPath),{recursive:true});
const temporaryPath=outputPath+'.updating.xlsx';
await (await SpreadsheetFile.exportXlsx(workbook)).save(temporaryPath);
await fs.rename(temporaryPath,outputPath);
try {await fs.unlink(temporaryPath+'.inspect.ndjson');} catch(e) {if(e.code!=='ENOENT')throw e;}
console.log(JSON.stringify({masterRows:data.rows.length,sourceRecords:data.sourceRecordCount,worksheets:1,updatedInPlace:true,output:outputPath}));
process.exit(0);
