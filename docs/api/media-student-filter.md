# 我的学员高级筛选

2026-09-28。员工工作台 `/zsjos/media-students` 使用独立场景 `media_student`、页面标识 `media_students`；不复用规划师 `student/student_my` 的字段目录。

## 接口与字段

保留 `GET /admin-api/zsjos/media-students/page` 普通分页；有高级条件时使用只读 `POST /admin-api/zsjos/media-students/search-page`，JSON 请求沿用 `MyStudentPageReqVO` 的 `pageNo`、`pageSize`、`keyword`、`inServicePeriod` 和 `advancedFilter`。响应仍为 `CommonResult<PageResult<MediaStudentListRespVO>>`，列表与总数都在过滤之后计算。POST 标注 `ZsjosAudit.Mode.READ_ONLY`。

| fieldKey | 名称 | 实际字段 | 选项来源 |
|---|---|---|---|
| mediaAccount.ownerOperatorUserId | 责任运营 | zsjos_media_account.owner_operator_user_id | 当前用户可读账号的真实责任运营 ID，经 System 用户 API 解析；保留仍有归属记录的停用人员 |
| mediaAccount.platform | 账号平台 | zsjos_media_account.platform_value | zsjos_account_platform 字典 |
| mediaAccount.currentStatus | 账号状态 | zsjos_media_account.current_status_value | zsjos_media_account_current_status 字典 |

三个字段支持统一选择类型的属于、不属于、为空、不为空。条件组结构、深度及数量限制沿用现有高级筛选契约。字典选择仅作瞬时查询，不写业务快照。人员选项为空时返回已解析空列表，不能退回全员接口或静态选项。

## 匹配和权限

完整 AND/OR 条件树作用于同一媒体账号行，再复用 `MediaAccountObjectPermissionProvider.filterReadable` 校验账号来源关系和当前读权限，按 Person 去重，最后与原学员查询范围、关键词、服务期、分页条件相交。普通、全量及指定人员读取分支都使用媒体学员场景；指定人员范围不扩大当前操作者的账号可见性。

例如学员的账号 A 由甲运营负责、平台为视频号，账号 B 由乙负责、平台为抖音：该学员不满足“甲且抖音”。不属于和为空仍要求存在一个可见账号；没有可见账号的学员仅在未应用高级条件时保留原列表行为。筛选不裁剪详情内其余获准账号。

目录、模板列表和分页均要求 `zsjos:media-student:query-my`；个人模板创建/修改按请求场景授权，删除仍检查模板所有者并校验媒体场景权限。模板仅保存条件，不授予任何对象权限。租户、逻辑删除、服务来源与账号权限沿用既有边界，无菜单赋权、数据库结构或依赖变更。

## 页面交互与验证

左栏由收起按钮、弹性搜索框、筛选按钮组成一行。页面四边沿用 workspace 及 CRM 间距；右边对齐按列表真实滚动条宽度补偿。摘要在下方换行，可逐项删除或清空，仅清除高级条件。筛选按钮显示生效数量；收起后仍可打开筛选抽屉。

应用通过页面离开保护后重载第一页，取消不改变查询。关键词和服务期与筛选共同生效，加载更多携带完整筛选；新条件使旧请求失效，避免迟到响应覆盖。重复打开可重新选用同名个人模板。字典/目录/模板失败均提供重试，空结果保持明确提示。

验证入口：`MediaStudentFilterQueryTest`（H2 MySQL 模式实际执行条件 SQL，并使用真实账号权限提供者）、高级筛选/模板/学员服务回归、两端共享契约测试，以及 `frontend/workbench/test/media-student-filter-browser.py`。浏览器夹具使用真实页面和隔离模拟接口，不写真实业务数据；H2 不替代目标 MySQL 或已部署运行时的登录验收。
