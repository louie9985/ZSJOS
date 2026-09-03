import fs from 'node:fs/promises';
import { FileBlob, SpreadsheetFile } from '@oai/artifact-tool';

const inputPath = 'D:/liulanqi/中世健资产台账导入模板.xlsx';
const outputDir = 'D:/ZSJ-OS/outputs/asset-ledger-adaptation';
const outputPath = `${outputDir}/中世健资产台账导入转换版.xlsx`;
await fs.mkdir(outputDir, { recursive: true });

const source = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(source);
const ledger = workbook.worksheets.getItem('资产台账');
const fieldSheet = workbook.worksheets.getItem('分类字段说明');

const standard = [
  '分类编码', '资产名称', '资产编号', '数量', '资产状态', '品牌型号', '规格参数', '序列号', '条码',
  '原值', '净值', '购入日期', '资产来源', '保修到期日', '使用人', '存放地点', '预计使用年限（月）', '备注',
];
const fieldRows = fieldSheet.getUsedRange(true).values.slice(1);
const custom = [];
const seen = new Set();
for (const row of fieldRows) {
  const key = String(row?.[2] ?? '').trim();
  const name = String(row?.[3] ?? '').trim();
  if (key && name && !seen.has(key)) {
    seen.add(key);
    custom.push(`${key}:${name}`);
  }
}
const headers = [...standard, ...custom];
ledger.getRange('A1:BE2').clear({ applyTo: 'contents' });
ledger.getRangeByIndexes(0, 0, 1, headers.length).values = [headers];
ledger.getRangeByIndexes(1, 0, 1, headers.length).values = [Array(headers.length).fill(null)];
ledger.getRangeByIndexes(0, 0, 1, headers.length).format = {
  fill: '#1F4E78',
  font: { bold: true, color: '#FFFFFF' },
  wrapText: true,
  horizontalAlignment: 'center',
  verticalAlignment: 'center',
  borders: { preset: 'all', style: 'thin', color: '#B7C9D6' },
};
ledger.getRangeByIndexes(1, 0, 1, headers.length).format = {
  borders: { preset: 'all', style: 'thin', color: '#D9E2F3' },
  verticalAlignment: 'center',
};
ledger.getRange('A1').format.columnWidth = 14;
ledger.getRange('B1').format.columnWidth = 20;
ledger.getRangeByIndexes(0, 2, 1, 16).format.columnWidth = 15;
ledger.getRangeByIndexes(0, 18, 1, Math.max(1, custom.length)).format.columnWidth = 18;
ledger.getRangeByIndexes(0, 0, 1, headers.length).format.rowHeight = 42;
ledger.freezePanes.freezeRows(1);
ledger.showGridLines = false;

const note = workbook.worksheets.add('转换说明');
note.showGridLines = false;
note.getRange('A1:D1').merge();
note.getRange('A1').values = [['EAM V3 资产台账导入转换说明']];
note.getRange('A1:D1').format = { fill: '#1F4E78', font: { bold: true, color: '#FFFFFF', size: 14 }, horizontalAlignment: 'center', verticalAlignment: 'center' };
note.getRange('A3:B3').values = [['处理项', '规则']];
note.getRange('A3:B3').format = { fill: '#D9EAF7', font: { bold: true, color: '#1F1F1F' }, borders: { preset: 'all', style: 'thin', color: '#B7C9D6' } };
note.getRange('A4:B15').values = [
  ['原始文件', 'D:/liulanqi/中世健资产台账导入模板.xlsx；原文件不覆盖'],
  ['导入工作表', '仅读取“资产台账”；第 1 行为单层表头，第 2 行开始填写数据'],
  ['分类编码', '使用系统已配置的分类编码；原资产类别勾选列需转换为一个分类编码'],
  ['资产名称', '每一项实际资产一行；原“其他……请说明”写入资产名称或备注'],
  ['多资产拆分', '原始一行包含多个资产时，拆成多行，每行一个分类编码和资产名称'],
  ['自定义字段', '仅使用系统已配置字段，表头格式为“字段标识:字段名称”'],
  ['密码', '微信密码不导入；系统导入器会跳过密码列'],
  ['图片和附件', '微信账号截图、附件需在资产建档后进入详情上传'],
  ['行政信息', '行政核对结果、行政核对人、签核区不属于当前台账导入列'],
  ['交接纪录', '通过资产交接/流转流程生成，不写入资产导入表'],
  ['未接入字段', '上级、入司日期、使用人承诺、承诺日期暂保留在原始台账；当前解析器不读取'],
  ['字典字段', '资产状态、资产来源及下拉自定义字段使用系统字典值'],
];
note.getRange('A4:B15').format = { wrapText: true, verticalAlignment: 'top', borders: { preset: 'all', style: 'thin', color: '#D9E2F3' } };
note.getRange('A1:A15').format.columnWidth = 18;
note.getRange('B1:B15').format.columnWidth = 72;
note.getRange('A1:B15').format.rowHeight = 25;
note.getRange('A1:D1').format.rowHeight = 32;

const preview = await workbook.render({ sheetName: '资产台账', range: `A1:${String.fromCharCode(64 + Math.min(headers.length, 26))}2`, scale: 1, format: 'png' });
await fs.writeFile(`${outputDir}/asset-ledger-preview.png`, new Uint8Array(await preview.arrayBuffer()));
const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(outputPath);
console.log(JSON.stringify({ outputPath, headers: headers.length, customFields: custom.length }, null, 2));
