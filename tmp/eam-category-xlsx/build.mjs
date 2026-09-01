import { execFileSync } from 'node:child_process';
import fs from 'node:fs/promises';
import { SpreadsheetFile, Workbook } from '@oai/artifact-tool';

const mysql = (sql) => execFileSync('docker', ['exec','yudao-mysql','sh','-lc',`mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D ruoyi-vue-pro -N -B -e ${JSON.stringify(sql)}`], {encoding:'utf8'}).trim();
const catRows = mysql('SELECT c.code,c.name,COALESCE(p.code,\"\"),c.status,c.sort,c.management_mode,COALESCE(c.unit,\"个\"),COALESCE(c.remark,\"\") FROM eam_category c LEFT JOIN eam_category p ON p.id=c.parent_id WHERE c.deleted=0 ORDER BY c.sort,c.id').split(/\r?\n/).filter(Boolean).map(x=>x.split('\t'));
const fieldRows = mysql('SELECT c.code,f.field_key,f.field_name,f.field_type,COALESCE(f.option_source,\"\"),COALESCE(f.dict_type,\"\"),IF(f.admin_visible,\"是\",\"否\"),f.sort FROM eam_category_field f JOIN eam_category c ON c.id=f.category_id WHERE f.deleted=0 ORDER BY c.sort,c.id,f.sort,f.id').split(/\r?\n/).filter(Boolean).map(x=>x.split('\t'));
const typeMap = {1:'单行文本',2:'多行文本',3:'数字',4:'日期',5:'下拉选择',6:'图片/文件'};
const wb = Workbook.create();
const cat = wb.worksheets.add('分类');
const fld = wb.worksheets.add('字段');
const catHeaders = [['分类编码','分类名称','父分类编码','状态','排序','管理模式','计量单位','备注','交付模式','持有模式']];
const catData = catRows.map(r=>[r[0],r[1],r[2],r[3]==='1'?'关闭':'开启',r[4],r[5]==='2'?'批量':'单件',r[6],r[7],'实物入库','消耗型']);
const fldHeaders = [['分类编码','字段标识','字段名称','字段类型','选项来源','字典类型','管理端显示','排序','备注']];
const fldData = fieldRows.map(r=>[r[0],r[1],r[2],typeMap[r[3]]??r[3],r[4],r[5],r[6],r[7],'']);
cat.getRangeByIndexes(0,0,1,catHeaders[0].length).values=catHeaders;
cat.getRangeByIndexes(1,0,catData.length,catHeaders[0].length).values=catData;
fld.getRangeByIndexes(0,0,1,fldHeaders[0].length).values=fldHeaders;
fld.getRangeByIndexes(1,0,fldData.length,fldHeaders[0].length).values=fldData;
for (const [sheet, rows, cols] of [[cat,catData,10],[fld,fldData,9]]) {
  sheet.showGridLines=false; sheet.freezePanes.freezeRows(1);
  const header=sheet.getRangeByIndexes(0,0,1,cols); header.format={fill:'#1F4E78',font:{bold:true,color:'#FFFFFF'},horizontalAlignment:'center',verticalAlignment:'center',wrapText:true}; header.format.rowHeight=28;
  const body=sheet.getRangeByIndexes(1,0,rows.length,cols); body.format={verticalAlignment:'center',wrapText:false}; body.format.borders={preset:'inside',style:'thin',color:'#D9E2F3'};
  sheet.getUsedRange().format.autofitColumns();
  sheet.getRangeByIndexes(0,0,rows.length+1,cols).format.borders={preset:'outside',style:'thin',color:'#9FBAD0'};
}
cat.getRange('A1:J1').format.columnWidth=18; cat.getRange('B:B').format.columnWidth=22; cat.getRange('H:H').format.columnWidth=24; fld.getRange('A:A').format.columnWidth=22; fld.getRange('B:B').format.columnWidth=24; fld.getRange('C:C').format.columnWidth=18; fld.getRange('D:F').format.columnWidth=16; fld.getRange('I:I').format.columnWidth=24;
const outDir='outputs/eam-category-config'; await fs.mkdir(outDir,{recursive:true});
const out=await SpreadsheetFile.exportXlsx(wb); await out.save(`${outDir}/中世健EAM分类配置-统一初始属性.xlsx`);
console.log(JSON.stringify({categories:catData.length,fields:fldData.length,path:`${outDir}/中世健EAM分类配置-统一初始属性.xlsx`}));
