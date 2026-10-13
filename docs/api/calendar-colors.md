# 日历颜色与个人快捷色

工作台我的日历、课程日历支持新建/编辑选色、自选不透明颜色、即时预览及恢复默认。月历、当天详情和移动卡片使用记录自身颜色并按亮度选择黑/白文字。历史空颜色保持原默认；考期日历不变。

## 日程接口

个人与课程保存、列表、详情（课程）、搜索响应增加可选 backgroundColor。仅接受 #RRGGBB，规范为大写。新增省略或空串为默认；更新省略/null保留、空串清除。服务端显式写 NULL，避免 MyBatis 忽略清除。管理端旧表单不提交此字段，因此编辑保留原色；本期不增加管理端颜色界面。课程颜色不参与通知内容版本和快照哈希，不自动发送通知。

## 快捷色接口

ADMIN 路径前缀保持现有契约。/zsjos/calendar-colors/{type} 支持 GET、POST；/{type}/{id} 支持 PUT、DELETE。type 仅 PERSONAL、COURSE；POST/PUT 请求为 name、color，响应为 presets、colors 数组，元素为 id、name、color。POST生成不透明字符串ID；返回顺序即创建顺序。DELETE只移除当前员工色板中的一项，不删除日程。

读取使用对应 zsjos:personal-calendar:query / zsjos:course-calendar:query；维护使用对应 color-manage 按钮权限。服务端从登录身份取得用户，AOP校验本人色板，服务再检查对应日历权限；不接收他人的 userId。租户由现有框架上下文取得，本系统租户为1。两类色板独立。

每人每类最多20项，名称去首尾空白后1–20个Unicode码点，同一色板名称不区分大小写去重。颜色相同但名称不同允许。每个员工/日历持久化一个JSON色板，唯一键及行锁串行化首次创建和后续改动，保证并发限额/重名约束。修改、删除快捷色不改变已有日程颜色。

课程固定预设由服务返回：公共营养师 #6ABF6D、营养指导员 #00D0FF、注册营养师 #B695E8、执业药师 #FFBE73。它们是明确批准的展示快捷标注，不作为课程业务分类，不匹配课程名称，不能由个人覆盖。个人日历无固定业务预设。

保存快捷色独立提交；取消日程仍保留。加载失败显示重试，仍可自选颜色并保存日程；快捷色保存失败保留输入。无色板管理权限时隐藏维护入口，可使用已有快捷项。新建不自动带入上次颜色。修改旧记录回显记录颜色。

## 升级与验证

V314__calendar_colors.sql 在Core V313后、后端发布前执行；新增两列、空色板表和菜单按钮元数据，不修改角色授权、不回填历史。管理员通过角色管理分配两个color-manage权限。既有禁用配置不被恢复。

DDL隐式提交，无法以事务回滚结构；失败不写成功账本，修正前置/结构后重放。回退应用时保留字段、表及颜色数据。升级前备份两张日程表、菜单和版本账本。两份基线结构清单同步最终定义；新环境仍执行bootstrap后有序迁移，历史编号迁移不变。

验证入口：
- python -B script/sql/mysql/tools/test_calendar_colors.py：保留隔离MySQL库，验证首次、重复、前置失败、账本失败、部分应用、错误字段及索引恢复；--apply-dev备份后应用本地开发库，核对原日程数据、角色授权和中文HEX。
- 后端 CalendarPaletteServiceTest、CalendarPalettePermissionTest、CourseCalendarVersionTest、PersonalCalendarEventServiceTest。上述数据库脚本加 --java 验证真实 MySQL 持久化和第20项并发限额；加 --fresh-core 验证完整Core初始化链及本地结构比对。
- 工作台 npm run typecheck；相关日历Vitest；python -B frontend/workbench/test/calendar-colors-browser.py（使用已有5174 Vite及隔离合成数据，不写真实API）。
- 管理端旧请求兼容由源码请求形状审查和后端省略颜色测试覆盖；真实认证HTTP、跨设备登录同步和共享环境上线仍需运行时验收。
