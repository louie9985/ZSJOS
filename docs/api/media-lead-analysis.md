# 新媒体客资目标与分析（代码已实现，待部署验收）

新媒体人数目标和分析接口由 ZSJOS 独立提供，不复用销售金额目标或销售业绩权限。V283 只定义表、菜单和按钮，不写 system_role_menu；管理员通过 System 角色管理分配权限。2026-09-28 复核共享开发库已存在原编号 原编号 V282 的三张表、八项菜单/按钮定义和两份版本记录，八项权限各有两条角色关联。新媒体组织和月目标记录仍为空；开发后端健康检查已恢复，但未提供认证会话，真实账号和真实数据验收未完成。

## 数据口径

- 客资按首次计数时冻结的 contributionUserIdSnapshot / contributionDeptIdSnapshot 归属；兼职、代录计入对应贡献员工。销售自拓录若在提交时明确关联新媒体提供方，该提供方会写入冻结贡献人并计入本大盘；未关联提供方的销售自拓录没有冻结新媒体贡献人，不计入本大盘。缺失快照不以当前归属或实际录入人回填。
- 有效客资为当前状态 valid / converted / won；无效为 invalid，其他状态列为“待判或其他”。“提交日期与判定情况”参照销售页的每日环形日历，环中央为提交数，环段按当前判定区分有效、无效和待判或其他。按月筛选提交日期（最多 93 天）；未来日期不统计，没有已确认的判定截止时间，因此不展示伪造的“逾期未判”。
- 月度漏斗使用同一提交日期范围的去重客资批次：提交客资、其中当前判有效客资、其中截至查询时已有生效首购的成交客资。三层依次收窄，不按订单数量计数；切换月份同时更新日历和漏斗，团队固定期间卡、成员进度和本月目标仍按北京时间的当前期间计算。
- 团队期间统计按提交批次；成交数为该批次有效客资截至查询时已有生效首购订单的去重客资数，成交率分母为有效客资。成员“昨日/今日/周/月成交”按首购订单生效日统计，归到冻结贡献员工，含往期提交客资。
- 昨日、今日、上周、本周、上上月、上月、本月、本年和全部按北京时间自然周期；周一为周起点，未来提交和生效时间不计入。
- 四个饼图按本月/上月有效客资的来源渠道标签快照、分类标签快照统计；缺失标签为“历史未记录”。

## 目标及组织

- 指标为每月非负整数，个人目标面向 System 岗位 new_media_operator 和 content_director 的启用员工。未设置与明确设置为零不同。
- 新媒体中心在 zsjos_media_lead_org 中显式选择；所属部门及多级下级部门来自 System 组织树，无须逐部门映射。中心不能凭名称猜测，也不依赖销售的 zsjos_performance_org。既有 DEPT 配置行不再作为组织范围来源。
- 保存另一个中心不会替换已有中心。误选时可在已配置中心行点击“取消中心设置”；服务将该行恢复为不参与中心范围的 DEPT 配置，保留客资和目标记录。若它同时位于另一个中心的 System 下级树中，仍作为普通部门显示；以后可再次选为中心。
- 部门未人工覆盖时汇总当前该部门符合岗位的个人目标；中心未人工覆盖时汇总直属员工及下级部门的生效目标。没有符合岗位员工且未人工覆盖的空部门不阻断中心汇总；有员工却缺目标时，自动值为未设置。人工修改记录原因、版本和修订历史；部门/中心可恢复自动汇总，个人必须显式设置。
- 历史客资按冻结部门归属；若管理员以后调整 System 部门树，中心历史汇总随当前树范围变化。已删除、且不再出现在 System 树中的历史部门无法按当前中心归属；不会按现任部门或录入人伪造回填。目标中的部门、中心字段保留写入时的组织快照。

## API 与权限

- GET /admin-api/zsjos/media-lead-analysis/tree、/overview：需 zsjos:media-lead-analysis:query，具体本人/部门/中心还分别需 self / department / center 按钮权限，并叠加 System 部门数据范围。overview 的 scopeType 为 SELF、USER、DEPT、CENTER；start/end 为月首至月末，用于 calendar 和 funnel（submitted、valid、converted），固定期间卡不受其影响。
- GET /admin-api/zsjos/media-lead-analysis/details：需 query 与 detail 权限，沿用相同范围授权，按 start/end 返回客资明细（最大 367 天），展示 leadNo、冻结贡献人、判定、来源/分类快照和首购生效时间；不返回内部 leadId。submittedAt 和 orderEffectiveAt 为毫秒时间戳，Workbench 按北京时间格式化，缺失首购时间显示“—”。
- GET /admin-api/zsjos/media-lead-target/list?periodStart=YYYY-MM-01、/orgs、/departments：需 zsjos:media-lead-target:query，结果限定在 System 数据范围。
- PUT /admin-api/zsjos/media-lead-target/batch：需 update 按钮；批次中每个对象在写入前单独校验岗位、组织和可写部门范围，并验证版本。restoreAutomatic 仅用于部门/中心。
- PUT /admin-api/zsjos/media-lead-target/org：仅接受 kind=CENTER 且 deptId=centerId，需 configure 按钮、可写部门范围和有效的 System 部门。GET /admin-api/zsjos/media-lead-target/{id}/revisions 另检查目标对象读取权限；Workbench 目标表提供只读修订记录弹窗，展示前后目标、原因、时间和内部操作人 ID。
- PUT /admin-api/zsjos/media-lead-target/org/unset：传 deptId 和当前 version，需 configure 按钮、该中心的对象可写权限和 System 部门可写范围；只撤销显式中心标记，版本不符或已非中心时拒绝，不删除客资或目标。撤销后重新选为中心走原 /org 接口。
- Workbench 菜单为 /zsjos/media-lead-analysis 和 /zsjos/media-lead-target；仅渲染服务端下发的菜单与按钮。大盘视角选择沿用销售业绩页的可搜索中心／部门／人员树：桌面侧栏可收起，手机在抽屉中选择；只展示 tree 接口返回的授权节点及父子关系，切换后按该节点的 scopeType/scopeId 查询。拥有 detail 权限时，大盘显示“按提交期间查看客资明细”入口，点击期间后打开对应提交日期范围的明细弹窗；没有 detail 权限时只显示汇总。登录失效、缺少明细权限、后端未部署 details 接口等失败会在弹窗内分别提示。

## 验证与待办

隔离 MySQL 8.4 夹具验证重编号后 V283 前置失败、DDL 后菜单/账本失败回滚、重复执行、部分恢复、管理员改名保留、中文 HEX 和无角色授权。后端新媒体目标及权限聚焦测试、Workbench typecheck/生产构建和真实 Chrome 合成响应的桌面/手机页面、修订记录及中心选择测试通过。共享开发库的 V282 元数据已只读核实；2026-09-28 开发后端 /actuator/health 为 UP，未认证的目标与分析请求返回业务码 401。尚需认证账号和真实配置来验收 API、部门/中心范围、租户数据及成交口径；合成夹具不能替代这些检查。

2026-09-29 月度图表补充验证：MediaLeadAnalysisServiceTest 11 项通过，覆盖所选提交日期范围漏斗和未来首购不计入明细；Workbench typecheck、生产构建通过。隔离真实 Chrome 在桌面和手机宽度验证了月份请求首末日、三层漏斗、每日环形图、按日下钻、空月份、旧后端缺漏斗字段时保留日历，以及原目标配置流程。未使用认证账号请求共享后端，也未部署前后端或修改共享数据。
