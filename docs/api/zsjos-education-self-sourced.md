# 教务自拓与直接成交

## 业务边界

教务自拓提交 → 当前登录员工直接成为负责人 → 本人跟进和判定 → 本人提交成交 → 原有报名/财务审批 → 成交归属该员工（教务）。不要求销售岗位，不经过销售接单或转交销售。

新增权限 `zsjos:lead:education-self-sourced:create` 由管理员通过角色管理配置，通常分配给学习规划师。权限本身不绑定角色名，不自动授予跟进、判定、成交、全量数据或审批权限。原销售自拓岗位检查、自动派单候选、接单、抢单、公海协作获客规则保持不变。

## API 与菜单

- `POST /admin-api/zsjos/lead/education-self-sourced/create`：请求/响应复用 LeadCreateReqVO/LeadCreateRespVO。服务端强制 `dispatchMode=self` 和归属当前登录员工，忽略客户端指定的其他负责人。账号、部门和人员状态必须有效。
- 可选 `newMediaProviderUserId` 使用原有新媒体候选 API 和资格检查；未选择不计入新媒体贡献。
- 产品目录、附件上传、新媒体候选查询允许教务自拓权限。
- Workbench 公开地址 `/zsjos/leads/education-self-sourced`，服务端根菜单的子路径为 `leads/education-self-sourced`。
- React 使用原 LeadSubmissionPage；Vue 使用原 LeadCreateDialog 和 WorkbenchListPage。菜单、路由和操作权限仍由服务器配置。
- 同一个幂等键不能跨教务和其他来源重放；查重复核保存并还原原始教务来源与自拓归属。

## 身份与历史

| 数据 | 字段 | 规则 |
| --- | --- | --- |
| 客资提交来源 | `zsjos_lead.source_type` | 新值 `education_self_sourced`；显示教务自拓录；不随转移改写 |
| 当前负责人 | `zsjos_lead.owner_identity` | 教务自拓为 education；现有销售接单/抢单/转移为 sales |
| 归属历史 | `zsjos_lead_assignment_history.owner_identity_snapshot` | 记录本次归属身份 |
| 成交归属 | `zsjos_order.formal_owner_identity` | 首购从负责人复制；订单补正继承原订单，客资复购传入其负责人身份 |
| 跟进历史 | Lead/Opportunity 跟进表 `owner_identity_snapshot` | 保存跟进时的负责人身份 |
| 投诉历史 | `zsjos_lead_complaint.owner_identity_snapshot` | 保存被投诉负责人身份 |

现有 `ownerUserId` 和 `formalSalesUserId` 保留为技术关联；不得因为字段名含 Sales 就限制教务成交。`submitterCenterType` 继续表示销售转化/学员服务业务入口，不作为人员角色分类。

身份是由本次业务入口/归属命令确定的技术事实，不是用户可编辑的业务字典。不得按当前角色、岗位名称、部门名称回推历史身份。迁移不回填旧记录；空值显示未记录/中性负责人称谓。无客资来源的既有复购入口不会凭当前角色推断身份。

审批轮次 order/leadProfile 快照包含新身份；BPM 实例可读取 `formalOwnerIdentity` 与 `formalOwnerIdentityLabel`。历史已启动流程及已发送消息不重写。主管确认继续根据成交负责人所在部门及既有权限配置解析；老化协作仍只寻找原规则允许的销售，教务部门没有符合条件的销售时维持空候选。

## 展示、筛选、导出、通知

- 客资负责人、订单成交归属、跟进及投诉提供独立身份字段，混合列表使用中性负责人称谓。
- 高级筛选目录增加 `lead.sourceType`、`lead.ownerIdentity`、`order.formalOwnerIdentity`；仍叠加原租户/对象/数据范围。
- 财务订单导出增加成交归属身份列；不通过人员当前角色推断。
- 管理端订单列表、订单详情、成交审批和主管确认详情展示订单成交身份快照；历史空值显示“未记录”。回收或释放到抢单池时，负责人及当前身份在同次数据库更新中清空，历史快照保留。
- Lead 通知变量新增 `lead.submitterIdentityLabel`、`owner.identityLabel`；订单通知变量新增 `order.ownerIdentityLabel`。
- V257 只替换正文和摘要均与 V080 原文完全一致的来源关联默认文案，允许维护标识已变化；自定义正文/摘要、模板名称/状态与租户规则保留。管理员应按需使用新身份变量更新自定义文案。

## 数据库部署与授权

V257 位于 `script/sql/mysql/migrations/V257__education_self_sourced.sql`；依赖现有 V256 前置结构及工作台根菜单。新环境沿用 baseline → 编号迁移顺序，已有环境执行此增量迁移。

仅新增六个可空字段、一个菜单，并更新未经编辑的 V080 默认来源关联模板。无业务行删除，无历史身份回填，无角色授权写入；按字段与权限存在性守卫，可重复执行。回滚应用时保留字段和身份快照，通过管理员停用菜单，不提供删除数据的回滚脚本。

配置教务办理人员时复用本人客资查询、详情历史读取、跟进、判定、订单创建/补正/本人订单查询以及实际业务所需的付款和附件权限；不直接复制销售角色，不授予接单/抢单/公海获客权限。审批人的权限由现有审批配置独立管理。

验证入口：`python script/sql/mysql/tools/test_education_self_sourced.py`。该脚本在现有本地 MySQL 容器创建并保留独立测试库，仅构造合成记录；验证迁移重复执行、原始身份空值、角色授权不变、菜单相对路径、版本、中文 HEX 和自定义模板保护。

运行验收需同时加载新后端代码和前端资源，并使用已获授权的学习规划师账号覆盖直接提交/查重复核/跟进/判定/成交/审批，以及未授权、他人客资与租户隔离情形。

部署时应协调后端升级与默认通知模板同步：新变量需要新版后端支持，不应在旧后端仍运行时提前启用新模板。2026-09-17 本地交付已应用字段及菜单，按用户决定暂不重启，最终默认通知模板同步暂缓；切换新版后端时需重放最终 V257 并验证通知。
