# H5 排行榜配置设计规格

日期：2026-09-14

## 目标

在 Vue Admin 提供 H5 排行榜的租户级配置，并让 H5 与统计后端使用同一配置来源。

## 配置内容

- 排行榜总开关 nabled
- 内部员工客资提交者纳入开关 includeEmployeeSubmitter
- 纳入统计的 System 角色编码列表 mployeeRoleCodes
- 四个榜单的启用状态：stimated_income、withdrawn_amount、lead_count、alid_lead_count
- 默认榜单 defaultType
- 默认展示周期 defaultPeriod，取 	oday/week/month/total

配置按租户隔离，首次读取无记录时返回安全默认值：排行榜开启、四榜单开启、默认榜单为 stimated_income、默认周期为 month、员工纳入关闭、角色列表为空。

## 后端

新增 ZSJOS 配置持久化表及 Admin 查询/保存接口，沿用 Controller -> Service -> DAL、租户、逻辑删除和审计字段规范。保存接口校验至少启用一个榜单，默认榜单必须属于启用榜单，周期必须为允许值。H5 /part-api/zsjos/partner/leaderboard/config 读取同一租户配置；默认榜单关闭时返回首个启用榜单作为有效默认值。

排行榜统计仅在员工纳入开关开启且提交人角色命中配置编码时计入员工提交数据；Partner 原有统计口径保持兼容。角色候选项由 System 角色 API 提供，ZSJOS 不复制角色表。

## Vue Admin

新增排行榜配置页面与路由/菜单权限，页面包含总开关、员工纳入开关、角色多选、四榜单开关、默认榜单、默认周期及保存反馈。页面不维护静态角色数组，加载失败、空角色列表和未授权状态分别展示。

## H5 兼容

保持现有配置响应字段兼容，新增员工纳入相关字段；H5 继续使用服务端返回的启用榜单、默认榜单和默认周期，不在前端生成权限或统计口径。

## 验证

- 后端配置保存校验、租户隔离、默认值和统计过滤单测。
- Admin 类型检查、lint、生产构建。
- H5 配置响应兼容测试，验证关闭榜单与默认回退。
- 检查直接受影响的架构/API 文档。

## 非目标

不改动排行榜展示样式，不新增榜单类型，不改变现有 Partner 身份授权，不引入新 npm/Maven 依赖。
