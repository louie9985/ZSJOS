# 员工付款二维码

成交录入生成线上支付链接后，点击「付款二维码」预览，再「复制二维码」粘贴到微信，或「下载二维码」发送 PNG。二维码使用接口返回的原始 paymentUrl，不创建新的支付单。

图片使用既有公开支付页的中世健官网 Logo（来源 https://www.zsjedc.com/logo.png，随工作台本地打包），包含订单编号 purchaseIntentNo、后端已保存的 totalAmount/currency 和 paymentExpiresAt。金额不读取正在编辑的表单。当前员工接口没有支付商品名称快照，图片不从实时商品目录推断名称；客户扫码进入原支付页核对商品与收款信息。

二维码采用 H 纠错、四模块白边、固定黑白色；中央图标取官网 Logo 的标志部分，遮挡区域不超过二维码宽度的 14%。PNG 不随工作台暗色主题变色。生成失败显示重试；复制图片要求浏览器支持 ClipboardItem、安全上下文及剪贴板权限，失败时提示下载。

只有线上 created/waiting、具有未来有效期且未取消待确认的链接可以生成或导出图片。弹窗每秒检查到期，复制/下载时再次检查。已发送图片无法撤回，实际到账、失效及是否可付款仍由原支付接口判定，员工发送前应刷新支付状态。

本功能沿用现有成交录入及支付链接授权入口，不新增业务导出 API、权限标识或授权来源；不变更管理端、H5 支付协议及数据库。

开发验收页面：`frontend/workbench/test/payment-qr.html`（仅开发环境模拟数据，不请求业务接口）。运行 `npm test -- src/components/paymentQrCard.test.ts src/components/SalesOrderEntryModal.test.ts` 和 `npm run build`，并验证桌面/手机宽度的预览、复制、下载、到期与不可分享状态。微信真机长按识别和实际支付渠道跳转需另行验收，浏览器生成成功不等同于微信支付成功。
