# 备份业务数据合并进测试库

日期:2026-09-16
执行人:Claude

## 目标

把 `ruoyi-vue-pro-after-legacy-20260915-184129.sql`(备份库,2026-09-15 18:41)的
**业务数据**合并进当前 `zsjos` 测试库。

约束:
- 字典、菜单、角色权限保留测试库自己的(重建版更新),不能被备份覆盖
- 测试库原有业务数据全部清空
- 备份中 `KZ` 开头的客资删除
- 审计日志:清空测试库的,改用备份的
- 身份对不上的,按现有表结构新增

## 关键发现:身份字段是多态的

备份里 `*_user_id` 这类列名**混用 ID 空间**,单表校验会得出"大量悬空"的错误结论:

| 空间 | 说明 |
|---|---|
| `staff` | `system_users.id` |
| `partner_account` | `zsjos_partner_account.id` |
| `partner_main` | `zsjos_partner.id`(注意不是 account) |
| `poly` | `source_user_id` / `provider_owner_id`,由同表 `provider_owner_type` 判定 |
| `staff_or_partner_account` | 早期数据串空间,需两段兜底 |

实测数据:
- `provider_owner_type='system_user'` 时 `source_user_id` → 员工,41/42 个值可解析
- `provider_owner_type='partner'` 时 `source_user_id` → 兼职账号,96/98 个可解析
- `creator` / `updater` 也被兼职账号污染(如 `creator=2`、`creator=5`)
- 两个空间数值重叠 7 个:`[1, 107, 108, 109, 110, 111, 113]`

纯兼职提交的客资:`zsjos_lead.source_user_id = zsjos_partner_account.id`,
订单继承:`zsjos_order.submitter_user_id = lead.source_user_id`。
正确链路是 `partner_id` → `zsjos_partner` + `provider_owner_type` 判别。

## 身份映射

- 员工:备份 210 人,按 wecom_user_id → 手机号 → 用户名 → 昵称 优先匹配,
  命中 44 人(复用测试库现有 ID)
- 未命中的 166 人**沿用备份原 ID**(107–522),测试库现有员工占 1–61,两段不重叠,
  业务数据里的引用不需要翻译即自动对齐
- 兼职:249 个主体 + 122 个账号,整表导入,ID 不变

## 执行步骤

```bash
cd /opt/zsjos/script/sql/merge

python3 merge_legacy_business_data.py --analyze    # 引用分析
python3 merge_legacy_business_data.py --plan       # 导入计划
python3 merge_legacy_business_data.py --apply      # 生成 /tmp/merge_payload.sql
sudo systemctl stop zsjos-backend.service          # 必须先停服
python3 merge_legacy_business_data.py --write      # 执行
python3 merge_legacy_business_data.py --verify     # 行数校验
python3 merge_legacy_business_data.py --audit-apply  # 审计日志
python3 merge_legacy_business_data.py --audit-write
python3 merge_legacy_business_data.py --dict       # 字典补差预览
python3 merge_legacy_business_data.py --dict-write
sudo systemctl start zsjos-backend.service
```

## 结果

非空 `zsjos_*` 业务表 102 张,合计 689,119 行。

| 表 | 行数 |
|---|---:|
| `zsjos_lead` | 4,427 |
| `zsjos_person` | 4,430 |
| `zsjos_lead_follow_up_record` | 16,762 |
| `zsjos_order` / `_item` | 390 / 397 |
| `zsjos_cashback` | 3,290 |
| `zsjos_withdrawal` | 57 |
| `zsjos_partner` / `_account` | 249 / 122 |
| `zsjos_product` | 30 |
| `zsjos_business_audit_log` | 647,354(导入时) |

聚合值逐一比对一致(客资状态分布、订单金额合计 657,858.43、返现总额 60,964.50、
提现申请/批准总额)。链路校验零孤立。

