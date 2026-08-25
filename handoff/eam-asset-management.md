# Workstream: eam-asset-management

- **ID**: eam-asset-management
- **Goal**: 新增 `yudao-module-eam` 企业资产管理模块（数字资产 / 设备资产 / 办公用品），
  含分类与分类驱动的自定义字段、资产台账、流转、盘点、维修、报废、编号规则、二维码、
  Excel 导入导出、统计报表，以及配套 Vue 管理后台页面和独立数据库模块脚本。
- **Non-goals**:
  - 不做折旧（用户明确要求移除）
  - 不做扫码交互（本期只生成二维码，扫码端后续再排）
  - ~~不做 React workbench 页面（本模块为管理员后台功能，按 AGENTS.md 归 Vue admin）~~
    **已于 2026-08-24 推翻** — 见下方「决策变更：EAM 迁移至 React workbench」
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

## 2026-08-24 决策变更：EAM 迁移至 React workbench

**推翻的原决策**：Non-goals 中的「不做 React workbench 页面（本模块为管理员后台功能，按 AGENTS.md 归 Vue admin）」。

**新决策**：用户明确要求把 EAM 全部 8 个功能域整体迁到 React 员工工作台，Vue admin 侧后续下线。
理由是员工需在统一工作台内完成资产全生命周期操作，不再切换到 admin 面板。

**已交付**（`frontend/workbench/`）：

| 文件 | 说明 |
|---|---|
| `src/services/api.ts` | 新增 EAM 类型区 + `api.eam` 命名空间，覆盖 9 个 Controller 的全部端点 |
| `src/services/eam.ts` | 枚举常量、`buildEamTree`、`filterCategoryTree`、`previewAssetCode`、`pruneExtFields` 等纯函数 |
| `src/services/eam.test.ts` | 12 个纯函数单测 |
| `src/services/useDict.ts` | 字典加载 hook（复用 `api.dictDataByType`） |
| `src/components/AssetSelect.tsx` | 资产远程搜索选择器（300ms 防抖 + 竞态防护） |
| `src/components/DynamicFields.tsx` | 分类驱动的动态字段渲染 |
| `src/components/AssetImportModal.tsx` | 台账 Excel 预检/确认导入、模板下载、行级警告展开 |
| `src/components/CategoryImportModal.tsx` | 分类配置 Excel 预检/确认导入、模板下载、冲突阻断 |
| `src/services/download.ts` | 带鉴权的 Blob 下载；统一处理二进制错误响应 |
| `src/pages/Eam{Asset,Category,CodeRule,Inventory,Repair,Scrap,Statistics,Transfer}Page.tsx` | 8 个页面 |
| `src/styles/pages/eam.css` | 统一样式，全部走 `--crm-*` token |
| `src/constants.ts` | `APP_ROUTES` 8 条 + `RENDERABLE_APP_ROUTES` 8 条 |
| `src/layouts/RouteHost.tsx` | 8 条路由分发 |
| `src/styles/styles.guard.test.ts` | 新增 page/pane padding 锚点与 `eam-category-layout` 列宽锚点 |

**关键事实**（核验自 `script/sql/mysql/migrations/eam/V002__eam_menu.sql`）：
- 菜单路径为 `/eam/{category,asset,transfer,inventory,repair,scrap,code-rule,statistics}`
- `component` 字段仍指向 Vue 路径（如 `eam/repair/index`），但 workbench 走 `menu.path` 匹配，
  **后端菜单无需修改**即可让 workbench 渲染
- 权限码沿用 admin 侧的 `eam:*`，页面按 `permissions` 数组控制按钮显隐

**验证**：`npx tsc --noEmit` 干净；`npm test` 58 文件 353 用例全绿（含 12 个新增）；`npm run build` 通过。
guard 反向验证：故意把 `--crm-pane-pad` 改成字面量后测试确实转红，锚点非空转。

**本轮补齐**（2026-08-24）：
- 台账 Excel：预检、确认导入、模板下载、更新已有资产标签、行级映射/默认值/警告展开。
- 分类配置 Excel：预检、冲突阻断、确认导入、模板下载、分类/字段差异表。
- 资产附件：复用 `/infra/file/upload`，表单上传后写回 `fileUrls`。
- 资产导出：通过带鉴权的 Blob 请求下载 `资产台账.xlsx`。
- 资产二维码：改为带鉴权的 Blob 请求并使用 object URL，关闭/卸载时释放 URL；修复原 `<img src>` 不携带 Authorization 的问题。

