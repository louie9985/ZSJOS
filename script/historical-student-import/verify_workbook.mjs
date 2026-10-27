// UTF-8. Read-only final visual verification; no workbook export.
import fs from 'node:fs/promises';
import path from 'node:path';
import {FileBlob, SpreadsheetFile} from '@oai/artifact-tool';
const [input, evidence] = process.argv.slice(2);
const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(input));
for (let i = 0; i < 3; i++) {
  const sheet = wb.worksheets.getItemAt(i);
  for (const [label, range] of [['header', 'A1:H1'], ['reasons', 'A1:A5']]) {
    const png = await wb.render({sheetName: sheet.name, range, scale: 1.2, format: 'png'});
    await fs.writeFile(path.join(evidence, `final-${i + 1}-${label}.png`), new Uint8Array(await png.arrayBuffer()));
  }
}
console.log('Final exception headers and review reasons rendered from the delivered XLSX');
process.exit(0);
