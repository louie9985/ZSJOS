import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';

const outputDir = 'D:/ZSJ-OS/outputs/asset-ledger-adaptation';
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(`${outputDir}/中世健资产台账导入转换版.xlsx`));
const summary = await workbook.inspect({ kind: 'sheet,table', maxChars: 6000, tableMaxRows: 3, tableMaxCols: 12 });
const errors = await workbook.inspect({ kind: 'match', searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A', options: { useRegex: true, maxResults: 100 }, summary: 'formula error scan' });
const ledger = await workbook.inspect({ kind: 'table', sheetId: '资产台账', range: 'A1:BF2', include: 'values,formulas', tableMaxRows: 2, tableMaxCols: 58, maxChars: 12000 });
const note = await workbook.render({ sheetName: '转换说明', range: 'A1:B15', scale: 1, format: 'png' });
await fs.writeFile(`${outputDir}/conversion-notes-preview.png`, new Uint8Array(await note.arrayBuffer()));
console.log(summary.ndjson);
console.log(ledger.ndjson);
console.log(errors.ndjson);
