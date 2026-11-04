const task = await taskSpace('identity-area-20261104'); const page = task.page('p1');
await page.goto('http://127.0.0.1:5197/student-info-form#token='+'a'.repeat(43));
console.log({spaceId:task.spaceId}); console.log(await page.snapshot());
