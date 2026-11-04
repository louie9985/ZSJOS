const task=await taskSpace(22); const page=task.page('p1');
await page.click('@79'); await page.waitForFunction(()=>document.body.textContent.includes('信息已提交'));
console.log('PASS: manual fallback submit completed after existing confirmation dialog');
await task.finish({keep:[]});
