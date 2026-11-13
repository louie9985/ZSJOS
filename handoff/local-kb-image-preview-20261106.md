# 知识库公开分享图片预览

- 环境 local（环境变量及环境文件未指定，按规则回退）。
- 工作流 local-kb-image-preview-20261106，负责人 Codex。
- 分支 main，工作目录 D:\ZSJ-OS，基点 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。
- 目标为公开 HTML 正文图片提供点击预览和原始尺寸查看。
- 范围为 PmsKnowledgeSharePage.java、PmsKnowledgeSharePageControllerTest.java、docs/api/pms-knowledge-public-share.md 及本记录。
- 现有未提交改动属于其他任务，保留。
- 依赖为浏览器原生 Popover，无新增依赖。保留禁止脚本 CSP。
- 验证为定向 Controller 测试及合成页面桌面、手机浏览器检查。
- 非目标为数据修改、权限变更、附件预览改造、部署。
- 集成目标及顺序 None。

## 交付

- 已补充原生 Popover、关闭按钮、原始尺寸滚动查看及打开原图入口，正文原有图片链接保留。
- Maven 定向 Controller 测试 6 项全部通过，覆盖预览生成、XSS 清理、CSP、HEAD 和既有分享状态。
- 桌面合成页面实测通过，图片预览、1600px 原始尺寸、Esc 关闭及焦点返回、关闭按钮和外部点击均可用。
- 截图位于 backend/yudao-module-pms/target/pms-share-page/desktop.png。mobile.png 的移动端模拟参数在浏览器工具轮次间被重置，截图实际为桌面尺寸，不能作为手机验收证据。手机及企业微信真实 WebView 尚未验证。
- git diff --check 通过。未部署，需发布更新后的 PMS 后端才能在实际分享页生效。
- 浏览器提示 Ego Lite 可更新，本次沿用现有版本完成检查。
- 本工作流文件写入完成，释放所有权。
