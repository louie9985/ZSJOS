const task=await taskSpace(22); const page=task.page('p1');
console.log(await page.evaluate(()=>({viewport:innerWidth,scrollWidth:document.documentElement.scrollWidth,manual:document.querySelector('textarea[aria-label="本人填写的历史地区"]')?.value})));
await page.evaluate(()=>document.querySelector('button.van-button--primary').scrollIntoView({block:'center'}));
await page.click('button.van-button--primary');
await page.waitForFunction(()=>document.body.textContent.includes('信息已提交'));
console.log('manual submission completed in fixture browser');
await task.finish({keep:[]});
