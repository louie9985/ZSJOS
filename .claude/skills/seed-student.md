---
description: "创建学员档案并绑定兼职端 H5 登录账号。当用户说"新增学员"/"建一个学员"/"生成学员和兼职账号"/"给XX编导加学员"时触发。"
---

# 新增学员并绑定兼职端 H5 账号

直接写库创建学员（person + 服务关系 + 兼职主体 + H5 登录账号）。用于短期内高频补录，**不经过 application 层**。

## 前置

1. 确认 `pymysql` 与 `bcrypt` 可用：
   ```bash
   python3 -c "import pymysql, bcrypt; print('ok')"
   ```
   不可用则 `pip3 install --break-system-packages pymysql bcrypt`。

2. 数据库密码从环境文件读取，**不要写进命令行历史或提交到仓库**：
   ```bash
   PW=$(grep -E '^ZSJOS_DB_APP_PASSWORD=' /opt/zsjos/.env.production | cut -d= -f2)
   ```

## 执行

```bash
python3 script/sql/mysql/tools/seed_student.py \
  --name   "学员姓名" \
  --mobile "11位手机号" \
  --director "编导username" \
  --operator "运营username" \
  --db-password "$PW"
```

可选参数：`--password`（默认 `Test@123456`）、`--dry-run`（只打印并回滚，**批量前先跑一次**）、`--tenant-id`（默认 1）。

**批量就是重复调用**，每行一次，每次独立事务。用户一次给多个学员时，逐个执行并在最后汇总。

## 先用 --dry-run 验证

新增前先 dry-run，确认三件事：
- `person_id` 是「新建」还是「复用既有档案」——复用说明该学员已存在
- `partner_id` 是「新建」还是「复用既有」——复用说明兼职主体已存在
- 没有报 `手机号已被占用` / `兼职主体已绑定其他学员`

## 脚本已处理的边界

| 情况 | 行为 |
|---|---|
| 编导/运营 username 不存在 | 报错退出，不写任何数据 |
| 手机号被**其他** person 占用 | 报错退出（同一个人自己占用不算冲突） |
| person 已存在（姓名+手机号 / 手机号 / 姓名） | 复用并升为 `student`，补手机号，不重复建人 |
| person 已有 active 服务关系 | **只补运营归属，不重复建订单链路** |
| 兼职主体已存在（按手机号匹配） | 复用，重置密码 |
| 兼职主体已绑其他学员 | 报错退出（一对一约束） |
| 重复执行同一学员 | 幂等，不产生重复行 |

## 生成口径（必须与 application 一致）

| 字段 | 格式 |
|---|---|
| `person_no` | `XY` + `yyyyMMddHHmmss` + 4 位当日日序（`zsjos_person_no_daily_counter`） |
| `order_no` | `OD` + `yyyyMMddHHmmss` + 4 位当日日序（`zsjos_order_no_daily_counter`） |
| `partner_no` | `PT` + `yyyyMMddHHmmss` + 4 位随机数 |
| 密码 | BCrypt **强度 4**（`$2a$04$`），不是默认的 10 |
| class_id | `1`（PENDING 待分班） |
| director_stage | `precheck` |
| owner_user_id | **NULL**（运营归属走 `operator_user_id`） |
| 课程快照 | 系统自身的「未明确课程」（`LeadProductSnapshot.unknown()`），880.00 |

主键一律交给 `AUTO_INCREMENT`，**不要硬编码 id**——库在实时写入，固定 id 会撞号。

## 完成后验证

```bash
docker exec -i zsjos-mysql-1 mysql --default-character-set=utf8mb4 \
  -uzsjos_app -p"$PW" -D zsjos -e "
select p.id,p.name,p.mobile,p.identity_status,sr.content_director_user_id dr,sr.operator_user_id op,
       l.partner_id,a.mobile acct
from zsjos_person p
join zsjos_service_relation sr on sr.person_id=p.id and sr.deleted=b'0'
left join zsjos_partner_student_link l on l.student_person_id=p.id and l.deleted=b'0'
left join zsjos_partner_account a on a.partner_id=l.partner_id and a.deleted=b'0'
where p.mobile='11位手机号';"
```

期望：`identity_status=student`、服务关系 `active`、`acct` 等于手机号。

**注意**：`--default-character-set=utf8mb4` 不能省，否则中文姓名显示为 `????`。

## 向用户汇报

按学员汇总：person_id（新建/复用）、服务关系 id、兼职主体 id、登录账号（手机号）+ 密码。明确说明「编导/运营可在学员列表看到」+「可用手机号+密码登录兼职端 H5」是否都已满足。

**复用既有档案时要单独指出**（例如 person 原先身份是 `lead`，或兼职主体是之前通过邀请码激活的），因为这会影响用户对数据来源的判断。

## 已知限制

- 脚本只管到「学员初始态」：无媒体账号、无定位卡、无交付计划。后续由编导/运营在系统里正常走流程。
- 不处理 `zsjos_user_relation`（编导↔运营关联）。若运营不在该编导的候选列表里，编导端「指派运营」选不到人，需要另行补 `content_director_operator` 关系。
- `person_no` 沿用既有编号格式不重排。复用迁移档案时会是 `LEGACY-P-*` 而不被改写。
