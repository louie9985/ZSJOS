# 公告来源、发布人与接收范围

## Registration — 2026-10-08（北京时间）
- ID: local-notice-origin-20261008; environment: local（按环境变量、环境文件检查后的默认值）; owner: this chat /root.
- Branch: main; worktree: D:/ZSJ-OS; base/HEAD: d2f836006ff07b5517bcd6d485d72df788bf3cb0; target branch/integration: None.
- Goal: 来源部门可选且默认当前人员所属部门，实际发布人由服务端记录，展示接收部门或全体员工，公告搜索支持来源。
- Non-goals: 改变公告可见性/角色授权、公开分享契约、补造历史来源、依赖、部署、服务重启或 Git 操作。
- Ownership: System notice DO/VO/Controller/Service/Mapper及公告测试、System test create_tables.sql；Workbench noticeManagement.ts、api.ts 中公告类型、NoticeEditorDialog/NoticeManagementDetail/HomeAnnouncementPanel、公告中心及管理页面、公告 CSS；Admin notice API、公告编辑/详情/管理页面；V291 公告元数据迁移、两个 core schema 镜像和专项 SQL 验证；新增公告元数据浏览器 fixtures；docs/api/notice-origin.md；本记录。已有公告分享工作流已交付释放，保留其修改。其他活跃工作流的公共文件只修改上述片段，写前重读。
- Dependencies: 现有 System 部门/用户服务、公告生命周期和接收快照、现有 React/Vue UI 与测试工具；无新增依赖。
- Verification: 发布人/来源/接收范围快照和搜索的后端测试，两端静态检查，桌面与移动浏览器；MySQL 初次/重复/部分应用/失败恢复、双版本账本、中文 HEX，开发库非破坏性同步；baseline 修改触发受控 fresh chain；局部 diff。
- Decision: 用户已确认“选择来源部门，默认发布人所属部门”。规则冲突：旧 ownership 文档要求所有行为修改前确认，根 AGENTS 第 2 节允许明确请求的局部可逆修改；依优先级遵循根规则执行本请求。

## Coordination — 2026-10-08（北京时间）
- 表格工作流请求公告正文显式传入 announcementTables；已在本工作流拥有的 NoticeManagementDetail.tsx 与 AnnouncementCenterPage.tsx 完成入口修改。SafeRichText prop 实现由“修复公告表格间距”聊天拥有，待其实现后验证。
- announcements.css 的来源元信息样式已写入；即刻暂停该文件写入，将后续表格规则修改留给表格工作流。保留 notice-origin-meta 样式。此依赖完成后再运行最终浏览器与静态检查。

