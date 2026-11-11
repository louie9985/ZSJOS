# 客资管理取消下次跟进入口

- Environment 为 local，ZSJOS_AGENT_ENV 和环境文件未设置。
- ID 为 local-cancel-follow-up-entry-20261111，Owner 为 Codex /root。
- Goal 为补齐客资管理主从详情的取消入口，复用既有接口与确认交互。
- Non-goals 为后端业务、权限配置、数据库、部署。
- Branch 为 main，Worktree 为 D:\ZSJ-OS，Base 为 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。
- Ownership 为 frontend/workbench/src/components/LeadNextFollowUpCancelButton.tsx、LeadDetail.tsx、LeadDetailModal.tsx、frontend/workbench/src/pages/LeadManagementPage.tsx、frontend/workbench/docs/follow-up-form.md、docs/api/zsjos-lead-submission-dispatch.md 及本记录。
- Dependencies 为既有取消接口和服务端返回的权限列表。上述目标文件无既有 Git 修改。
- Verification 为 Workbench typecheck、scoped diff、ego-browser 桌面及窄屏组件交互验证。夹具仅置于 frontend/workbench/tmp/cancel-follow-up-entry-20261111/，脚本位于 C:/Users/EDY/ego/cancel-follow-up-entry-20261111/。
- Target branch / Integration order 为 None。

## Delivery

- 原因确认为客资管理使用 LeadDetail，而先前入口仅位于 LeadDetailModal。主从详情现已透传服务端权限，在下次跟进时间旁接入取消按钮，两处复用 LeadNextFollowUpCancelButton。
- 沿用已成交、存在时间及双权限条件，主从只读模式隐藏；取消原因必填，失败保留输入，成功调用原刷新路径更新详情与列表。接口和表单文档已同步。
- npm run typecheck、npm run build、scoped git diff --check 通过。构建存在静态/动态混合导入及大分包提示。
- ego-browser 合成传输验证通过，覆盖返回零提交、原因必填、失败保留输入与重试、成功刷新、缺少取消权限、只读、非成交、无时间等分支；桌面 1440 和手机 390 宽度检查通过，手机无水平溢出。截图和脚本位于登记目录，浏览器空间已关闭。
- 初次夹具缺少 OverlayCoordinatorProvider 导致白屏，补齐项目既有 Provider 后通过；初次类型检查发现抽屉其他功能仍使用 hasPermission，恢复该 import 后检查通过。
- 真实账号权限、部署后接口与线上页面尚未验收。本次为本地源代码修复，交付所有权已释放。

## 用户指定位置调整

- 复用本登记并重新领取上述组件、直接相关文档及夹具的所有权。用户要求取消入口位于现有操作按钮栏。
- 计划通过 OverflowToolbar 的操作项打开共用确认弹窗；验证类型检查与桌面、手机交互及权限显示。

## 操作栏调整交付

- “取消跟进时间”已加入概览的 OverflowToolbar，排列在“录入复购”之后，沿用工具栏的宽度适配。主从详情通过该操作项打开共用取消弹窗，相关文档已同步位置。
- 类型检查和 scoped diff 检查通过。浏览器使用真实 LeadDetail 和合成传输数据，桌面 1440、手机 390 的操作栏及弹窗已检查；填写原因确认后提交一次，刷新一次，时间及入口随刷新消失。
- 原有 5174 服务中途停止，后续使用本任务独立 5197 验证进程。首次加载较慢、连续脚本中的等待存在超时，改为分步操作与快照后确认交互成功；完整连续脚本未通过，不作为本轮通过证据。
- 截图 toolbar-desktop.png、toolbar-mobile.png 及分步脚本位于已登记的浏览器脚本目录。真实账号及线上页面待部署后验收。本轮所有权释放。

## 已成交人工跟进时间必填

- 根据用户新规则，重新领取并扩展范围至 FollowUpFormFields.tsx、LeadFollowUpServiceImpl.java、LeadFollowUpCreateReqVO.java、LeadFollowUpServiceImplTest.java 及既有 API/表单文档。新增目标文件当前无既有修改。环境仍为 local。
- 人工新增跟进统一要求未来下次跟进时间。自拓自动首跟继续使用原有自动生成规则，已有历史记录沿用幂等返回。
- Admin 的 leadFollowUp API 仅查询历史，无人工新增消费端；Workbench 弹窗及内联表单共用字段组件。验证聚焦服务测试、类型检查和表单浏览器校验。

## 必填规则交付

