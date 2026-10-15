# 考期状态配色优先级修复

## 登记（2026-10-15）
- 工作流 ID：local-exam-status-color-20261015；环境：local；负责人：Codex。
- 分支：main；工作区：D:\ZSJ-OS；基线：315efd729f2e049fc2639194567d86077ae91184；目标分支及集成顺序：None。
- 目标：仅 PUBLISHED 展示自选底色，草稿和撤销使用状态色；同步预览、图例、文档和验证。用户已确认替代原自选底色覆盖状态的规则。
- 非目标：后端、数据库、权限、状态流转、课程日历、提交推送部署；保留其他并行工作。
- 文件归属：frontend/workbench/src/pages/{examCalendarColor.ts,examCalendarColor.test.ts,ExamCalendarMonth.tsx,ExamCalendarMonth.test.tsx,ExamCalendarPage.tsx}；frontend/workbench/test/exam-color.tsx、exam-color-browser.py；docs/api/exam-calendar.md；本记录。
- 依赖：既有主题 token、状态 CSS、颜色转换和隔离浏览器 fixture；无新增依赖。目标文件当前无本地修改，其他工作流记录未发现相同归属。
- 验证：针对性 Vitest、typecheck；隔离 Chrome 桌面/手机、明暗主题、状态色/跨周跨月/预览/发布撤销；仅使用隔离数据，不调用真实业务 API。

## 交付（2026-10-15）
- 已完成：增加按 recordStatus 判断的展示函数并接入单日、多日条；DRAFT/REVOKED 不产生颜色内联覆盖；PUBLISHED 沿用合法色与高对比文字；预览改为发布后效果，清除恢复主色浅底；同步图例、表单说明与 API 文档。
- 颜色数据、后端接口、数据库、权限与状态流转未改；未修改现有 exam-color.tsx，新增浏览器脚本复用该 fixture，并在独立浏览器 sessionStorage 中准备矩阵数据。
- Vitest：examCalendarColor.test.ts、ExamCalendarMonth.test.tsx、ExamCalendarPage.test.ts 共 43 项通过；npm run typecheck 通过；范围内 git diff --check 通过。
- Chrome：python frontend/workbench/test/exam-color-browser.py 通过；390/1440px × default-light/default-dark 四组，每组 54 个单日与跨周跨月片段，实际背景/文字/边框色及撤销虚线均通过；草稿→发布→撤销、保存颜色保留、选色预览与清除恢复默认均通过，无 pageerror。
- 截图和结果：C:\Users\EDY\AppData\Local\Temp\exam-status-color-54mh6f58（四张 PNG 与 results.json）。使用现有本地 Vite 5174，无启停服务操作。浏览器脚本已适配 Ant Design 文本间距、颜色面板关闭和发布刷新后重新打开日期详情的既有交互。
- 边界：上述为源码和隔离浏览器验证，未执行真实账号/后端发布撤销验收，未提交、推送或部署。无依赖或打包配置变化，未运行生产构建。
- 本工作流交付完成，释放登记文件归属。
