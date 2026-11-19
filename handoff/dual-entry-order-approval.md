# 双入口成交订单审批
环境 local；分支 codex/dual-entry-order-approval；工作树 D:\ZSJ-OS-dual-entry-order-approval；基点 21631a8a98。
Owner: Codex coordinator. Goal: single finance BPM engine with identical, directly actionable order detail from business and BPM entries within each frontend. Preserve order behavior, in-flight and legacy contracts. Integration target/order: None. No merge, push, shared publication or running-service modifications.
Ownership: backend agent owns backend and required BPM assets and order API docs; separate agents own React and Vue frontends respectively. Coordinator owns this record, isolated acceptance and any additional prerequisite scope registered here. Prior completed task files in D:\ZSJ-OS are read-only reference; do not import unrelated changes. Private build outputs, dependencies, DB/Redis and service ports only. Verify targeted backend tests, both frontend builds, authorized/denied tasks and cross-entry UI flows.
Scope addition for reproducible standalone DB: coordinator copies byte-identical previously delivered Core V371 through V377 and matching bootstrap/schema/verify/docs from main as read-only prerequisite sources. V372/V373 retained for continuous numbering, no new business migration introduced by dual entry. Separate private MySQL/Redis 13459/16459 populated exclusively from previous synthetic QA fixtures via single-transaction read-only dump; all writes target new containers.

## 验收结果
本地独立工作树完整构建，不覆盖 main 的 JAR、静态资源或依赖。Docker 私有实例不可用时，使用 MySQL 8.4.11 和 Redis 独立回环进程 13469/16469，后端 48259，工作台 25259，管理端 15259。仅使用合成测试数据。
- 工作台业务入口可原位通过、驳回、前后加签及评论。9/10 两笔跨入口任务各自只生效一次。业务权限专用财务账号可办理父任务；无财务身份的签人分别以仅 BPM 和仅业务审批权限办理本人子任务，未授权入口不可访问。
- 管理端订单 21 在两个入口展示一致，业务入口直接通过。订单 20 从 BPM 前加签、签人在业务入口通过、财务回 BPM 最终通过；签人办完后业务队列无其他任务。
- 订单 22 业务入口驳回后产生唯一补正任务，过期重放被拒。订单 23 在独立 S3 兼容测试存储上，两入口签名图片读取和放大预览通过。
- 后端 326 项回归通过；补齐非财务业务入口加签后另行执行 196 项定向回归（部分重叠）。工作台 61 项通过、类型检查与生产构建通过。管理端构建通过，全量类型检查仍有旧 MES 页未使用 handleRefresh。完整服务端 JAR SHA256 11F62F64AE68BBB104AC310ED7DC1C0EBB2BCD38052E43004F4FF0FB094E83E7。
- 398 步 fresh 链在独立 MySQL 执行；直接使用 Windows CRLF 文件会产生 7 项历史校验和提示。按仓库 LF 内容重新建库，398 步通过、数据级校验 0 项异常，完成重放及防重复初始化验证。
- 新分支基于已提交的 21631a8a98，带入先前成交审批、商品履约及数据库依赖。未将本分支合并或发布，未在共享库执行迁移或改变流程。当前变更在该分支工作树中，尚未创建提交。
