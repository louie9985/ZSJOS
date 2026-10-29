# 中世健历史报名收集表数据库兼容补录（2026-10-29）

## 授权与契约例外

用户明确要求：旧订单无法生成收集表不应阻止完成履约，不重启后端，只通过数据库修复。
范围是前次迁移 36 项中，缺少 product_id 和 product_ref 的固定 33 条任务，包含
OR202609180022；其他 3 条可正常生成的任务及此前关闭的 40 条不在范围内。

正常契约要求生成表单并由员工真实发送后确认。当前后端不存在历史豁免状态，完成门禁
只检查该任务存在表单且 sent_at 非空。用户本次明确指令覆盖这 33 项的正常发送事实要求，
使用带修复来源的数据库兼容补录；不将它扩展成新订单默认规则。
前端仍会显示“已发送”和本次修复时间，不能显示“历史豁免”；这不是实际发送事件。
未发送短信、微信、邮件或站内通知，未伪造学员填写或提交。

## 精确修改

执行器：`script/sql/mysql/repairs/backfill_legacy_information_20261029.py`。
仅 test、tenant_id=1、IDS 中 33 项；前置为有效订单、迁移后的 pending/class_per_item，
没有信息表、交付或分班等后续活动，原清单未勾选。前提变化时停止。

- 新增 33 条 DRAFT 表单，绑定原任务、订单商品、Person；不猜填 product_id、分类路径或 SKU。
- config_version_id 绑定当前 PUBLISHED 配置 3，冻结其 31 个 enabled 字段；通过部署包的
  StudentInfoFields.withAttachmentPolicy 应用当前附件限制。因没有权威分类映射，保留
  当前配置中的教育条件字段及其选填设置，不凭旧名称判断类别，不编造分类快照。
  这是本批兼容快照，正常新表单的产品分类裁剪仍保持原逻辑。
- product_name 来自订单历史快照；不修改商品、订单金额、历史快照或审批。
- 生成每份独立随机令牌，使用运行包 EncryptTypeHandler 兼容的 AES 加密保存，哈希唯一；
  令牌不写明文证据或日志。有效期沿用 30 天，表单可被现有 DRAFT 读取逻辑解析。
- sent_at 写本次修复执行时间，sent_by_user_id 保留 null。非空 sales_user_id 使用技术哨兵
  0，预检无真实账号 0；不冒填员工身份。当前对象授权不依赖该字段，生成入口之外也没有
  业务消费该字段；该哨兵仅用于本次兼容，不新增账号或授权。
- submitted_at、submit_source、答案、图片和草稿均不伪造；creator/updater 使用
  legacy-info-compat-20261029，create/update 时间记录本次操作。
- 任务仅增加 version 和修复审计，保持 pending；不勾选清单、不分班、不建立交付、不完成任务。

取消发送或重新生成会清除发送确认，这是现有业务行为；操作后需按正常界面重新确认，
不能再次运行修复脚本覆盖后续业务修改。链接 30 天过期不清除既有 sent_at，按当前后端
契约不会重新阻止履约完成。学生资料仍为“尚未提交”，不应当作已收集档案。

## 执行与验证

复用同目录 close_legacy_registration_20261028.py 的配置连接及受限无损证据序列化。
使用环境已安装 PyMySQL、PyYAML、cryptography、JDK，不修改 npm/Maven 依赖。
从正在运行的服务进程和部署包读取配置，在内存解析密钥；不将密钥、明文令牌或敏感载荷
输出到日志。离线 Java 进程仅加载现有部署 JAR 的库，不启动 Spring 或另一个后端服务。

```bash
python3 -B script/sql/mysql/repairs/backfill_legacy_information_20261029.py \
  --evidence-dir /opt/zsjos-runtime/backups/registration-information-20261029/preview
python3 -B script/sql/mysql/repairs/backfill_legacy_information_20261029.py \
  --evidence-dir /opt/zsjos-runtime/backups/registration-information-20261029/applied --apply
```

执行顺序：只读冻结前像和配置→部署包离线验证→会话临时 MySQL 副本预演→锁定订单、
任务、目标表单和配置并比对前像→保存 before/intent→插入表单→更新任务版本审计→
校验并保存 pending-after→事务提交→独立连接回读及 verified 记录。
连接显式 utf8mb4、SET NAMES utf8mb4，时间采用 +08:00；标题和字段快照逐项验证 HEX。

临时表继承真实约束，自增字段由 MySQL 分配，生成列不显式插入；复制列使用
information_schema.columns.generation_expression 区分计算列和默认时间列。
覆盖首次 33、重复零写入、第 1/17/33 条插入后、全部插入后、任务更新后的五阶段异常
整批回滚，恢复执行，以及后续取消发送时拒绝重放。

正式 DML 在同一事务；任何插入、核验或证据写入失败均不提交。无业务删除、DDL、
迁移账本写入、权限变更、应用配置修改或重启。任务和信息表全行比较保护无关记录与
其他租户；订单、审批、清单、路由、附件、分班、服务、任务和资料内容保持原值。

同一证据目录只有精确后像一致才零写入返回；有业务后续修改或部分结果就拒绝。
遇到提交中断先对照真实数据、intent 和 pending-after，不能通过是否有 verified 文件
推断提交结果。前像和加密字段证据存于受限目录，不能复制到普通日志或报告。
回退须另行授权，并确认没有后续填写、发送或交付；仅依据前像和新增表单身份恢复，
不得删除后续真实资料或覆盖新业务。脚本不自动删除或回退已提交记录。

## 验证结果与边界

部署包离线测试通过：实际 EncryptTypeHandler 解密全部新令牌并验证哈希，实际
StudentInfoFields 构建字段快照，StudentInfoFormService.state 返回 DRAFT，实际
RegistrationServiceImpl.applyCompletionState 从 information_not_sent 转为
checklist_incomplete，证明收集表门槛解除而清单门槛保留。
这不是已登录 API 或浏览器验收，不宣称真实发送、填写、完整履约已经发生。

初次临时表复制误把 DEFAULT_GENERATED 时间列排除，副本前像检查阻止了执行；修正
为仅排除 generation_expression 非空列后，全部预演通过，故障阶段没有真实业务写入。
2026-10-29 11:27:30（北京时间）正式提交：新增 33 条 DRAFT 表单并满足发送门禁，
33 条任务仅增加版本与修复审计；提交时间、学员答案和发送员工均未伪造。
独立连接完整后像、保护数据及 UTF-8/HEX 回读通过；同一目录实际重跑返回零写入。
OR202609180022 显示 pending/class_per_item、DRAFT、informationSent=true、未提交。
此前关闭的 40 条保持 ended，其他三条具有商品关联的任务保持原值。

后端 PID 1159859、进程启动计数 321631817 和部署包身份前后相同，运行配置未修改，
无后端重启。证据位于上述 preview/ 和 applied/；后者包含 before、intent、pending-after、
fixtures、verified。此前文档所述 33 条收集表生成缺口已由本次特例补录解除，商品关联
本身仍为空，未被伪造或补映射。真实分班、清单、凭证和完成服务须由业务继续办理。
