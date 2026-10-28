# 学员批注同行布局

- 环境：local（未配置环境变量及环境文件）；工作流：annotation-inline-20261028；owner：Codex。
- 分支：main；工作树：D:\ZSJ-OS；基线：42fa03db3302282101beb2294b2e90c23991f4e6；集成：None。
- 目标：代理学员、代理人标签和输入框同行，无需服务另起一行。
- 非目标：业务、权限、保存逻辑、数据库、部署。
- 所有权：本记录、frontend/workbench/src/components/StudentServiceAnnotationCard.tsx、同目录 StudentServiceAnnotationCard.css。
- 依赖：保留现有未提交的复选框改动；仅调整结构及样式。
- 验证：TypeScript、diff 检查；尝试现有浏览器夹具桌面和移动宽度。
- 布局约定：代理选项与代理人输入保持同行，输入占据剩余宽度；无需服务独占第二行。

## 交付
- 已实现两行布局，保留此前未提交改动、自动保存和选项行为。
- npm run typecheck 通过；目标文件 git diff --check 通过。
- 浏览器验证未完成：现有夹具 127.0.0.1:5186 未运行，桌面和移动实际排布仍待验证。
- 未提交、未部署；文件所有权释放。

## 代理人关键词搜索注册
- local / main / D:\ZSJ-OS；owner Codex；基线沿用。目标：现有学员搜索增加代理人姓名；非目标：代理人选择器、数据迁移、部署。
- 所有权新增：StudentServiceReadService.java、StudentServiceReadServiceTest.java、RegistrationPages.tsx、docs/student-filter-adjustments.md（均位于现有对应模块目录）。此前工作已交付，保留已有修改。
- 依赖：批注 READ 权限，服务关系、租户及逻辑删除约束；使用参数化 LIKE；接口参数不变，Admin 调用兼容。
- 验证：定向 Maven 测试、Workbench typecheck、diff 检查。集成 None。

## 代理人关键词搜索交付
- 现有 keyword 增加有批注 READ 权限时的代理人姓名 LIKE 匹配；EXISTS 限定同服务、同租户及未删除，保留原学员范围。搜索提示同步。
- Maven StudentServiceReadServiceTest 通过；Workbench typecheck、git diff --check 通过。新增测试覆盖无批注权限、有权限的关联及参数化条件。
- 未完成真实账号/API 和浏览器验收；无数据库修改、无部署。所有权释放。

## 无需督学口径注册
- 环境 local；main / D:\ZSJ-OS；基线 0628f72f2c6208a6cc553f3f29b893de8f4ec697；owner Codex；集成 None。
- 目标：批注与快捷筛选统一“无需督学”，筛选是映射 serviceNeeded=false，否映射 true。
- 所有权：本记录、StudentServiceAnnotationCard.tsx、StudentServiceFilters.tsx、services/studentServiceAnnotation.ts 及其测试、docs/student-filter-adjustments.md（均为 Workbench 文件）。
- 非目标：API/数据库字段反转、历史数据修改、部署。依赖：服务端字典选项；验证：映射单测、typecheck 和 diff。

## 无需督学口径交付
- 批注显示“无需督学”，快捷筛选显示“是否无需督学”；服务端字典标签不变，选是提交 serviceNeeded=false，选否提交 true，清除不限制。既有默认需要督学不变。
- 4 项批注/映射单测通过（默认进程池 ENOMEM 后单线程通过）；scoped diff --check 通过。
- Typecheck 因 Node 内存分配失败未完成；浏览器实际交互未验证。未变更接口或数据库、未部署。所有权释放。