保留未被覆盖:字典(测试库独有 3 个 dict type + 备份独有的 `eam_book_subject`
已补差)、菜单、角色权限、`zsjos_payment_subject`(含商户密钥)、迁移版本表。

## 已知的源端脏数据(原样保留,非导入错误)

1. `zsjos_lead.id=25` (`KZ202608162154330005`):`provider_owner_type='partner'`
   但 `source_user_id=240` 超出兼职账号范围(1–124),备份原值即如此
2. `zsjos_lead.id=56` (`LD202605270007`):`owner_user_id=20` 在两表中均不存在
3. `zsjos_lead.owner_user_id` 有 216 行为空
4. 备份里 3 行 `source_type` 存成了字符串 ``'`source_type`'``(dump 语法残骸)

`operator_user_id=0` 是"系统"哨兵值(定时/自动派单),原样保留。

## 踩过的坑

脚本调试过程中遇到并修复的问题,记录以免重蹈:

1. **TSV 切分会丢行** —— 字段内含换行的行被整行跳过。改用 `JSON_OBJECT` +
   `0x1E` 分隔符取数
2. **`group_concat` 默认只 1024 字节** —— 必须 `--init-command` 调大;整表
   group_concat 超 `max_allowed_packet` 会**静默返回空**(审计日志 64.7 万行就这样
   少了 64 万),必须分页
3. **裸 hex 字面量会给 varchar 列塞数字** —— `0x3631` 被当数字解析成 13873 而不是
   `'61'`,导致 `owner_user_id` 全部错乱。必须 `CAST(0x... AS CHAR)`
4. **`JSON_OBJECT` 对 decimal 丢尾零、对 bigint 丢精度** —— 数值列用
   `CAST(col AS CHAR)` 取,写库时按数字字面量还原
5. **`bit(1)` 列在 `JSON_OBJECT` 里变 base64 或裸 NUL** —— 取值时 `+0` 转 0/1
6. **JSON 列解出 dict 不能 `str()`** —— 会得到 Python 单引号字面量,须 `json.dumps`
7. **mysql 客户端需要 `--binary-mode`** —— `b'0'` 字面量含 NUL 字节
8. **`--raw` 模式必须开** —— 否则 JSON 里的换行被转义成字面 `\n`

## 备份文件

- 合并前测试库全量备份:`/opt/zsjos-runtime/backups/zsjos-testdb-before-merge-20260916-111213.sql`
  (sha256 `b1bb707c0d71bba65e039ffc0c5ed9bc2eda6b008770c13d7ac757897ede588d`)
- 源备份:`/opt/zsjos-runtime/backups/ruoyi-vue-pro-after-legacy-20260915-184129.sql`
- 暂存库:`zsjos_staging`(514 张表,615 MB)—— 保留待确认,确认无误后可 DROP

---

# 后续处理(2026-09-16 下午)

## 1. KZ 客资全部清除

测试库的 52 条 `KZ*` 客资是 2026-08-08 ~ 09-07 的联调/验收数据
(昵称「全链路测试」「E2E0820有效成交A」「自动测试…」),按要求删除。

**软删除**(`deleted=1`),不是物理 DELETE —— 业务表都按 `deleted` 过滤,
行留在库里可随时回滚。

依赖链一并软删除,共 554 行:

| 表 | 行数 | | 表 | 行数 |
|---|---:|---|---|---:|
| `zsjos_lead` | 52 | | `zsjos_order` | 15 |
| `zsjos_person` | 52 | | `zsjos_order_item` | 14 |
| `zsjos_lead_intended_product` | 63 | | `zsjos_order_approval_round` | 14 |
| `zsjos_lead_assignment_history` | 141 | | `zsjos_order_command` | 26 |
| `zsjos_lead_attachment` | 16 | | `zsjos_opportunity` | 18 |
| `zsjos_lead_follow_up_record` | 34 | | `zsjos_service_relation` | 10 |
| `zsjos_cashback` | 9 | | `zsjos_collaboration_group` | 10 |
| 其余 11 张 | 60 | | `zsjos_media_account` | 5 |

