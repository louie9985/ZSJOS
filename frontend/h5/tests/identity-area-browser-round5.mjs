const task=await taskSpace(22); const page=task.page('p1');
const win=await task.cdp('Browser.getWindowForTarget',{targetId:page.targetId});
await task.cdp('Browser.setWindowBounds',{windowId:win.windowId,bounds:{windowState:'normal'}});
await page.screenshot({path:'D:/ZSJ-OS/frontend/h5/tmp/identity-area-mobile.png'});
await page.cdp('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
await page.screenshot({path:'D:/ZSJ-OS/frontend/h5/tmp/identity-area-desktop.png'});
console.log(await page.snapshot());
