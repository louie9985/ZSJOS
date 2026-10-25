# 学员档案阅读布局与默认敏感读取

## Registration — 2026-10-23 17:17:56 +08:00（北京时间）
- ID: student-archive-display-20261023; environment: local（环境变量/环境文件未指定）；branch: main；worktree: D:\ZSJ-OS；base/HEAD: 404387de566fcb9abf4b264f0e005bf0566ea209；target branch/integration order: None。
- Owner: Codex root；Goal: my-students学员档案按已确认的分区/字段网格/选项底色布局展示，附件用通用组件，默认读取权限内完整敏感信息，无查看/隐藏切换。
- Non-goals: 修改后端敏感授权、角色权限、数据库、报名资料弹窗/抽屉机制、Admin、依赖、共享服务、Git发布。
- Ownership: frontend/workbench/src/components/StudentArchivePanel.tsx、StudentArchiveExtraPanel.tsx；styles/pages/student-archive.css；已有test/student-archive-inline-browser.py及必要新增test/student-archive-display*；frontend/workbench/docs/ui-guidelines.md、docs/api/student-information-collection.md；本记录。单一root写入，不使用子代理。
- Dependencies: 保留现有行内编辑/上传/保存/取消/幂等/未保存导航guard；前一inline工作流已交付释放。复用StudentInfoField.group/sort快照、/archive与/sensitive、原附件鉴权字节、AttachmentCard和既有student-info-panel样式。StudentArchiveExtraPanel现有考试可见性配置保留。
- Decisions: 默认先取服务端权限投影，有canReadSensitive且已提交则在展示前自动读取/sensitive；不提供敏感切换按钮。无权限仍服从服务端脱敏和拒绝，未授予任何真实权限。附件卡仅初始元数据，逐项主动预览/下载。默认完整读取对打开/刷新/保存后均生效，旧响应与Blob随服务切换失效。
- Verification: Workbench typecheck，现有AttachmentCard及时间专项；真实Chrome1440/390的分区/标签/长内容、默认full/无切换、无敏感权限/拒绝/重试、刷新与保存后full、附件预览下载/清理/切换；复用既有inline浏览器用例保留编辑/字典/地区/上传/冲突/取消与readonly。定向diff、文档与样式token检查。全程合成接口，不读写真实报名。

## Delivery — 2026-10-23 17:27:21 +08:00（北京时间）
- Context: local / main / D:\ZSJ-OS；HEAD仍为404387de566fcb9abf4b264f0e005bf0566ea209；target branch/integration order: None。
- User goal: my-students学员档案采用前述分区/选项底色布局、通用附件组件，默认完整敏感阅读且无查看/隐藏切换。
- Result: 报名资料按后端group/sort分区、两列字段、长内容通栏；dict/area快照浅底标签；主侧9:3、窄容器单列。右侧课程/提交时间/表单版本/维护版本；报名与考试卡片统一主题token。保留既有逐项编辑、上传、保存/取消、版本/幂等、原提交快照、维护历史和StudentArchiveEditGuard。
- Sensitive behavior: 默认普通档案权限投影后自动读取获准的/sensitive，打开/刷新/保存后均生效；完整读取前保持loading，不闪现普通脱敏结果。移除敏感查看/隐藏及材料批量查看入口。无敏感权限不请求/sensitive，保留后端限制与说明；敏感读取拒绝显示错误及重试，不伪装完整读取成功。后端、授权/对象范围及真实账号权限未改。
- Attachments: 报名及考试信息的阅读/行内编辑均使用AttachmentCard；初始只取元数据，点击预览/下载经既有服务关系鉴权字节接口。刷新/切换/卸载销毁预览；迟到文件响应撤销Blob。保留可见性配置及所有编辑边界。
- Changed files: frontend/workbench/src/components/StudentArchivePanel.tsx、StudentArchiveExtraPanel.tsx；src/styles/pages/student-archive.css；test/student-archive-inline-browser.py；frontend/workbench/docs/ui-guidelines.md；docs/api/student-information-collection.md；本记录。先前用户/其他任务的行内编辑及导航保护修改已保留，未改StudentArchiveEditGuard或RegistrationPages。
- Verification: 最终npm run typecheck通过；AttachmentCard/time专项14项通过；定向git diff --check及全部archive CSS主题token检查通过。Chrome最终1440/390合成HTTP专项通过：默认full打开/刷新/保存、无切换按钮、四组/浅色快照/长附件名/无横溢出、PNG与有效PDF预览/下载、活跃与迟到Blob清理、服务切换迟到敏感响应隔离、无敏感权限不发敏感请求、拒绝/错误重试。既有完整inline专项仍通过：文本/字典/地区/附件/考试复合值编辑，保存/取消/冲突重试，幂等保持，readonly/敏感编辑限制，迟到edit响应隔离。已目视最终桌面、手机及手机PDF截图。
- Evidence: C:\Users\EDY\AppData\Local\Temp\zsjos-student-archive-display-browser.log；最终截图目录C:\Users\EDY\AppData\Local\Temp\zsjos-student-archive-inline-l792wq3l。首轮通用附件按钮含eye图标导致旧精确locator未匹配，改为实际可访问名称后全部通过；未改业务逻辑来规避测试。
- Dependencies/integration: None；无新增依赖、SQL/数据/权限/后端契约/共享服务/Git操作。Admin和报名资料弹窗/个人背景抽屉不在此次展示范围。
- Remaining: 本范围无待实现项；真实认证账号与目标部署未执行，合成浏览器验收不代替实际账号验收。所有权释放。
