# Workstream: eam-asset-management

- **ID**: eam-asset-management
- **Goal**: 新增 `yudao-module-eam` 企业资产管理模块（数字资产 / 设备资产 / 办公用品），
  含分类与分类驱动的自定义字段、资产台账、流转、盘点、维修、报废、编号规则、二维码、
  Excel 导入导出、统计报表，以及配套 Vue 管理后台页面和独立数据库模块脚本。
- **Non-goals**:
  - 不做折旧（用户明确要求移除）
  - 不做扫码交互（本期只生成二维码，扫码端后续再排）
  - 不做 React workbench 页面（本模块为管理员后台功能，按 AGENTS.md 归 Vue admin）
  - 不改动 ZSJOS、CRM、HRM、FMS 任何既有行为
- **Branch**: main
- **Worktree**: /Users/louie/Documents/ChatGPT/ZSJOS 2
- **Base commit**: 69741138
- **Target branch**: main
- **Owner**: louie Alex（AI 协助实现）

## Ownership scope

新增文件（不修改任何既有文件）：

```
backend/yudao-module-eam/**                     90 个 Java 文件 + pom.xml + 测试资源
script/sql/mysql/modules/eam.json
script/sql/mysql/schema/eam.sql
script/sql/mysql/migrations/eam/V001__eam_schema.sql
script/sql/mysql/migrations/eam/V002__eam_menu.sql
script/sql/mysql/migrations/eam/V003__eam_dict.sql
script/sql/mysql/verify/eam.sql
frontend/admin/src/api/eam/**                   8 个 API 模块
frontend/admin/src/views/eam/**                 17 个 Vue 组件
```

待人工修改的既有文件（本次会话因沙箱权限无法读取，故未改）：

```
backend/pom.xml                 需在 <modules> 增加 yudao-module-eam
backend/yudao-server/pom.xml    需增加 yudao-module-eam 依赖
```

## Dependencies / integration order

1. 先应用两处 pom 改动，模块才会进入 Maven reactor
2. 再执行 `ZSJOS_DB_MODULES=core,eam` 的数据库安装
3. 最后构建前端

数据库模块 `eam` 依赖 `core`（仅引用 system_users / system_dept 的标识符，无外键）。

## Verification plan

| 检查项 | 命令 | 本次状态 |
| --- | --- | --- |
| 后端编译 | `mvn compile -pl yudao-module-eam -am` | **未执行**（沙箱阻断，见下） |
| 单元测试 | `mvn test -pl yudao-module-eam` | **未执行** |
| 数据库清单 | `ZSJOS_DB_MODULES=core,eam python script/sql/mysql/tools/zsjos_db.py check` | **未执行** |
| 全新安装 | `... test-fresh` | **未执行** |
| 存量升级 | `... test-upgrade` | **未执行** |
| 前端类型 | `cd frontend/admin && pnpm ts:check` | **未执行** |
| 前端构建 | `pnpm build:local` | **未执行** |
| 浏览器验证 | 桌面 + 移动宽度 | **未执行** |

已完成的静态检查（脚本核对，非编译）：

- 90 个 Java 文件括号配平，package 声明与目录结构一致，类名与文件名一致
- `eam.json` 为合法 JSON
- 权限字符串三方一致：后端 `@PreAuthorize` 34 个 = V002 菜单 SQL 34 个，前端
  `v-hasPermi` 27 个为其子集，无孤儿权限
- V002 的 8 个菜单 `component` 路径与实际 Vue 文件一一对应
- V001 建表 10 张 = verify/eam.sql 断言的 10 张（初稿写成 9，已修正）
- V002 菜单行数 43 = verify 断言的 43（初稿写成 42，已修正）

## Unresolved risks

1. **编译未验证（高）**：本次会话中途 macOS 文件系统权限收紧，Bash / Read / Maven
   均无法读取仓库既有文件，只能写新文件。因此后端从未编译过，前端从未构建过。
   所有 Java / Vue 代码均为静态检查通过、编译状态未知。合入前必须完整跑一遍上表命令。
2. **BPM 审批未真正接入（高）**：`yudao-module-bpm` 源码在本次会话中不可读
   （`Operation not permitted`），`~/.m2` 亦无编译产物，无法取得 `BpmProcessInstanceApi`
   的真实签名。已用 seam 模式落地：
   - `framework/approval/EamApprovalService` —— 审批抽象接口
   - `framework/approval/EamDirectApprovalService` —— 直通实现（`approvalRequired()` 返回 false）
   当前行为：领用 / 借用 / 调拨 / 报废**提交即生效，不产生审批环节**。
   接入 BPM 需新增一个 `EamBpmApprovalService`（bean 名 `eamBpmApprovalService`，
   直通实现上的 `@ConditionalOnMissingBean` 会自动让位），并在 BPM 侧部署
   `eam-transfer`、`eam-scrap` 两个流程定义。业务代码无需改动。
3. **zxing 依赖未确认（中）**：pom 中声明了 `com.google.zxing:core`（hutool `QrCodeUtil` 的
   运行时依赖）。未能读取 `yudao-dependencies/pom.xml` 确认其是否已有版本管理；
   若无 `<dependencyManagement>` 条目，该依赖会因缺少版本号导致构建失败，需补版本或改用
   已有依赖。按 AGENTS.md §5，新增依赖需单独确认。
4. **菜单 ID 段未与实际库核对（中）**：EAM 占用 7100–7199。未能查询 `system_menu`
   当前最大 ID 确认该区间空闲。V002 已内置守卫：区间内若存在非 EAM 菜单则直接
   `SIGNAL` 中止，不会静默覆盖。
5. **租户维度的默认编号规则（低）**：V003 只为 `tenant_id = 1` 插入全局兜底编号规则。
   多租户环境下其他租户需自行配置，否则该租户创建资产会报 `CODE_RULE_NOT_EXISTS`。

## Status

`in-progress` — 代码完成，验证全部待补。**不可在未编译验证前合入。**
