# 2026-10-14 审查修复

本轮修复基于 e07efc9d6b，范围为已确认的 12 个审查问题。租户只有 1。

## 行为与上线
- 审核附件先读元数据，用户预览/下载时再读正文；正文接口仍逐次校验本轮批次和资源权限，返回 no-store/nosniff。实际内容探测决定响应 MIME，非预览白名单统一 octet-stream；工作台仅按正文响应类型启用预览，PDF iframe 禁止脚本。旧版本正文端点返回 octet-stream 时仍能下载，但不会在线预览，需协调更新后端/工作台。
- BPM 在审批过程中补选 START_USER_SELECT 人员时复用角色范围校验；未配置范围的旧流程不变，不撤销既有冻结人员。
- 审核定位历史按授权账号的服务关系与学员获取；账号应用定位与服务主定位分开。审批历史快照不重建。
- Infra 新增批量元数据读取，缺失 ID 不返回；单文件 API 异常契约不变。课程列表批量读取并保留失效附件占位。积分附件初次复用详情，刷新合并并发请求。
- 个人日程省略颜色不更新该字段，空字符串明确清除；色板修改使旧读取响应失效。
- Vue Admin 课程通知通过服务端菜单 componentName ZsjosCourseCalendar 路由进入，读取 courseId 对应的权威详情，不新增菜单或角色授权。

## 固定服务关系修复脚本
`script/sql/mysql/tools/repair_relation_1016.sql` 是单次受控工具，不属于编号迁移链，本轮不再次应用真实目标。

前提：相关表为 InnoDB，已有 zsjos_data_repair_backup.old_value LONGTEXT。范围固定 tenant=1 / relation=1016，以及脚本中的原订单、学员、配置和人员标识；不得按姓名扩大。执行前需授权目标并备份关系、班级、分配日志、任务、协作群与证据表。

首次执行需在同一 UTF-8 MySQL 会话设置 `@repair_1016_expected_version` 和 `@repair_1016_expected_sha`。摘要按 information_schema.columns 的 ordinal_position 构造完整 JSON_OBJECT（bit 转 UNSIGNED），再计算 SHA2(CAST(JSON AS CHAR),256)，由操作者审阅并冻结，不得在正式执行脚本中自动接受当前值。审批前态必须仍为未接收、未分配 owner/编导/运营/协作群，且没有既有修复副作用。已在其他正式班或其他状态的记录拒绝，不自动覆盖。

存储过程锁定关系后校验完整前态、版本、引用和配置，再依次创建班级、变更关系、写日志、建首联任务和协作群。首联期限读取实际配置。任何 SQL 异常或后置检查失败回滚全部业务 DML；即便客户端继续执行，CALL 后也没有业务写入。DDL 仅为存储过程和临时表，不宣称可由事务回滚。

成功后保留 repair_key=relation-1016-v2 的 1 个完整前镜像及 6 个完整后镜像，后者固定本次新增行 ID。重复执行仅在当前完整行集合与后镜像一致时零写入通过；缺失证据、部分执行或后续修改均拒绝。原脚本已执行但没有这些证据的目标不得视为新脚本已验证成功，需另行核对，不回填虚构前态。

恢复必须另行授权：先锁住相同关系与证据中列明的行，逐行确认当前值等于后镜像；若任何字段改变则停止。仅删除证据中明确记录且不存在前镜像的新增日志/任务，恢复关系完整前镜像（含 director_stage、版本、审计字段和原班级），解除引用后仅删除本次新增协作群/班级；有其他关系引用时停止。保持事务和外键约束，保留恢复审计及外部备份。没有硬编码 class_id/version/空人员的自动回滚脚本。

## 验证入口
- Maven 聚焦范围：BpmStarterSelectionBoundaryTest、BpmStarterSelectionPolicyTest、ContentReviewStudentFilesTest、ContentReviewPositioningProjectionTest、CourseAttachmentServiceTest、CalendarCreatorNamesTest、PersonalCalendarEventServiceTest、PersonalCalendarColorMapperTest、FileServiceImplTest。
- `python -B script/sql/mysql/tools/test_review_relation_repair.py` 仅创建保留的 review1016_* 合成测试库，覆盖首次/重放/前态缺失/版本错误/依赖缺失/写入失败/后来修改拒绝，不连接真实业务库。
- 部署顺序测试使用 UTF-8 临时文件；可通过 ZSJOS_TEST_BASH 指定本机 Git Bash，所有部署动作均为桩。
- Workbench `test/review-fixes-browser.py` 与 Admin `test/review-course-link-browser.py` 使用隔离传输，分别连接 5274/5275 测试端口；合成浏览器验证不代表真实登录、存储、部署验收。

完整执行结果以 handoff/local-review-fixes-20261014.md 为准。