- 已成交人工新增跟进统一要求未来下次跟进时间，前端移除选填提示，后端移除 won 状态的空时间豁免。请求 VO 注释、接口与表单文档同步更新。
- LeadFollowUpServiceImplTest 与 LeadNextFollowUpCancellationTest 共 25 项通过，包含已成交空时间拒绝、两种记录范围的未来时间保存、自动首跟兼容及取消功能。Workbench typecheck 和 scoped diff 检查通过。
- 浏览器完整详情和独立表单夹具均遇到页面加载超时，本轮浏览器校验未完成。真实账户、服务重启及线上部署验收仍待执行。
- 本次源代码及文档交付完成，所有权释放。

## 独立取消权限登记

- 用户批准将取消下次跟进作为客资管理下的独立按钮权限。环境 local，当前 main 工作树及原登记上下文继续有效。
- 扩展所有权为 LeadFollowUpController.java、LeadNextFollowUpCancelButton.tsx、对应新权限测试、V370__lead_next_follow_up_cancel_permission.sql、test_lead_next_follow_up_cancel_permission.py、迁移 README、data-and-permission-flow.md、既有 API/表单文档和本记录。
- 已只读核实 yudao-mysql 的 ruoyi-vue-pro，MySQL 8.4.11，双账本到 V369。客资管理 id=6770，通用取消待办 id=602402/parent=6780；新权限尚不存在。
- V357 已有历史版本记录和 Git 历史，本次按增量 V370 新增独立元数据。只增加客资管理子按钮及双账本记录，保留已有菜单与授权。角色授权由管理员管理。
- 验证计划为 MySQL 首次/重跑/部分恢复/前置失败/账本写入失败、中文 HEX 和本地同源执行对比，前后端权限允许/拒绝测试及类型检查。独立测试数据库保留。

## 独立权限交付

- 前后端统一使用 `zsjos:lead-follow-up:cancel-next-time`。服务层继续使用既有客资可写关系、租户及 won 状态检查。通用任务取消维持自身接口与菜单配置。
- V370 受控 MySQL 首次、重复、部分应用、前置失败、账本失败回滚恢复、菜单冲突及中文 HEX 检查通过；验证库前缀 task370_20261111165844819652 保留。
- 同源 SQL 已应用到本地 ruoyi-vue-pro，新增按钮 id=602425、parent=6770。名称 HEX 为 E58F96E6B688E4B88BE6ACA1E8B79FE8BF9B，双账本登记 V370；原通用菜单及 system_role_menu 行数/校验摘要在执行前后相同。
- 后端权限及取消服务 5 项测试、前端独立权限 3 项测试、Workbench typecheck 和 scoped diff 检查通过。浏览器合成权限验证新权限显示入口、缺少新权限隐藏入口。
- API、表单、权限架构与迁移说明已同步。新增前端测试数据最初使用字符串时间，与 Timestamp 类型不符，已修正为 epoch 毫秒并重新验证。
- 当前账号需管理员授予新权限，运行后端需重启加载新注解；本轮未执行账号授权或服务重启。当前运行接口尚未验收新权限，交付所有权释放。

## 2026-11-12 取消死锁修复登记

- 用户已要求修复已成交取消接口的死锁。环境 local，复用当前 main 工作树登记。
- MySQL 死锁记录与用户日志一致。旧取消 SQL 经 EXPLAIN 使用 PRIMARY 扫描约 22973 行；加入 biz_type=lead 后命中 idx_tenant_biz，约 5 行。当前所有跟进提醒的 biz_type 均为 lead。
- Ownership 扩展为 BusinessTaskMapper.java、BusinessTaskCommandService.java、LeadLifecycleTaskService.java、LeadLifecycleTaskServiceTest.java、新增 mapper 范围测试、script/sql/mysql/tools/test_follow_up_cancel_lock_scope.py、API 文档及本记录。前两文件已有 updatePendingById 并行工作成果，予以保留。
- 实现通过带业务类型的取消重载传递 lead；原调用接口继续兼容。验证聚焦 Java 测试及受控 MySQL 并发锁回归，应用库仅只读检查。服务重启和真实取消待用户授权。

## 2026-11-12 死锁修复交付

- LeadLifecycleTaskService 取消跟进提醒显式传递 lead 业务类型，经命令服务和 Mapper 的新重载加入 biz_type 条件。原无业务类型签名保持兼容，两个文件已有 updatePendingById 改动保留。
- MySQL 8.4 独立合成库 follow_up_lock_20261112002313170525 验证通过。旧 SQL 被无关行锁阻塞，新 SQL 命中 idx_tenant_biz 并可在同样条件下成功；同目标行锁仍阻塞，同编号其他业务/租户和 completed 任务均不受影响。测试库保留，应用库无业务数据变更。
- Maven 聚焦 BusinessTaskCancelScopeTest、LeadLifecycleTaskServiceTest、LeadNextFollowUpCancellationTest 构建和测试通过；scoped diff 检查通过。API 文档补充索引及锁范围说明。
- 修复需重启后端加载，真实账户取消接口尚未重测，本轮未执行服务重启。所有权释放。
