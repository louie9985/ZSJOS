# 中世健数据库管理编辑契约

## 目标与边界

管理端 `/infra/database-admin` 继续使用 Infra 通用数据库管理能力。Vue Admin 是当前直接调用方；Workbench 使用既有菜单运行模式决定是否打开嵌入页，不新增 React 数据库编辑器。2026-09-06 只读检查中，该菜单的 `workbench_render_mode` 为 `native`，不是 `admin_embed`；本次不更改菜单配置或认证、租户及权限契约。

修复仅影响数据库编辑的数据往返、输入校验及诊断，不更改业务表结构、反馈流程、附件绑定或真实业务记录。数据库通用写入不会调用反馈业务服务，也不会校验附件 URL；附件 ID、名称、大小与文件引用的一致性由相应业务流程负责。

## HTTP 与值类型

保持 `/admin-api/infra/database-admin` 路径、`CommonResult` 包装和既有 `infra:database-admin:query/create/update/delete` 权限。后端独立执行权限判断，前端消费服务器权限控制操作入口。

- `PUT /row/update`：`dataSourceConfigId`、`tableName`、`primaryKeyValue`、非空 `values`。仅更新 `values` 中出现的列；未出现的列不在 SQL `SET` 中。
- `POST /row/create`：`dataSourceConfigId`、`tableName`、非 null `values`，允许 `{}` 使用数据库默认值。省略列、显式 `null`、空字符串有不同含义。
- `DELETE /row/delete`：主键按真实列类型转换、校验和绑定，不依赖字符串隐式类型转换。
- 空更新请求不合法。界面没有实际变化时禁用保存；修改后恢复原值不提交。失败保留草稿供重试。
- 元数据增加 `columnSize`（长度或数值精度）、`decimalDigits`（小数或小数秒位数）、`defaultValue`、`generated` 和 `valueKind`。
- `valueKind` 为 `boolean/integer/decimal/float/date/time/datetime/text/json/readonly`。服务端根据真实列元数据决定，不按列名称猜测。

| 列类型 | 查询/编辑的 JSON 值 | 写入处理 |
| --- | --- | --- |
| BOOLEAN、BIT(1) | boolean 或 null | 布尔开关；后端兼容严格的 `true/false/0/1` 字符串 |
| 整数、DECIMAL/NUMERIC | 十进制字符串或 null | 精确整数范围、unsigned、精度/小数位校验，再以 BigDecimal 绑定 |
| FLOAT/REAL/DOUBLE | 数值字符串或 null | 有限数值；数据库近似数类型仍遵循自身精度 |
| DATE | `YYYY-MM-DD` 或 null | 严格日期解析 |
| DATETIME/TIMESTAMP | `YYYY-MM-DD HH:mm:ss[.fraction]` 或 null | 接受 T 分隔符；保留列支持的小数秒，不经过浏览器时区或全局 epoch 序列化 |
| TIME | `[-]HHH:mm:ss[.fraction]` 或 null | 支持 MySQL 的负时长及超过 24 小时时长，最大 838:59:59 |
| CHAR/VARCHAR/NCHAR/NVARCHAR | 原始字符串或 null | 保留空字符串、空白和换行；按字符长度校验 |
| TEXT/CLOB | 原始字符串或 null | 保留空字符串、空白和换行；不使用 JDBC 的 `COLUMN_SIZE` 前置截断，实际容量由数据库校验 |
| 原生 JSON | JSON 文本字符串或 null | 校验语法而不解析重写；数据库自身可能规范化 JSON 存储 |
| 二进制、多比特、未支持类型 | 只读值 | 二进制按十六进制展示；拒绝写入 |

主键、自动增长列、生成列、敏感列不可通过编辑表单修改；没有受支持单列主键的表只读。MySQL `TINYINT(1)` 使用原生元数据识别为整数，不因 Connector/J 的 `tinyInt1isBit` 配置而误当布尔列。

`value_snapshot_json` 是 LONGTEXT 时，按普通文本原样处理，即使文本不符合 JSON 语法也不会按字段名拒绝。原生 JSON 列才执行 JSON 语法验证。签名 URL 是否过期不会影响 SQL 保存。

查询映射中的 SQL NULL 即使被 JSON 序列化器省略，前端仍归一为 NULL，不误判为修改。新增时使用默认值、NULL 或具体值由用户显式选择。

## 错误与日志

| 错误码 | 含义 |
| --- | --- |
| 1001008008 | 未分类的数据库执行失败 |
| 1001008009 | 字段值不符合列类型 |
| 1001008010 | 非空列缺值或缺少默认值 |
| 1001008011 | 唯一记录冲突 |
| 1001008012 | 关联约束不满足 |
| 1001008013 | 数据长度、范围或精度超限 |
| 1001008014 | 数据源账号访问被拒绝 |
| 1001008015 | 数据行不存在 |
| 1001008016 | 其他数据库约束不满足 |

