# 学生详情读取范围

## 2026-10-19：严格读取数据范围

学员管理保留 `zsjos:delivery-class:query` 与 `zsjos:delivery-class:query-managed` 的历史查询入口兼容，但功能权限不产生部门数据授权。管理读取使用 System `getDeptDataPermission` 返回的全部/部门集合；`self=true` 只保留本人关系，不能转换为本人所在部门。多角色范围由 System 合并，空部门集合不扩为本部门，范围解析异常直接失败。

本人负责及既有个人协作关系继续独立生效；部门范围只按服务 owner 所属部门增加读取权，不按同部门其他人的协作关系扩大范围。新课程服务分页/搜索、旧学员分页/详情、student/student-service 对象权限及联系上下文/记录使用一致的严格读取边界。同一学员的范围外课程不可通过可见课程、旧通知或服务深链读取。

班级管理分页、筛选上下文及班级详情采用相同的严格部门读取规则，并保留本人班主任关系。系统待分班的独立 `query-managed` 读取例外继续有效；仅普通 query 不获得该例外，显式 SELF/USER 不附加待分班。列表可见不授予跟进、修改资料、分配和调班权。现有命令范围解析与状态/归属校验保持独立。

本次不修改角色授权、服务归属、数据库结构或历史记录。Admin 与 Workbench 共用既有接口和响应结构；源码测试、只读 SQL/浏览器样本和部署后的真实账号验收分别记录。

2026-10-19：考试与证书信息按当前服务独立维护，无需先提交收集表；原报名收集资料仍按冻结表单展示。详见 [学员信息收集与档案](student-information-collection.md)。

2026-10-18：新增“学员档案”标签页，查看 `zsjos:student-archive:read` 和维护 `zsjos:student-archive:update` 分别配置；维护同时要求查看权限与当前服务本身可读。档案绑定当前报名任务、Person 和订单商品，原始答案不可变，内部修订单独记录。Workbench visibleTabs 和 Admin 权限状态控制入口，后端独立校验功能与对象权限；敏感读取、图片和导出保留独立权限。详见 student-information-collection.md。

2026-10-16：具有 `query-managed` 权限的主管还可读取租户真实系统待分班内的 active/paused/completed 课程服务，无需服务已有负责人。课程服务分页与详情、兼容的旧学员分页与详情、student/student-service 对象读取权限保持同一边界；普通用户和显式 SELF/USER 查询不增加此范围。范围外正式班课程不因同学员另一条待分班服务可读而开放。读取权限不附带接收、联系或修改资料的执行权限。

`GET /admin-api/zsjos/student/my/{personId}` 和 `GET /admin-api/zsjos/student/my/by-service/{relationId}` 使用既有学生查询权限及对象权限。

具有管理读取入口权限（兼容 `zsjos:delivery-class:query` 和 `DeliveryClassService.PERMISSION_QUERY_MANAGED`）的用户，详情按学生列表同一严格部门范围、服务 owner 集合及本人既有协作关系读取。全范围仅来自服务端数据范围；空部门集合不包含同部门其他人。交付主管另持有 `zsjos:delivery-class:direct-transfer` 时，可按现有命令规则发起主管直接调班；查询权限不授予接收、联系、分配或学员资料修改操作。普通用户保持本人负责及既有协作查询规则。

同一学生不同课程分别受服务关系范围限制。按服务关系读取必须确认指定关系本身可见，不能借另一条可见关系读取范围外课程；响应只聚合可见关系。范围解析失败不降级为全量查询。

Admin 的 `RegistrationApi.getMyStudent` 与 Workbench 的 `api.myStudent` 消费相同接口；响应结构未改变。按服务关系入口的对象权限同步支持已有管理读取范围。

验证覆盖：普通负责人/协作者、部门范围内/外、全范围、空范围、历史服务、同学生跨范围课程、范围解析异常，以及管理读取不授予写操作。

页面详情还会读取 `/zsjos/student/service/{relationId}/contact-context` 和 `/contact-records`。这两个子资源的服务内部校验复用同一 `student-service` 管理读取权限；仅通过主详情接口不能作为整个详情页验证成功的依据。原有负责人命令校验保持不变。