**本轮补齐**（2026-08-24，字段配置与二维码功能对齐 admin）：
- 字段配置表单补齐 `optionSource`（STATIC / SYSTEM_DICT）与 `dictType`，并遵循后端 `normalizeOptions`：
  非下拉类型清空选项相关字段，系统字典源不留静态选项，`required` 恒为 false，员工表不可见时强制关闭 `collectionRequired`。
- 字段类型补充 `FILE(6)`，下拉类型合法值从 5 扩展到 6。
- 字段配置表单补充 `conditionRule` 条件规则 JSON 输入与校验（必须是对象，留空则置空）。
- `DynamicFields` 支持 `SYSTEM_DICT` 下拉：按 `dictType` 用 `useDict` 加载字典选项（修复 admin 端下拉字段选项恒为空的缺陷）。
- 二维码弹窗补充「打印」功能，只打印标签区域，对标 admin `QrCodeDialog.handlePrint`。

**验证（本轮）**：`npx tsc --noEmit` EAM 相关文件零错误；`npm test` 58 文件 353 用例全绿。

**仍未完成**：admin 侧尚未下线 — `frontend/admin/src/views/eam/` 与 `src/api/eam/` 原样保留，两端目前并存。

**历史缺口（已关闭）**：Excel 导入导出、资产附件上传、二维码鉴权已在本轮完成。

## 2026-08-17 EAM JSON attachment mapping continuation

- **Workstream ID**: eam-asset-management
- **Goal**: 修复 `eam_asset.file_urls` JSON 列在空附件列表写入时收到空字符串的问题。
- **Non-goals**: 不修改公共 `StringListTypeHandler`；不执行数据库迁移；不修改 EAM 之外的业务行为。
- **Branch**: main
- **Worktree**: D:\ZSJ-OS
- **Base commit**: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- **Target branch**: main
- **Ownership scope**: `EamAssetDO.java`、对应聚焦测试、本 workstream handoff 文件。
- **Owner**: Codex（当前任务）
- **Dependencies**: MyBatis-Plus 内置 `JacksonTypeHandler`；不新增依赖。
- **Integration order**: 实体映射 -> 聚焦测试 -> EAM 模块编译。
- **Verification plan**: 验证字段映射使用 JSON handler、空列表序列化为合法 JSON，并运行 EAM 聚焦测试及 Maven compile。
- **Status**: in-progress

### Delivery entry: 2026-08-17 21:18:00 +08:00

- **Beijing time**: 2026-08-17 21:18:00 +08:00
- **Branch**: main
- **Worktree**: D:\ZSJ-OS
- **HEAD commit**: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- **User goal**: 修复新建 EAM 资产时空附件列表写入 MySQL JSON 列失败的问题。
- **Key decisions**: 仅将 `EamAssetDO.fileUrls` 从面向 varchar 的 `StringListTypeHandler` 切换为 `JacksonTypeHandler`；不改公共处理器，不执行数据库变更。
- **Execution or analysis result**: `fileUrls` 现在按 JSON 数组持久化，空列表不再转换为空字符串；新增字段映射回归测试。
- **Changed files**: `backend/yudao-module-eam/src/main/java/cn/iocoder/yudao/module/eam/dal/dataobject/asset/EamAssetDO.java`; `backend/yudao-module-eam/src/test/java/cn/iocoder/yudao/module/eam/dal/dataobject/asset/EamAssetDOTest.java`; `handoff/eam-asset-management.md`.
- **Verification evidence**: `mvn -pl yudao-module-eam -Dtest=EamAssetDOTest test` 通过，1 个测试、0 失败；`mvn -pl yudao-module-eam test` 通过，39 个测试、0 失败；`git diff --check` 通过。
- **Dependency or integration impact**: 不新增依赖；不影响 EAM 外模块；无需数据库迁移。
- **Remaining work**: 未连接实际 MySQL 重放创建请求；部署更新后的后端后需用无附件和有附件资产各做一次 API 冒烟验证。
- **Turn status**: completed
