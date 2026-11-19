# 订单管理微信号快捷搜索

- Environment local（环境变量及环境标识文件未设置，采用默认值）
- Workstream local-order-wechat-search-20261112
- Goal 订单管理 keyword 支持订单保存的学员微信号模糊匹配
- Scope SalesOrderMapper 的 selectManagementPage/selectManagementCursor、两端订单管理页面、订单 API 文档、专用回归测试和本记录
- Non-goals 其他订单入口与审批搜索、数据库变更
- Branch main
- Worktree D:\ZSJ-OS
- Base 464c40f6d0eae8ed91cceddde915675ee8a38543
- Owner Codex
- Dependencies 既有 keyword 参数及 studentWechatId 字段
- Integration None
- Verification 后端 SQL 条件回归测试、两端静态检查、可用浏览器验证

## Delivery

- 修改 management-page/search-page 和 management-cursor/search-cursor 共用的两条 Mapper 查询，微信号使用订单 studentWechatId，与原有关键词字段同组 OR，继续与状态、提交人范围及高级条件取交集。
- 同步 React、Vue 订单管理搜索提示及 docs/api/zsjos-sales-order.md。
- Maven reactor focused test 通过，SalesOrderManagementKeywordTest 共 1 项，0 failures/errors。覆盖分页/游标 SQL 的四字段模糊匹配、空白修剪、空关键词、范围/状态/高级筛选条件、无范围和空匹配。
- Workbench npm run typecheck 通过。
- ego-browser 使用现有 localhost:5174/test/order-pagination.html 夹具，确认新提示可见，输入带空格微信号后回车，pageNo=1、keyword=wx_sample。该夹具使用模拟传输，仅验证前端请求构造。
- Vue 管理页面 SFC parse、compileScript、compileTemplate 通过。全量 vue-tsc 因默认约 4 GB Node heap 耗尽而退出，完整 Vue 类型检查未完成。Prettier 检查发现当前页面既有格式问题，HEAD 基线同样未通过。
- scoped git diff --check 通过。
- 未进行真实账户后端 HTTP 联调，运行中的后端尚未验证加载本次代码。上线生效需要部署相应前后端版本。
- 文件所有权已释放。
