import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';

const inputPath = 'outputs/eam-category-config/中世健EAM分类配置-统一初始属性.xlsx';
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inputPath));
const categorySheet = workbook.worksheets.getItem('分类');
const fieldSheet = workbook.worksheets.getItem('字段');
const categoryRows = categorySheet.getRange('A2:J39').values;

const orderedRows = [];
const pendingCategories = [...categoryRows];
const emittedCodes = new Set();
while (pendingCategories.length > 0) {
  const ready = pendingCategories.filter(row => !row[2] || emittedCodes.has(row[2]));
  if (ready.length === 0) throw new Error('Category hierarchy contains a missing parent or cycle');
  for (const row of ready) {
    orderedRows.push(row);
    emittedCodes.add(row[0]);
    pendingCategories.splice(pendingCategories.indexOf(row), 1);
  }
}

categorySheet.getRange('A2:J39').values = orderedRows;
const outDir='outputs/eam-category-config'; await fs.mkdir(outDir,{recursive:true});
const outputPath = `${outDir}/中世健EAM分类配置-统一初始属性.xlsx`;
const out=await SpreadsheetFile.exportXlsx(workbook); await out.save(outputPath);
const fieldCount = fieldSheet.getRange('A2:I70').values.filter(row => row.some(value => value !== null && value !== '')).length;
console.log(JSON.stringify({categories:orderedRows.length,fields:fieldCount,path:outputPath}));
