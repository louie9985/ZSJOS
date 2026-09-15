# 学生详情读取范围

`GET /admin-api/zsjos/student/my/{personId}` 和 `GET /admin-api/zsjos/student/my/by-service/{relationId}` 使用既有学生查询权限及对象权限。

具有 `DeliveryClassService.PERMISSION_QUERY_MANAGED` 对应配置权限的用户，详情按学生列表同一部门范围、服务 owner 集合读取。全范围仅来自服务端数据范围；空部门集合只包含本人。交付主管另持有 `zsjos:delivery-class:direct-transfer` 时，可在同一部门数据范围内发起主管直接调班；该权限不授予接收、联系、分配或学员资料修改操作。普通用户保持本人负责及既有协作查询规则。

同一学生不同课程分别受服务关系范围限制。按服务关系读取必须确认指定关系本身可见，不能借另一条可见关系读取范围外课程；响应只聚合可见关系。范围解析失败不降级为全量查询。

Admin 的 `RegistrationApi.getMyStudent` 与 Workbench 的 `api.myStudent` 消费相同接口；响应结构未改变。按服务关系入口的对象权限同步支持已有管理读取范围。

验证覆盖：普通负责人/协作者、部门范围内/外、全范围、空范围、历史服务、同学生跨范围课程、范围解析异常，以及管理读取不授予写操作。

页面详情还会读取 `/zsjos/student/service/{relationId}/contact-context` 和 `/contact-records`。这两个子资源的服务内部校验复用同一 `student-service` 管理读取权限；仅通过主详情接口不能作为整个详情页验证成功的依据。原有负责人命令校验保持不变。
