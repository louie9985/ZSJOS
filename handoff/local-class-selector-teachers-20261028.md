# 班级下拉班主任展示
- 环境：local（环境变量及环境文件未指定）；分支 main；工作树 D:/ZSJ-OS；基线 42fa03db3302282101beb2294b2e90c23991f4e6。
- Owner: Codex；工作流 local-class-selector-teachers-20261028。
- 目标：补齐班级筛选与手工录入选项的班主任展示；不改权限、接口、数据库及既有分班行为。
- 所有权：RegistrationPages.tsx 仅 classFilterOptions 行、两端 StudentEntryForm 的班级 label、student-filter-toolbar-browser.py、本记录。
- 保留 RegistrationPages.tsx 已有其他未提交变更。依赖现有班级接口姓名字段；集成目标与顺序 None。
- 验证：工作台类型检查、现有浏览器筛选回归（桌面/移动）、Admin 单文件编译检查、差异检查。
- 展示规则：班名 · 班主任姓名 · 原有考期；缺失姓名显示“班主任姓名未记录”；既有待分班特殊选项保留。

## 交付
- 已补齐工作台学员筛选班主任姓名快照；两端手工录入缺失姓名时显示明确提示。现有报名分班、调班、恢复服务选项已有姓名，保留待分班特殊项。
- npm run typecheck 通过；git diff --check 通过。浏览器回归在 5174 测试页找不到全部班级控件，未进入本次标签断言；Admin 模板检查因缺少 @vue/compiler-sfc 未运行成功。真实页面展示与 Admin 编译仍未验证。
- 已更新既有浏览器回归的姓名字段及期望标签。未部署、未提交。释放本次文件所有权。