其他既有错误码继续有效。若驱动返回更新影响行数为零，查询相同主键是否存在，区分幂等更新与记录已删除。差量更新不覆盖其他普通列，但不提供同一列的并发冲突检测；数据库 `ON UPDATE` 字段仍可能自动更新。

SQL 异常只记录操作、数据源配置编号、受限表名、SQLState、数据库错误码和现有 traceId。不得记录原始异常消息/堆栈、行值、SQL 参数、签名 URL 或连接凭据。所有数据库管理 HTTP 方法关闭访问日志的请求参数及响应正文记录；操作日志不再记录主键值。前端沿用共享 HTTP 客户端展示安全业务错误，编辑草稿保留；加载失败提供重试入口。

## 验证与运行

环境只读核验：MySQL 8.4.11、Connector/J 9.7.0，开发主连接配置 `connectionTimeZone=Asia/Shanghai`。实际反馈表有 BIT(1)、DATETIME、LONGTEXT 和原生 JSON 列。临时表及 JDBC 受控测试均复现旧式 `setObject("false")` 写入 BIT(1) 的错误 `1406 / 22001`；原始线上请求未留存，不能断言该请求不存在其他错误。

后端聚焦测试：从仓库根目录运行 `mvn -f backend/pom.xml -pl yudao-module-infra -am test "-Dtest=DatabaseAdmin*Test" "-Dsurefire.failIfNoSpecifiedTests=false"`。常规测试覆盖类型、权限、请求约束和日志开关。

真实 MySQL 测试通过环境变量 `DATABASE_ADMIN_MYSQL_URL`、`DATABASE_ADMIN_MYSQL_USER`、`DATABASE_ADMIN_MYSQL_PASSWORD` 显式启用。URL 必须指向 `127.0.0.1:3306/codex_db_admin_*` 专用验证库并含连接参数，勿设置 `useAffectedRows`，测试分别验证两种模式。凭据通过本地环境提供，不写入仓库或命令参数。测试仅新增唯一命名的虚构数据表，保留测试数据，不删改已有表。2026-09-06 本地验证库为 `codex_db_admin_20260906`，未复制业务数据；后续清理需要独立明确授权。

前端从 `frontend/admin` 运行 `node --experimental-strip-types --test tests/databaseAdminRowEditor.test.ts`，以及 `pnpm ts:check`、相关文件 ESLint/Stylelint/Prettier 检查和 `pnpm build:local`。

隔离浏览器验证使用 `pnpm exec vite --config tests/browser/databaseAdmin/vite.config.ts`，地址 `http://127.0.0.1:5188/`。它直接加载真实数据库管理 Vue 页面及 Dialog，但 API 仅连接测试内存适配器，绝不连接数据库，不属于生产构建入口。`?fail=1` 模拟一次写入失败，`?loadFail=1` 模拟一次加载失败，`?readonly=1` 隐藏写入入口，`?empty=1` 模拟空数据。截图及交互检查覆盖 1440x1000 和 390x844。

生产前后端须配套交付并刷新旧页面；本次不执行共享后端重启、发布或真实记录修改。回退需一起回退前后端，不能只恢复旧文本编辑器；无需数据库结构回滚。登录后的真实业务入口及 Workbench 实际路由验收仍需有效会话与已更新运行时；隔离组件测试不代替这部分验收。

### 2026-09-06 验证结果

- 后端 19 项聚焦测试通过：服务 10、类型与错误分类 4、权限/校验/日志契约 3、MySQL 两种影响行数模式 2；整个 Infra reactor 编译成功。
- MySQL 虚构数据验证了原始布尔错误、JSON 单列写入、其他普通列不变、精确大整数/小数/微秒、NULL/空串/默认值、唯一/关联约束、记录不存在及生成列拒绝。中文“测试”的数据库 HEX 为 `E6B58BE8AF95`。
- 前端 5 项 Node 测试、作用域内 ESLint/Stylelint/Prettier 检查及最终 `build:local` 通过。
- 实际页面组件在隔离适配器下完成桌面/手机截图与交互检查：无变化禁用保存、单列 payload、布尔 true、错误后保留输入并重试、加载重试、空数据、无写入权限以及默认值新增 `{}`。
- `pnpm ts:check` 仍失败，共 22 个其他模块诊断，涉及流程设计器岗位类型、DocAlert 未使用变量、EAM 表单等；无 databaseAdmin 文件诊断。这不等于全项目类型检查通过。
- 真实浏览器会话提示登录过期，没有进行真实业务记录保存或共享后端重启，因此真实登录入口的配套运行时验收仍未完成。MariaDB 未提供专用验证环境，本次数据库证据限于 H2 和 MySQL 8.4。
- 代码审查复核：MySQL/MariaDB 的 `INSERT INTO table () VALUES ()` 是合法的空默认值插入语法，且已由隔离 MySQL 测试实际通过；未对该处做无效改动。`TEXT/LONGTEXT` 的 JDBC `COLUMN_SIZE` 前置限制已移除，仅保留有界字符列检查。
