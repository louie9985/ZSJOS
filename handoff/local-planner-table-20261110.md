# 学习规划师收集待办表格

## 登记 2026-11-10 北京时间
- ID local-planner-table-20261110，owner Codex，环境 local（环境变量与环境文件均未指定）。
- 分支 main，工作树 D:/ZSJ-OS，基线 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。
- 目标 补齐待办表格列设置验收和全局表头排序。
- 范围 ExamPlannerProvider.tsx、services/examCollection.ts、ExamCollectionPlannerVO.java、ExamCollectionReminderService.java、ExamCollectionPlannerMembersTest.java、test/exam-planner-share.tsx、docs/business-table.md、本记录。
- 保留现有未提交改动，在当前实现上增量修改。权限和数据归属沿用原契约。
- 依赖 既有 BusinessTable 和档案投影。集成目标与顺序 None。
- 验证 TypeScript、定向测试、隔离 fixture 浏览器列设置与排序。

## 交付 2026-11-10 09:44:39 北京时间
- 分支、工作树、HEAD 沿用登记。
- 结果 待办沿用 BusinessTable full，七列声明远程排序，排序变化重置第一页，取消排序恢复名单顺序；移除手动刷新进度入口，自动轮询与失败重试沿用既有机制。
- 后端 仅在指定排序字段时读取授权筛选后的完整名单，沿用档案投影后全局排序再分页；空值末尾，同值保留稳定名单顺序。参数采用字段白名单。
- 变更文件 登记所列范围中除 test/exam-planner-share.tsx 外的实现、测试、文档与本记录。保留原有未提交改动。
- 验证 npm run typecheck 通过；BusinessTable 7 项组件测试通过；入口 guard 有 5 项失败，涉及既有路径识别及其他表格，未扩大修复。Maven 定向 ExamCollectionPlannerMembersTest 1 项通过，包含升降序、跨页、授权查询及档案投影。定向 git diff --check 通过。
- 浏览器 ego-browser 真实加载 localhost:5174/test/exam-planner-share.html，1440/390px 检查弹窗与设置入口，确认列显隐写入独立 localStorage、表头点击 aria-sort=ascending。仅隔离夹具，真实账号端到端与部署后验收尚待执行。
- 依赖与集成影响 无新增依赖，员工待办接口新增可选排序参数，默认行为兼容；Vue Admin 不消费此专属接口。
- 剩余 大名单完整档案投影排序性能未压测。Ego Lite 提示有可用更新，本次使用现有版本验证。所有权释放。