执行脚本:`script/sql/merge/kz_cleanup.sql`(可重复执行)
行快照:数据库 `zsjos_kz_backup_20260916`(565 行)

复查:live 客资 4,427 → **4,375**;live 人档 4,430 → **4,378**;
live 订单 390 → **375**;悬空引用 **0**。

另补删 2 笔「人档已删但订单还在」的遗留测试单(order 14/19,
`自动测试0813182739002` / `来个测试1`),快照表后缀 `_stray2`。

## 2. 员工为什么有 227 个

备份 `system_users` 210 行,来源混杂:

- **7 个** ruoyi 模板自带演示账号(`admin107`~`aoteman`,2022-02 建)
- **约 203 个** 2026-05-25 之后由企微/落地页自动建的老系统账号,
  其中 **118 个用手机号当用户名**(`15905659645`、`13826082450`…),
  195 个 mobile 为空,152 个已停用

匹配逻辑(`build_staff_map`)按 wecom → 手机号 → 用户名 → 昵称 找,
测试库 61 行里命中 44 个真实员工;剩下 166 个按**备份原 ID**补建,
好让业务数据的归属引用天然对齐。

那 166 个里有 126 个只有占位作用 —— 备份的老账号在被引用的同时
也被整体搬了进来,这才是"员工变多"的原因。

## 3. 员工数据已收尾

执行脚本:`script/sql/merge/cleanup_imported_staff.py`

| 处理 | 数量 | 说明 |
|---|---:|---|
| 删除 | 116 | 业务数据零引用,纯占位(id 108、228、229、246…) |
| 保留 | 50 | 被客资归属人/跟进人/审计操作人引用,删了归属就断 |
| 禁用 | 12 | 7 个重名壳(2/3/4/6/7/8/11)+ 5 个演示账号(107~113) |

结果:`system_users` 227 → **111**;可登录账号 **73** ——
就是 44 个真实员工 + 29 个业务数据真正归属到的历史账号。

备份:`zsjos_kz_backup_20260916.system_users_before_cleanup`(227 行)

保留的那 50 个里,29 个是老系统真实业务员(叶老师、程振建、程腾、
沈瑞亮、陈丹…),21 个是 ruoyi 演示/测试账号,业务数据的
`owner_user_id` / `operator_user_id` 指向它们,必须留着。

重名壳(测试期在 1-61 段建的 `lijuncheng`/`huanghaijuan`…)与真实员工
(`LiJunCheng`@39 等)重复,已禁用;引用很少(9 条 `source_user_id`),
未做合并,保持可逆。

## 4. PMS 模块数据已导入

备份库有 **33 张 `pms_*` 表、847 行**,测试库的 schema 是按
`core,hrm,fms,eam` 建的,这些表从未创建 —— 但 `yudao-server.jar`
里已经打进 `yudao-module-pms`。

处理:
1. 从 `zsjos_staging` 复制 33 张表的 DDL 到 `zsjos`
2. 导入 847 行,`creator`/`updater`/`*_user_id` 按员工映射换算

脚本:`script/sql/merge/import_pms_data.py`

| 分组 | 表 | 行 |
|---|---:|---:|
| 项目 / 迭代 / 工作项 | 14 | 253 |
| 知识库 | 19 | 594 |

**已知问题**:6 个引用值(`user_id=100/103/104/114/115/118`)在备份库里
就指向不存在的用户 —— 备份自身的脏数据,原样保留。

PMS 菜单(8000-8999)在测试库里已经存在(60 条),无需补。

**注意**:`ZSJOS_DB_MODULES` 目前是 `core,hrm,fms,eam`,不含 `pms`。
表和数据已在库里,但要让前端 PMS 菜单可用,需要另行确认是否把 `pms`
加进 `ZSJOS_DB_MODULES` 并跑模块迁移。

## 5. zsjos_staging 可以删

