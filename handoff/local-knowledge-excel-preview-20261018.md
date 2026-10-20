# 知识库 Excel 只读预览

- ID: local-knowledge-excel-preview-20261018
- Environment: local（环境变量及环境文件未提供，按规则回退）
- Goal: 按用户确认方案实现 XLS/XLSX 内部、附件、回收站、新旧公开分享只读预览。
- Non-goals: 在线编辑、图表和打印还原、SQL/授权变更、部署、提交、外部 Office 服务。
- Branch/worktree/base: main / D:\ZSJ-OS / d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2
- Owner: 当前 Codex 主代理，单写者。
- Ownership: 本记录；PMS 知识库 Excel 服务、VO、控制器、公开 HTML、错误码、附件校验和对应测试；Infra FileApi/FileService/FileMapper 登记地址解析和对应测试；Admin 知识库 Excel 组件、API、文档/附件/回收站/分享入口和专项测试；docs/api/pms-knowledge-excel-preview.md、pms-knowledge-attachments.md、pms-knowledge-public-share.md。
- Dependencies: 既有 PMS 授权、Infra 文件、POI 和 Element Plus；保留其他并发修改。
- Verification: Java 解析/授权/文件匹配/HTML 专项测试、编译、Admin 类型/构建、桌面手机浏览器；真实存储和部署单独报告。必要时临时源码快照隔离构建输出，不创建 Git worktree。
- Target branch / integration order: None / None。
- Defaults: 10 MB，500 行/100 列，公式缓存不重算，无解析缓存，限并发和解压；仅租户 1。

## Delivery — 2026-10-18

- 实现：PMS 受权 Excel 内容预览，Infra 精确登记 URL 解析后按原存储配置读取；普通/回收站/匿名 JSON 和公开 HTML 入口；Admin 独立文件、附件、新旧分享接入。仅租户 1，保留原下载及公开附件规则。无新增依赖、SQL、权限分配、分支、提交或部署。
- 限制：10 MiB、500 行/100 列、200 万字符；只展示可见表；公式读取缓存；解压预检和每实例 2 并发；空表/损坏/加密/未登记/权限/繁忙等明确状态；不缓存解析结果，响应 no-store。
- 首次共享目录 Maven 在既有 Infra 测试编译出现 Windows 系统资源不足；使用临时源码快照和限定 JVM 资源后重验，不改业务模块测试。快照保留完整生产源、只保留目标测试及资源，无 Git worktree。
- 最终后端：21 tests / 0 failures / 0 errors：Infra URL 1；Excel parser 4；Excel service 5；Excel public adapters 2；原附件服务 4；原公开 HTML Controller 5。PMS/Infra 及 reactor 前置模块编译通过。日志：C:/Users/EDY/AppData/Local/Temp/knowledge-excel-isolated.log。
- Admin：test/knowledge-excel.tsconfig.json 定向类型检查退出 0；Vite env.local 生产构建通过，产物位于临时目录 knowledge-excel-admin-dist。全量 ts:check 被既有 src/api/zsjos/withdrawal/index.ts 的 12 条重复字段诊断阻断，本任务文件没有类型诊断；未修复无关业务。
- 浏览器：test/knowledge-excel-browser.py 在 1440/390 宽度通过内容/XSS 文本、工作表/空表/截断、普通/回收站/旧分享请求、拒绝与重试、旧请求覆盖保护、Excel 附件和 PDF/图片/下载回归，无页面异常和整页横向溢出。最新截图目录 C:/Users/EDY/AppData/Local/Temp/knowledge-excel-browser-td5eflyz。公开 HTML 来自 MockMvc 导出，桌面/手机内容、导航、安全文本及页面宽度检查通过；截图 knowledge-excel-public-1440.png / knowledge-excel-public-390.png。图像工具未可靠显示图片，未将截图自动断言等同人工视觉审阅。
- 文档：新增 docs/api/pms-knowledge-excel-preview.md，更新附件/公开分享文档。保留原公开文件下载描述，其新 Excel 预览是独立入口。
- 未验证：真实登录会话、真实存储文件及部署后目标环境验收，当前无授权会话且未部署/重启；MockMvc/浏览器合成请求不等于线上验收。
- 资源：本任务测试页面使用专用 5329 端口，交付时关闭；保留临时日志、截图、构建快照供复核。所有权在交付后释放。

## Registration update — merged cells

- Goal update: 修复用户反馈的合并单元格被拆分；同一工作流重新取得本次目标文件所有权。
- Scope: Excel parser/VO、公开 HTML、Admin Excel API/组件、对应 Java/浏览器测试及 Excel API 文档、本记录。
- Behavior: 返回零基合并区域、两端使用 rowspan/colspan；保留左上角文本、边界裁切、稀疏空行补齐；权限/存储/预览限制不变。
- Verification: XLS/XLSX 横向/纵向/矩形/边界合并；公开 HTML 及 Vue 合并渲染；定向类型和浏览器桌面/手机。复用当前 local/main 上下文，保留前轮改动及其他工作流。
