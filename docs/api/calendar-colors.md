# 日历颜色与共享课程色板

## 使用与存储
我的日历快捷色按登录员工独立保存；课程日历色板在租户1内共享。表单只显示底色栏目及一组色块，操作横排并自然换行。课程左上角图例使用与表单相同的完整共享色板，不再显示两段冗余说明。

课程四个初始项：公共营养师 #6ABF6D、营养指导员 #00D0FF、注册营养师 #B695E8、执业药师 #FFBE73，均为普通可编辑、可删除的快捷项，不作为业务分类、不按课程名称自动匹配。删除后刷新或重新登录不会补回，允许删空。

每份色板最多20项（共享课程含四个初始项），名称去首尾空白后1–20个Unicode码点，同色板不区分大小写重名检查。颜色相同但名称不同允许。个人色板唯一键按租户/员工/PERSONAL，课程使用租户/0/COURSE_SHARED；0仅是共享存储范围标识，不是员工或授权身份。写入通过行锁串行化，保留登录操作者审计。

保存快捷色独立提交，取消日程仍保留；失败保留输入并允许重试。新建不自动沿用上次颜色。打开编辑器、页面刷新或窗口重新获得焦点时重新读取色板，自己保存后立即同步图例。共享色板缺失时报升级错误，不在请求中重新生成默认项。

## API 与权限
ADMIN API /zsjos/calendar-colors/{type} 支持 GET、POST；/{type}/{id} 支持 PUT、DELETE。type 仍仅 PERSONAL、COURSE，公开接口不接受用户身份或内部共享范围。POST/PUT 为 name、color；响应保持 presets、colors，其中 presets 为空，所有可维护项放在 colors。元素为 id、name、color，创建顺序展示。

读权限分别为 zsjos:personal-calendar:query、zsjos:course-calendar:query；个人维护沿用 zsjos:personal-calendar:color-manage，课程维护改为 zsjos:course-calendar:manage，与新增课程一致。旧 course-calendar:color-manage 不再参与授权；迁移不撤销、重新分配或更改角色授权。AOP验证调用主体，服务验证实际日历功能权限，课程存储范围与操作者身份分开。

个人与课程日程保存、列表、详情及搜索的 backgroundColor 契约保持不变：#RRGGBB保存为大写，更新省略/null保留，空串清除。每条日程保存实际色值，修改或删除快捷项不改变历史记录。课程颜色不参与通知内容版本和哈希；管理端旧请求兼容保留。

## V315 升级与恢复
先完成 V314，再执行 V315__shared_course_palette.sql。本次用户明确授权清理租户1的旧个人 COURSE 色板，不导入共享项；旧行逻辑删除并保留JSON恢复证据，PERSONAL及所有日程不变。共享色板初始化一次；重放保留已修改或删空的内容。数据变更、后置校验及双账本在同一事务内。已存在成功记录却丢失共享行时拒绝重建，需审查并恢复备份。

升级前备份色板与双账本，停用旧版本写入后执行迁移并部署新后端。旧客户端不能用于共享色板维护。回退需保留或备份共享色板，按批准范围恢复旧COURSE配置；没有自动数据回滚。V315无DDL，不修改历史编号迁移、基线或角色授权。共享/生产目标执行仍须另行授权。

## 验证
- test_shared_course_palette.py：隔离MySQL首次/重复/前置失败/账本失败/后置失败及已有空共享色板；--java验证真实持久化、跨用户共享、编辑删除、个人隔离和并发限额；--apply-dev按授权备份并清理本地。
- CalendarPalettePermissionTest及CalendarPaletteServiceTest：查询/维护拒绝、课程manage授权、输入约束。
- calendar-views-browser.py、course-shared-palette-browser.py：真实组件日/周/月及初始色可编辑/删除、图例同步、空色板不补回；测试为隔离传输，实际跨设备登录仍需部署后验收。
- 日程视图详见 [日／周／月视图](calendar-views.md)。
