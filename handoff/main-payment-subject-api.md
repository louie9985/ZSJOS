# Workstream: main-payment-subject-api

- Goal: 修复支付主体管理与产品支付主体配置的缺失查询接口、字段和状态映射、保存请求。
- Non-goals: 不改菜单迁移、账号授权、支付交易流程、数据库结构、分支或提交。
- Branch / target branch: main / main
- Worktree: D:\ZSJ-OS
- Base commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- Owner: Codex /root
- Ownership scope: backend/yudao-module-zsjos 下支付主体相关 controller/admin/payment、service/payment、dal/mysql/payment、dal/dataobject/payment/ProductPaymentSubjectDO.java 及对应 payment 测试；frontend/admin/src/api/zsjos/payment、src/views/zsjos/payment 及定向测试；docs/api/payment-subject.md；本文件。
- Dependencies: 现有 System 权限、MyBatis 分页与租户机制、ZSJOS 产品表、Vue Admin 与 Workbench admin_embed；无新增依赖。保留工作树现有修改。
- Integration order: 查询契约与后端实现 → 前端调用及表单对齐 → 定向测试与文档 → 验证。
- Verification plan: 后端定向测试和编译；Admin 类型检查、定向 lint、生产构建；Workbench 嵌入相关测试；可用环境真实请求及桌面/移动浏览器检查。
- Confirmed design: /payment-subject/page 返回分页；/simple-list 返回无密钥的选择项；产品配置 /page 以当前租户产品为基表包含未配置产品，并在分页前按主体筛选；保存使用既有 configure/batch-configure 和 paymentSubjectId；状态 0 启用、1 停用，管理表单匹配后端 subjectCode/cusid/appid 字段。菜单、权限标识与交易语义保持既有契约。
- Status: active

## Scope update — V240 tenant correction

- Authorization: 用户明确要求将初始化脚本改为租户 1；数据库内容已由用户手动修改。
- Ownership scope extension: script/sql/mysql/migrations/V240__init_payment_subjects.sql。
- Goal: 两条支付主体初始化记录及重复检查统一限定 tenant_id = 1。
- Non-goals: 不执行数据库写入，不修改关联表结构、建表默认值或其他工作区改动。
- Verification plan: 检查 SQL 插入列值对应关系、两个租户值与重复检查条件，确认其他内容保持不变；本轮不重放业务初始化数据。

## Delivery — 2026-09-15 16:43:52 Beijing time

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 初始化脚本改为租户 1；数据库由用户手动修改。
- Key decisions: 仅修正 V240 两条插入值和租户内重复检查；补充 utf8mb4 连接声明与执行范围、重复性、回滚限制。
- Result: 两条初始化记录使用 tenant_id = 1，其他租户同编码记录不再阻止插入；没有数据库写入。
- Changed files: script/sql/mysql/migrations/V240__init_payment_subjects.sql；handoff/main-payment-subject-api.md。
- Verification evidence: 静态断言验证两个插入租户值、两个重复检查条件；逆向替换确认其余 SQL 内容保持不变。未执行数据库重放或 HEX 校验，本轮无数据库文本变更。
- Dependency / integration impact: None。
- Remaining work: 产品支付关联表缺少 payment_subject_id 的问题不在本次授权范围；SQL 受控重放及用户手动修改后的数据库一致性未验证。

## Scope update — V241 schema repair

- Authorization: 用户要求快速修复加载失败。
- Scope extension: V241 SQL、docs/api/payment-subject.md、本记录；本地 ruoyi-vue-pro 的空 zsjos_product_payment_subject 表。
- Facts: 关联表 0 行，旧字段 subject_code，代码需要 payment_subject_id；主体已归租户 1。
- Plan: 备份结构，修正空旧表，拒绝非空旧表转换；验证新建、旧表、重复执行与查询。
- Non-goals: 不修改主体数据、权限、Java、前端、分支或服务。

## Delivery — 2026-09-15 16:47:19 Beijing time

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 快速修复产品支付配置加载失败。
- Key decisions: 修正开发中的 V241 原脚本；仅空旧表将 subject_code 转为 payment_subject_id，非空旧表由 CHECK 拒绝。保留主体数据及权限。
- Result: 已执行修正到本地 yudao-mysql / ruoyi-vue-pro；结构备份 C:\Users\EDY\AppData\Local\Temp\zsjos-payment-subject-before-v241-20260915.sql。
- Changed files: script/sql/mysql/migrations/V241__product_payment_subject_association.sql；docs/api/payment-subject.md；handoff/main-payment-subject-api.md。
- Verification: 专用临时命名测试表验证新建、空旧表、重复执行和非空拒绝后清理；开发库与新建表字段结构一致。SQL 查询 31 个产品；中文列注释 HEX 为 E694AFE4BB98E4B8BBE4BD934944。实际 ADMIN HTTP 请求产品配置 code=0,total=31,list=10；主体分页 code=0,total=2,list=2。
- Dependency / integration impact: 无需重启或 Java/前端变更；DDL 已隐式提交。
- Remaining work: 未运行全量生产 bootstrap，未执行浏览器视觉验证；本次实际 HTTP 查询验证已通过。