`zsjos_staging` 是从源备份重放出来的只读暂存库(514 张表,615 MB),
**只是导入工具的中间产物**,没有任何运行时配置引用它
(`.env.production`、systemd unit、代码里都搜不到)。

它特有的内容:
- 33 张 `pms_*` → 已导入
- 5 张 `yudao_demo0{1,2,3}_*` → 未导入(不在本次范围)
- `zsjos_role_menu_backup_20260904` → 测试库自有的临时表,不需要

删除后如需重跑合并,可用源备份
`/opt/zsjos-runtime/backups/ruoyi-vue-pro-after-legacy-20260915-184129.sql`
重新建库。

## 备份清单

| 内容 | 位置 |
|---|---|
| 本轮全部待删行 | 库 `zsjos_kz_backup_20260916`(32 张表,565 行) |
| 清理前 system_users | 同库 `system_users_before_cleanup` |
| 合并前全量 | `backups/zsjos-testdb-before-merge-20260916-111213.sql` |

## 6. PMS 已改造成正式数据库模块

上一节说 PMS 是「jar 里有模块、表是手工建的、manifest 里没有」的半吊子状态,
现在收敛成和 hrm/fms/eam 一样的可选模块:

| 文件 | 内容 |
|---|---|
| `script/sql/mysql/modules/pms.json` | 模块清单(`dependsOn: core`) |
| `script/sql/mysql/schema/pms.sql` | 33 张表的权威结构定义 |
| `script/sql/mysql/migrations/pms/V001__pms_schema.sql` | 安装迁移(全部 `IF NOT EXISTS`) |
| `script/sql/mysql/verify/pms.sql` | 6 项只读校验 |

配置同步更新为 `ZSJOS_DB_MODULES=core,hrm,fms,eam,pms`:

- `deploy/production/.env.example`(入库)
- `deploy/production/.env.production`(忽略,本机)
- `deploy/production/compose.database.yml` 的默认值
- `/opt/zsjos-runtime/.env.production`(忽略,本机)

### 执行记录

```bash
# 备份
mysqldump ... zsjos > backups/zsjos-before-pms-module-20260916.sql

# 重建 migrator 镜像(旧镜像里没有 pms 清单)
docker build -t zsjos-db-migrator:local -f deploy/production/db-migrator/Dockerfile .

# 计划 -> 迁移 -> 校验
docker run --rm --network host -e ZSJOS_DB_MODULES=core,hrm,fms,eam,pms \
  -e ZSJOS_DB_HOST=127.0.0.1 -e ZSJOS_DB_NAME=zsjos \
  -e ZSJOS_DB_MIGRATION_USER=zsjos_migrator \
  -e ZSJOS_DB_PASSWORD_FILE=/secrets/mysql-migration-password \
  -v /opt/zsjos-runtime/secrets:/secrets:ro \
  zsjos-db-migrator:local migrate production
```

结果:`pms/V001__pms_schema.sql` 已应用,`zsjos_module_schema_version` 记录
`pms / V001`。计划阶段报的 `Unexpected schema drift: None` —— 我手工建的表和
`schema/pms.sql` 完全一致,迁移是幂等的空操作,847 行数据原样保留。

`verify/pms.sql` 6 项全 OK。

### 两个发现

1. **`test-fresh` 有 16 项 core 校验失败,与 PMS 无关**。做了 A/B:
   摘掉 `modules/pms.json` 再跑还是同样 16 项。这些是数据级的 base-state 断言
   (`V063 cashback defaults`、`lead_filter_versions` 等),在空库 bootstrap
   后本来就对不上,是既有问题,不是本次引入的。
2. **迁移前发现 `zsjos` 里残留一张 `kz_cleanup_backup_20260916`**
   (上一步 KZ 清理时建的 ID 索引表,不是数据快照)。已 DROP ——
   完整的待删行在 `zsjos_kz_backup_20260916` 库里,没丢。
