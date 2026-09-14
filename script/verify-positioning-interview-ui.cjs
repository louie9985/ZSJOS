// Controlled synthetic API fixture. No requests reach the development business service.
const { chromium } = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');
(async () => {
  const source = fs.readFileSync('script/sql/mysql/migrations/V203__positioning_interview_template.sql', 'utf8');
  const fields = [...source.matchAll(/JSON_OBJECT\('key','([^']+)','title','([^']+)','interviewNote','([^']*)','type','([^']+)'/g)].map((m, i) => ({ key:m[1],title:m[2],interviewNote:m[3].replaceAll('\\n','\n'),type:m[4],enabled:true,required:i>0&&i<51,systemField:i===0||i===51,sort:i,allowRemark:true }));
  assert.equal(fields.length,52);
  let current = {studentPersonId:900001,serviceRelationId:900001,studentName:'测试学员',studentNo:'TEST-INTERVIEW',status:'empty',version:0,templateId:1,templateVersionId:1,fields,items:[],attachments:[],statusOptions:{COMMUNICATED_DOCUMENT:'已沟通，见文稿',NOT_COMMUNICATED:'未沟通',CLIENT_REFUSED:'客户拒绝回答'},availableActions:['START_POSITIONING_INTERVIEW','COMPLETE_POSITIONING_INTERVIEW'],collectedAt:'2026-09-10'};
  const browser = await chromium.launch({headless:true, channel:'chrome'});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1000}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.route('**/zsjos/**',async route=>{
      const request=route.request(),url=request.url();let data=current;
      if(url.endsWith('/attachments')&&request.method()==='POST') data={fileId:800001,fileName:'访谈稿.txt',mimeType:'text/plain',fileSize:12};
      else if(url.endsWith('/draft')||url.endsWith('/complete')) {
        const body=request.postDataJSON();assert.equal(body.version,current.version);
        current={...current,items:body.items,collectedAt:body.collectedAt,version:current.version+1,status:url.endsWith('/complete')?'completed':'draft',attachments:body.attachmentIds.map(fileId=>({fileId,fileName:'访谈稿.txt',mimeType:'text/plain',fileSize:12}))};
        current.availableActions=current.status==='completed'?['VIEW_POSITIONING_INTERVIEW']:['CONTINUE_POSITIONING_INTERVIEW','COMPLETE_POSITIONING_INTERVIEW'];data=current;
      }
      await route.fulfill({json:{code:0,data}});
    });
    await page.goto('http://127.0.0.1:5188/test/positioning-interview.html');
    await page.getByText('尚无草稿，可随时保存已填写的部分。').waitFor();
    await page.getByRole('button',{name:'保存草稿',exact:true}).click();
    await page.getByText('草稿已保存', {exact:true}).waitFor();assert.equal(current.status,'draft');
    await page.locator('button').filter({hasText:/^完成定位访谈$/}).click();
    await page.getByText(/还有 50 项未确认/).waitFor();
    const radios=page.getByRole('radio',{name:'未沟通',exact:true});assert.equal(await radios.count(),50);
    for(let i=0;i<50;i++)await radios.nth(i).check();
    await page.getByRole('radio',{name:'客户拒绝回答',exact:true}).first().check();
    assert.equal(await radios.first().isChecked(),false);
    await page.locator('button').filter({hasText:/^完成定位访谈$/}).click();
    await page.getByText('请至少上传一份本次访谈稿', {exact:true}).waitFor();
    await page.locator('input[type=file]').setInputFiles({name:'访谈稿.txt',mimeType:'text/plain',buffer:Buffer.from('synthetic transcript')});
    await page.getByRole('button',{name:/访谈稿.txt/}).waitFor();
    const output=process.env.UI_ARTIFACT_DIR||'D:/ZSJ-OS/frontend/workbench/node_modules/.cache/interview-ui';fs.mkdirSync(output,{recursive:true});
    await page.locator('.ant-modal-body').first().evaluate(el=>{el.scrollTop=0});
    const cellPositions = () => page.locator('.positioning-interview-row').first().locator('th,td').evaluateAll(cells=>cells.map(cell=>{const r=cell.getBoundingClientRect();return {x:r.x,y:r.y}}));
    const desktopCells = await cellPositions();
    assert.equal(desktopCells[0].y,desktopCells[1].y);
    assert.equal(desktopCells[1].y,desktopCells[2].y);
    await page.screenshot({path:path.join(output,'desktop.png')});
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.locator('.positioning-interview-row').first().locator('th,td').count(),3);
    assert.equal(await page.locator('.positioning-interview-table-scroll').evaluate(el=>el.scrollWidth>el.clientWidth),false);
    assert.equal(await page.locator('.positioning-interview-table').evaluate(el=>[...el.querySelectorAll('th,td')].every(cell=>cell.scrollWidth<=cell.clientWidth)),true);
    const mobileCells = await cellPositions();
    assert.equal(mobileCells[0].x,mobileCells[1].x);
    assert.equal(mobileCells[1].x,mobileCells[2].x);
    assert.ok(mobileCells[0].y<mobileCells[1].y&&mobileCells[1].y<mobileCells[2].y);
    await page.screenshot({path:path.join(output,'mobile.png')});
    await page.locator('button').filter({hasText:/^完成定位访谈$/}).click();
    await page.getByText('已完成定位访谈，记录只读').waitFor();
    assert.equal(current.status,'completed');assert.equal(current.items.length,50);assert.equal(current.attachments.length,1);
    assert.equal(await page.getByRole('button',{name:'保存草稿',exact:true}).count(),0);
    assert.equal(await page.getByRole('radio').first().isDisabled(),true);
    assert.deepEqual(errors,[]);
    console.log('PASS: 52 fields, draft, validation, exclusive statuses, upload, completion, read-only, desktop three columns, mobile stacked without horizontal overflow.');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
