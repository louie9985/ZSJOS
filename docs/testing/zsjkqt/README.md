# 中世健考勤通测试迁移清单

## 2026-10-26 异常改正常专项审查

新增业务覆盖、10 个可执行反例和待确认口径见 [业务审查报告](business-audit-20261026.md)。`normalizationAudit.test.ts` 中 `it.fails` 是未修复问题的预期失败断言，不能把运行器通过数当作业务全部通过。新增 Java 转换矩阵验证保存校验和真实 Excel 回读，异常清单用例明确其当前不覆盖全部人工修订。最终执行证据见 [本轮记录](../../../handoff/local-zsjkqt-business-audit-20261026.md)。

同日后续按用户指定修复 A01/A02/A04，三条反例已转普通回归，新增 10 条相关边界用例；当前专项 196 条包含 189 条普通回归及 7 条未修复反例。无时段 2 小时为新增合成边界，不是已确认存在的真实月报问题；仅整日同假别转换增加总量守恒兜底，半天转换没有擅自分摊。

原始测试基准取自 `D:/AttendanceAutomation` 的当前工作区，包含迁移时尚未提交的规则修订。`original/` 是不可修改的对照归档；后续维护测试应修改对应的 Workbench 或 Java 测试，不回写归档。

## 全量保留与执行映射

| 原桌面内容 | 保留方式 | 网页版执行位置 |
| --- | --- | --- |
| 10 个不依赖 ExcelJS 的 TypeScript 测试文件 | 原样归档，并逐字节复制到业务核心目录 | `frontend/workbench/src/features/zsjkqt/core/*.test.ts`；迁移基准共 102 个执行用例，含参数化展开 |
| `browserSourceParser.test.ts` 的 1 个用例 | 原样归档，改写为 Java | `ZsjkqtExcelEngineTest.parses_sample_wecom_workbook_from_file` |
| `exportedReportParser.test.ts` 的 3 个用例 | 原样归档，改写为 Java | `ZsjkqtExcelEngineTest` 中的严格解析、不含年份拒绝、隐藏元数据恢复用例 |
| Rust `excel_engine.rs` 的 15 个测试 | 原始 Rust 文件完整归档；Java 保留同名测试方法 | `ZsjkqtExcelEngineTest` |
| Rust `lib.rs` 的 3 个人员资料测试 | 原始 Rust 文件完整归档；Java 保留同名测试方法 | `ZsjkqtServiceTest` |
| 46 个编号人工回归场景 | 原文完整归档，保留原编号和预期 | `original/docs/回归测试用例.md` |
| 源月报及九月回归夹具 | 原样归档；九月夹具同时供 TypeScript 测试运行 | `original/` 及 `frontend/workbench/src/features/zsjkqt/core/fixtures/` |

完整的文件及方法对应关系见 [coverage.json](coverage.json)，原始文件 SHA-256 和字节数见 [inventory.json](inventory.json)。2026-10-22 已核对 19 个归档文件的摘要与源工作区一致，并核对 10 个运行中的原 TypeScript 测试文件与归档逐字节一致。归档包含测试所需原始数据，只用于受控开发测试，不作为初始化数据、下载资源或产品静态资源发布。

用户明确拒绝新增 ExcelJS 依赖。因此两个 ExcelJS 测试保留为参考源码，不由 Workbench Vitest 执行；它们的四个断言场景由 Java/POI 测试接续。原 Rust 文件同样不属于网页版运行或编译依赖。仅存档不能替代 Java 对应用例通过。

## 执行入口

在仓库根目录运行 Java 用例（PowerShell 需保留引号）：

