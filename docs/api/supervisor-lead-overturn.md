# 主管直接改判有效

2026-10-08，员工工作台新增独立主管命令，现有三级申诉继续保留。

## 操作与范围

进入“客资管理”或“下属销售 → 名下客资”，打开客资详情，选择“改判有效”，填写理由后确认。
理由去除首尾空白后必填，最多 1000 字；可附最多 9 张 JPG/PNG/WebP 图片，沿用客资图片大小、内容类型及文件所有者校验。
上传失败保留图片与输入，不发送改判命令；请求失败可重试，未修改内容时沿用幂等键。

操作同时要求 `zsjos:subordinate-sales:lead-overturn-valid` 和当前负责人属于主管负责部门及子部门的人员范围。
查询全部、全租户读取、角色名称、申诉审批权限均不能替代此对象范围与按钮权限。
只允许 `invalid + owned` 且未关闭的客资；无申诉历史，或仅一条第一轮销售主管维持无效记录。
任何进行中申诉、二三轮历史、已改判/撤回等其他结果均不能直接改判。已存在商机须为未赢单的 `initial_conversion + lost`。

## API

- `POST /admin-api/zsjos/subordinate-sales/leads/{leadId}/overturn-valid`
- Body：`reason`、`qualificationToken`、`idempotencyKey`、`attachments: [{ infraFileId }]`。
- `qualificationToken` 来自该客资 `availableActions` 中的 `SUPERVISOR_OVERTURN_VALID` 动作。它绑定当前客资版本、负责人、判定轮次、时间、最近无效事件及无效内容；客户端不自行构造。
- 返回 `CommonResult<Boolean>`。不存在、无权限、状态不符、申诉限制、商机异常、过期判定和幂等冲突分别返回稳定错误。
- `POST /admin-api/zsjos/subordinate-sales/overturn-attachment/upload`：multipart `file`，返回现有客资上传 VO，使用同一新按钮权限。

服务端动作策略和命令复核保持一致。命令使用租户隔离、Service 对象权限、READ_COMMITTED 事务及 Lead 行锁。
申诉提交先锁同一 Lead，因此提交顺序决定合法结果；直接改判不锁定或操作 BPM 任务。
幂等事件绑定操作人、客资、判定 token、理由及附件；成功重放不重复商机、返现或通知，同键不同内容返回冲突。

## 状态、审计与通知

改判保持原负责人和客资分类标签快照，将 Lead 变为 `valid + owned`，创建或恢复唯一首次转化商机为 `open`。
理由成为有效备注，更新判定人及时间；显式清空当前无效原因、说明、证据、挂起及旧提醒投影。
不创建首跟、判定或跟进提醒；不更改订单，不代表成交。后续销售跟进通过正常商机入口安排。
调用现有有效客资返现幂等检查，不复制财务逻辑。

`lead_supervisor_overturned` 业务事件保存真实操作人、时间、理由、图片引用、原无效原因与标签快照、说明、证据、原判定人和时间。
流转记录显示“主管直接改判有效”和“无效 → 有效”。不创建伪申诉、不消耗申诉轮次，已有申诉历史不变。
`zsjos.lead.supervisor_overturned` 通过 System 通知设施通知提交人与负责人，默认站内信模板使用 `lead.no`，区分主管直接改判与申诉裁决。

Vue Admin 和 Partner H5 不新增直接改判入口。Admin 继续读取原详情、使用原申诉接口；新增动作字段为兼容扩展。

## SQL、发布与恢复

`V289__supervisor_lead_overturn.sql` 在 V288 后执行，新增一个按钮、一个通知模板，以及各现有未删除租户缺少的该场景通知规则。
不写 `system_role_menu`、不修改租户套餐或业务记录。既有管理员名称、排序、禁用状态和规则保留；身份冲突报错，不覆盖或恢复软删除配置。
元数据、后置校验和双版本登记位于同一失败控制事务；已有版本标记不能跳过缺失元数据修复，既有校验和不被修改。

发布顺序：V288 → V289 → 后端 → Workbench → 管理员在角色管理授予新按钮权限。
回退时先在 System 管理中禁用入口和规则，再回退应用；保留历史事件、申诉、消息及元数据，不反向修改已改判客资。

本地开发库的本次同步只执行同源元数据块，未执行其他任务的 V288，也未伪造 V289 版本登记。
后续正常迁移执行时以原迁移补登记，并保持元数据重复执行安全。同步前状态摘要及 SQL 保存在本机临时备份目录。

## 验证入口

- `python -B script/sql/mysql/tools/test_supervisor_lead_overturn.py`：独立、保留的 MySQL 测试库验证初次/重复/部分执行、缺前置、菜单冲突、登记失败及恢复、中文 HEX 和授权保留。
- 加 `--apply-dev` 仅同步本地 `ruoyi-vue-pro` 的本功能元数据，并与原迁移结果比较；不会推进版本账本。
- `python -B script/sql/mysql/tools/test_supervisor_overturn_transactions.py`：现有本地 MySQL 中独立测试库运行真实服务/MyBatis/事务/租户拦截测试；不删除测试库或业务数据。
- Workbench：`npm run typecheck`；Vite 专用测试端口 5201 下执行 `python -X utf8 frontend/workbench/test/supervisor-lead-overturn-browser.py`。

浏览器用例使用真实组件和合成传输响应；事务测试中的 System、Infra、返现和通知协作者为测试替身。
实际登录账号、BPM 部署、真实通知接收及线上业务数据仍需发布后验收；本次不包含服务重启、发布或账号授权。