## Delivery — 2026-10-08 12:39（北京时间）
- Context: local/main/D:/ZSJ-OS，HEAD 仍为 d2f836006ff07b5517bcd6d485d72df788bf3cb0；沿用登记所有者与目标，integration: None。
- Result: 来源部门从 System 启用部门选择，默认当前人员所属部门；新客户端必选来源。后端保存草稿来源/范围，发布时校验并冻结来源名称、实际登录发布人姓名与接收范围。复制保留选择但不继承发布人；历史缺失值不回填、不按当前组织推断。
- UI: Workbench 首页公告卡、公告中心列表/表格/详情、两端管理列表/详情和编辑预览展示来源、发布人、接收部门/人员；ALL 显示全体员工。两端关键词搜索扩展到标题、来源部门、发布人；分页和游标查询保持原租户/接收范围过滤。部门选项支持加载、空态、错误重试。
- Files: 登记中的 System notice DO/VO/Controller/Service/Mapper、两个 Service 测试和 NoticeMapperTest、H2 create_tables.sql；两端公告 API/编辑/列表/详情，首页组件与 notice-origin-meta CSS；新增 V291、同步两个 core schema 镜像、test_notice_origin.py；两端 notice-origin 浏览器 fixtures 和检查脚本；docs/api/notice-origin.md；本记录。未改动原公告分享、收件人权限、公开接口、角色授权或其他任务行为。
- Backend: 独立源码副本 C:/Users/EDY/AppData/Local/Temp/notice-origin-java-shc4aqfd/backend，39 项测试通过，包含来源选择/默认/保留、实际发布人、复制不继承、冻结名称、历史空值、搜索与租户/接收范围隔离及既有公告分享回归。完整 18 模块 reactor 成功。最后只更新 3 个查询 VO 的 Swagger 文案，随后最终编译成功；当前对应 Java 源码已与副本同步。
- Test isolation: 首轮 38/39 通过，原 NoticeShareServiceImplTest 的全表零阅读断言被其他测试留下的 system_notice_read 数据污染；原 clean.sql 未清理该表。未扩改清理脚本，采用 -DreuseForks=false 后 39/39 通过。日志 .notice-origin-java-isolated.log；最终编译 .notice-origin-java-final-compile.log。
- Frontend: Workbench 18 项公告既有测试通过，最终 typecheck 通过；Admin 公告文件 ESLint 通过。Admin 全量 vue-tsc 以项目 8GB 内存运行后，仍由已有 src/api/zsjos/withdrawal/index.ts 的六组重复字段阻塞，本次未修改。最初默认内存不足已按项目参数解决。日志 .notice-origin-workbench-types.log、.notice-origin-admin-types.log、.notice-origin-admin-eslint.log。
- Browser: React/Vue × 1440/390 四套 Chromium 用例全部通过（默认部门、改选/保存 payload、无客户端发布人、选项错误重试/空态、ALL/TARGET/历史详情、来源搜索/无结果）；无页面运行错误。使用当前实际组件与合成 HTTP 响应，不表示真实账号联调通过。已检查截图，元信息无重叠，移动端正常换行；Vue 弹窗截图在动画完成后重取。证据 .notice-origin-browser.log 与 C:/Users/EDY/AppData/Local/Temp/notice-origin-browser/。
- Database: MySQL 8.4.11 上初次、重复、部分应用、缺前置版本、错误列类型、版本写入失败及已有版本但缺列恢复均通过；成功版本双账本原子记录，失败不推进标记。中文 HEX、历史文本保留、新列快照、表结构比对通过。脚本的 6 个独立场景保留在 no291_1008123602_*。
- Fresh: 最终完整 bootstrap/适用迁移链至 V291、仓库 core verification 全部通过；保留库 no291_1008123602_fresh。初次 fresh 后检测到并行 baseline 变化，已重跑；最终执行前后校验 baseline、迁移和 verification 文本一致。最终 baseline SHA256 b083891ecd5f6a905505c144a95bf8741e6323cf5b68e2aee51970f6477bc179。日志 .notice-origin-mysql-final.log。两个 core 镜像一致；zsjos_db.py check 通过。
- Development sync: 在检查本地 yudao-mysql / ruoyi-vue-pro 的 V287 双账本及公告表后，执行了仅新增 5 个可空列和 V291 双账本的非破坏性迁移；原 6 条公告行数保持，未补写历史来源或发布人。新增列类型、字符集、中文注释 HEX 与受控结果一致。日志 .notice-origin-development.log。未执行其他未部署功能的迁移或改动共享环境。
- Coordination: 两处 SafeRichText announcementTables 入口已与表格工作流当前 prop 实现联验；该工作流继续拥有表格适配，保留其修改。announcements.css 来源样式已交付，本工作流不再写入。
- Remaining: 未部署/重启业务后端、未执行真实账号 HTTP 联调或生产验收；Admin 全量类型检查上述既有错误未解决。新增能力需按 V291 -> 后端 -> 两前端发布后做真实账号验收。无新增依赖、分支/工作树操作、提交、推送或权限变更。其他未提交工作已保留。
- Ownership released for this workstream; local implementation and applicable verification complete.
