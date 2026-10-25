# 学员账号删除展示与最终确认

## Registration — 2026-10-22（北京时间）
- ID: account-delete-visibility-20261022; environment: local（ZSJOS_AGENT_ENV 和环境文件未指定，按规则回退）。
- Goal: 已删除媒体账号不再显示在我的学员列表与账号标签页；填写原因后点击确认删除仍需最终确认。
- Non-goals: 删除权限、BPM 审批规则、历史记录、数据库修改、服务重启/部署、依赖、分支/提交/推送。
- Branch: main; worktree: D:\ZSJ-OS; base commit: 404387de566fcb9abf4b264f0e005bf0566ea209; target branch/integration order: None.
- Owner: Codex /root. Ownership: root 独占 MediaStudentsPage.tsx、相关账号标签测试、新增 account-delete-visibility 浏览器夹具、docs/api/media-account-delete-approval.md 和本记录；account_visibility_trace 负责 MediaStudentService.java 及相关专项测试。AccountProfilePanel.tsx 当前被诊断与历史弹窗工作流占用，本工作流暂不写入，待其释放后登记接手；已有 media-account-delete 浏览器测试暂不修改。
- Dependencies: 服务端 runStatus=deleted 是删除完成的事实源；delete_pending 继续可见。保留此前未提交的关键词、历史弹窗、诊断及删除流程实现。仅修改媒体学员列表/详情投影，不改变通用 Mapper 的历史查询。
- Verification: 后端列表/详情过滤与权限专项测试；Workbench typecheck、账号标签/名单相关测试；真实浏览器桌面/移动宽度验证取消不提交、最终确认仅一次请求、失败保留原因并重试、删除后列表/标签同步移除和概览回退。采用隔离合成接口，不执行真实删除。后端构建避免与其他工作流共享输出冲突。
- Task ownership update: deletion_visibility_browser 独占新增 test/account-delete-visibility.html、.tsx 和 -browser.py；root 继续独占生产页面与记录。浏览器使用现有 Vite 5174 的隔离合成传输和独立浏览器上下文，不启动/停止服务。
- Test ownership update: root 接手既有 test/media-account-delete-browser.py，更新直接删除的第二层确认断言；原合成接口 fixture 不变。AccountProfilePanel.tsx 仍等待诊断工作流明确释放。
- Component ownership transfer: 用户明确授权向诊断聊天协调交接；其 handoff/local-diagnosis-loading-20261022.md 已明确释放 AccountProfilePanel.tsx（交接 SHA256 A6213BDB2C24616B7FDC8370CE4391D80D0FDC74161E78E8F72015AC0F3F446D），历史弹窗工作流也已交付释放。root 现独占该文件，仅增加删除最终确认、提交锁及删除弹窗导航保护，保留所有诊断/历史实现。
- Verification update: 独立复核发现保留页面后台刷新可能通过新路由清理抢回当前页，root 在原已拥有文件补激活状态/延迟清理保护；deletion_visibility_browser 继续独占原 3 个新夹具，增加慢刷新跨页切换/返回场景。复用现有 RetainedReviewRoute，不改共享导航设施。

## Delivery — 2026-10-22（北京时间）
- Context: local，main，D:\ZSJ-OS，HEAD 404387de566fcb9abf4b264f0e005bf0566ea209；owner Codex /root，integration None。
- Diagnosis: MediaStudentService 列表/详情未过滤运行状态 deleted；前端 onSaved 继续传旧账号 ID，服务端隐藏后原校验会使整页报错；确认删除原来直接请求 API。
- Result: 仅在媒体学员列表/详情投影排除 deleted（权限仍独立校验），保留 delete_pending、学员主体及历史数据。关联账号详情/统计也基于过滤后的账号集合。删除当前或最后账号后同步更新卡片和标签、回概览、清理账号级路由。保留初始无效深链拒绝；隐藏页面不会抢回当前页，返回原地址才补清理。
- Confirmation: 原原因表单的“确认删除”现在打开“再次确认删除账号”，展示昵称/业务编号，只有“仍然删除”才提交；默认焦点在取消，取消保留原因；提交锁防重复请求、失败可重试，运营申请流程保持不变。补删除弹窗导航保护和卸载后的响应检查。
- Files: 后端 MediaStudentService.java / MediaStudentServiceTest.java；Workbench MediaStudentsPage.tsx / AccountProfilePanel.tsx；新增 test/account-delete-visibility.html / .tsx / -browser.py；更新 test/media-account-delete-browser.py；docs/api/media-account-delete-approval.md；本记录。完整保留已有关键词排序、学生新增、审批选择、诊断加载和历史弹窗等未提交改动。
- Backend verification: 独立 JDK 25 javac 编译当前服务与测试，JUnit 18/18 通过（新增 4 项，覆盖状态、排序分页、关联投影及最后账号删除）。复制既有 surefire classpath 后在独立临时输出运行，无共享 target 写入；证据 %TEMP%/zsjos-account-delete-visibility-cx4375xq/{compile.log,tests.log}。Admin 源码无 media-students API 消费，未改变通用 Mapper/HTTP 响应形状/权限契约。
- Frontend verification: 最终 npm run typecheck 通过；账号标签、名单卡片和页面守卫共 21 项测试通过；新 fixture 单独 TypeScript 检查和 Python 语法检查通过。既有删除面板浏览器回归通过；最终新完整浏览器脚本通过 1440/390 取消/单请求、列表和标签同步移除、最后账号概览、失败保留原因重试、pending/运营申请/无权限/无效深链，以及真实 RetainedReviewRoute 下延迟 profile/detail 的跨页/返回场景，无 pageerror。真实 admin-api 被拦截，均为合成数据。
- Visual/review: 桌面/手机最终确认框、删除后页面已目视检查，无溢出；截图 %TEMP%/zsjos-account-delete-visibility/{confirm-1440.png,confirm-390.png,removed-1440.png,removed-390.png}。独立只读复核发现的保留页导航问题已修复并专项验证，最终复核无新问题；scoped git diff --check 通过。
- Boundaries: 未改数据库、真实账号、角色权限或 BPM，未重启/部署服务，未新增依赖，未进行 Git 分支/提交/推送。未改路由注册或构建配置，不要求生产打包。真实认证账号与目标运行环境尚未验收，需发布后端和工作台后生效。
- Completion: 本范围实现、文档、验证完成；所有权释放。