```powershell
mvn -f backend/pom.xml -pl yudao-module-zsjos -am '-Dtest=Zsjkqt*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

在 `frontend/workbench` 运行：

```powershell
npx vitest run src/features/zsjkqt
npm run typecheck
npm run build
```

MySQL 用例使用已运行的本地 MySQL 8 容器，建立并保留独立验证数据库；默认不修改业务库：

```powershell
python -B script/sql/mysql/tools/test_zsjkqt.py
```

`--sync-local` 是对本地开发库的实际迁移，会在检查前置状态、保存限定备份后仅应用 V347。它不是测试默认步骤，生产及共享环境的执行需要相应目标的单独授权。测试不删除验证数据库；没有把 DDL 的事务包装当作原子回滚保证。

## 新增网页能力的验证

- `draftSession.test.ts` 验证串行保存、编辑中保存、失败后的脏状态、版本冲突以及创建重试标识。
- `menu.test.ts` 验证同一服务端工作台专用菜单在 React 中可渲染、在 Vue Admin 中被既有过滤器排除，以及 HRM 父级嵌入模式下的原生子菜单行为。
- Java 服务测试验证一个功能权限、共享对象访问、版本冲突、快照校验、实际部门时段初始化和空人员资料。
- `ZsjkqtBoundaryTest` 使用 Spring 方法鉴权、MockMvc 实际上传契约以及真实 MyBatis 数据边界；与仅检查注解声明的测试分开记录。
- Java Excel 测试除原用例外，还验证月份天数、空日补全、异常清单筛选以及导出格式。
- MySQL 脚本验证首次执行、重复执行、缺少前置条件、部分建表恢复、不兼容列类型、第二本版本账本写入失败、UTF-8/HEX、数据保留、乐观更新及租户条件。
- 浏览器验证使用实际 React 组件和隔离 HTTP 响应，覆盖导入、编辑、草稿恢复、失败重试、冲突、导出、续改、权限和不同宽度布局。它不等同于真实账号或已部署后端验收。

浏览器入口为 `frontend/workbench/test/zsjkqt.html`，自动化脚本为 `frontend/workbench/test/zsjkqt-browser.py`。它还验证参数阶段草稿恢复时使用冻结人员资料，以及新导入读取最新公共资料；测试响应全部为合成数据。

## 完整审查修复回归

- `core/reviewFixes.test.ts` 验证同名空账号独立行键、旧草稿保留人员配置键及人工值、参数重建、未确认筛选的下一位，以及原有陪产假/产检假分类输出。`reviewBuilder.test.ts` 的两条人员配置断言改查独立的 `profileKey`；归档原文不改，原计算公式断言保留。
- `draftSession.test.ts` 增加 117～120 字符原名及 Unicode 边界的副本名称；原名不改写。
- `ZsjkqtReviewRegressionTest` 验证并发创建唯一键冲突后的当前读取重放、不同内容/删除冲突、无关约束错误不吞掉、实际月份无效元数据回退、32767 字符边界以及两个假别的保存/导出/回读。`ZsjkqtBoundaryTest` 使用实际 Mapper 核对锁定读取的租户和逻辑删除条件。
- `test_zsjkqt.py` 增加错误索引列、逆序索引、已删除配置的失败及人工修正后的恢复验证，检查失败不能登记或推进两本账本；这些修正仅作用于保留的合成测试库，不自动恢复应用配置。
- `python -B frontend/workbench/test/zsjkqt-review-fixes-browser.py` 在独立浏览器上下文检查 1440/390 宽度的同名人员编辑隔离、未确认下一位、旧草稿重开、原配置键、长名称副本、超长文本不截断及未编辑设置离开。复用已运行的隔离 Vite 入口，可用 `ZSJKQT_FIXTURE_URL` 指定地址，不启停共享服务。

以上是新增验证入口，具体运行结果和部署边界记录在本次 [完整修复记录](../../../handoff/local-complete-review-fixes-20261022-01a16363.md)；归档历史通过数不作为本轮执行结果。

## 结果记录边界

原人工清单中的 46 个编号均已保留；这不表示每一个场景都已完成独立手工验收。`coverage.json` 对这些场景记录原文建议的相关测试位置，未建立单项执行证据的场景不得标为通过。

执行次数会随着新增网页测试增加，不应把当前总数写成长期完成门槛。每次交付的具体通过数、浏览器截图、本地迁移状态和未验证项记录在 [迁移工作记录](../../../handoff/local-zsjkqt-migration-20261022.md)。本清单及摘要核验不构成部署、角色授权或真实账号验收证明。

## 2026-10-22 验证边界

- 考勤通范围：109 个 TypeScript 用例、40 个 Java 用例（Excel 23、服务 14、运行边界 3）、生产构建、隔离浏览器流程，以及 V347 首次/重放/前置失败/部分恢复/错误列类型/账本失败恢复检查已通过。
- 本地开发库：V347 限定同步已执行；两本账本记录文件字节摘要，表结构、保留的部门时段、UTF-8/HEX 与限定数据结果已比对，原角色授权未改变。同步前限定备份为 `backups/mysql/zsjkqt-before-20261022172540304852.json`。
- 全仓库静态检查：仍受另外两项 Java 映射缺少 core schema 定义影响，分别为 `zsjos_account_diagnosis_metric` 和 `zsjos_student_entry_command`；考勤通的 baseline/desired schema 两表块已经同步。
- 完整 fresh 链：在独立受控数据库中执行，运行到 PMS V002 时因 `PMS V002 requires PMS V001` 失败，不能宣称全链新环境初始化通过。
- 升级入口：已有 V019→V021 限定升级套件通过，覆盖其版本记录、唯一约束、租户与重放；这不是从任意部署版本到最新版本的完整升级证据。

上述范围之外的问题保留原状，须在对应功能或发布任务中解决后重新进行全链验收。
