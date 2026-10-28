# 兼职客资明细

兼职管理与下属销售共用 LeadManagementPage 的表格列、分页、列设置和详情抽屉。partnerId 模式使用既有兼职列表和详情接口，保留 manager-readonly 详情。顶部快捷筛选复用已发布的 management 配置，通过兼职权限保护的 /zsjos/partner/leads/filter-profile 读取；列表把 inboxGroup、inboxStage、inboxQuick 交给共用解析器，与 partnerId 条件取交集。支持关键词搜索，不提供高级筛选、列排序和批量命令；切换兼职通过组件 key 重置列表及详情状态。概览仍独立查询累计条数。
