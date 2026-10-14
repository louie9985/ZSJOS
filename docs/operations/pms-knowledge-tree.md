# 知识库目录名称与宽度

Vue 管理端知识库桌面目录默认宽度为 320px，支持拖动目录与正文之间的分隔条，范围为 240～560px，并为正文保留至少 480px。双击分隔条或聚焦后按 Home 恢复默认；左右方向键每次调整 16px。

宽度在当前浏览器的 `pms:knowledge-library:sidebar-width` 中记忆，跨知识库复用。窗口变窄时临时收缩，不覆盖已保存的偏好。窗口不超过 900px，或工作区不足 736px 时，目录和正文上下排列，不显示分隔条。存储禁用或数据异常不影响页面使用。

文件夹、在线文档与上传文件的名称保持单行省略。仅实际溢出的名称在悬停 300ms 后显示完整纯文本提示；键盘聚焦树节点时也显示完整名称。提示框可换行，最大 480px，并限制在视口内。触屏通过打开节点查看完整标题。

本改动不改变接口、权限、层级、排序或名称。测试入口 `frontend/admin/test/knowledge-tree.html` 使用合成记录挂载生产布局与目录组件，不接入真实业务数据；在管理端目录启动 `pnpm exec vite --config test/knowledge-tree.vite.ts`，然后运行 `python test/knowledge-tree-browser.py`。组件验证不代表已部署或已完成真实账号在线验收。

目录组件的独立类型检查：在管理端目录运行 `pnpm exec vue-tsc --noEmit --project test/knowledge-tree.tsconfig.json`。该配置沿用管理端编译规则，只纳入本次目录组件及其直接依赖，避免由全局组件声明引入其他业务页面。
