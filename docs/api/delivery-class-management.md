# 班级管理与调班 API

本文档描述租户级交付班级、逐课程服务调班和 BPM 引用契约。接口均为 Admin API，使用
标准 `CommonResult`；菜单权限、部门数据范围和 ZSJOS 对象权限累积生效。

## 班级查询与维护

- `GET /zsjos/delivery-class/page`：按当前用户部门 DataPermission 分页查询管理范围；租户级
  `PENDING` 系统班级作为待分班入口一并返回。
- `GET /zsjos/delivery-class/my-page`：只返回当前用户担任班主任的正式班。Workbench 班级管理将该响应按服务端分页加载为卡片；卡片只展示班级摘要和人数，不展示班内学员。
- `GET /zsjos/delivery-class/{id}`、`GET /zsjos/delivery-class/{id}/students`：读取班级及课程
  服务分页；调用方必须传递并消费 `pageNo`、`pageSize` 和响应 `total`，不得截断为固定前 50 条。
  学员行以 `serviceRelationId` 为操作边界，并返回订单商品所属 `categoryId`。Workbench 从“班级管理”
  进入学员管理时通过路由 state 传递该 ID；主管管理视图不因此获得 owner 专属的学员服务操作权。
- `GET /zsjos/delivery-class/options?categoryId=&includePending=`：返回服务中的正式班，按
  可选 `categoryId` 过滤；不传时返回当前租户全部服务中班级。`includePending` 默认 true，包含并置顶服务中的租户待分班班级。报名分班不传分类参数，也不以产品、SKU、考期或分类匹配作为保存/完成前置条件；双端平铺展示。
  无分类请求仅按服务状态查询，不拼接系统班/正式班互斥条件。显式分类筛选使用“系统班或该分类”条件；MySQL 回归通过实际 Mapper 与租户拦截器核对结果，避免仅验证前端样本或未执行的 SQL。
- `GET /zsjos/delivery-class/homeroom-candidates`：返回主管部门范围内启用且同时持有
`zsjos:delivery-class:query-my`、`zsjos:student:query-my` 的用户。
- `GET /zsjos/delivery-class/product-options`：返回教务端同源的启用产品、规格和 SKU。
- `GET /zsjos/delivery-class/exam-options`：直接返回当前租户所有已发布、未结束考期，展示名称与日期。
  不需要分类、产品、规格或 SKU 参数；旧筛选参数不再限制结果。精确考期按 `exactDate`、粗略考期按
  `roughEndDate`，结束日期次日起不可选。创建与编辑均重新校验目标考期。
- `POST /zsjos/delivery-class/create`、`PUT /zsjos/delivery-class/{id}`、
  `POST /zsjos/delivery-class/{id}/complete`：创建、编辑和手动结课。

班级响应同时返回考期 `scheduleType` 与 `exactDate` 展示字段。`ROUGH` 班级由前端持续显示“未设置精确考期”警示；精确考期仍需通过已有班级编辑权限选择考期管理中已发布且未结束的精确考期。

正式班编号为 `BJyyyyMMddHHmmss####`，由服务生成并依赖租户级唯一约束及冲突重试。
班级名称 `className` 必须手工填写（非空白，最多 100 字），不再自动生成。创建只需名称、具体
`examScheduleId` 和 `homeroomUserId`；不关联产品、SKU 或分类，旧客户端提交的目录字段忽略。
考期必须来自已发布且未结束的考期记录，支持精确或粗略考期；新命名考期保存名称和日期组合的班级考期快照。
旧考期尚无独立名称时沿用日期快照，候选仍展示原历史名称，避免长规格名称超过旧字段长度。
历史班级原产品/规格快照保留仅供历史读取，不再作为编辑、报名分班和调班的匹配或锁定条件。
结课只关闭新学员入口，不修改已有服务关系、接收状态或服务阶段。

班主任必须属于当前交付主管的直接部门、账号启用并拥有启用的 `study_planner` 角色。该角色
资格是写命令的实时前置条件，而不只是候选列表过滤条件。班级创建/编辑、主管直调、
BPM 调班通过、报名分班保存和报名完成都会重新确认账号启用且同时持有上述两项权限。

班主任变更在一个事务中更新班级及其 `active/paused/completed` 服务关系 owner，并转派首联、
学习计划、督学和协助类未完成业务待办。已完成待办、接收状态、联系/计划/督学历史和协作者
不变。System 通知场景 `zsjos_delivery_class_owner_changed` 按班级、服务关系及变更前版本组成的
事件键逐服务关系幂等通知原、新规划师；待分班
转正式班时没有原规划师则只通知新规划师。

## 主管直接调班

`POST /zsjos/delivery-class/service/{serviceRelationId}/direct-transfer` 需要独立权限
`zsjos:delivery-class:direct-transfer`，请求包含 `targetClassId`、服务关系 `version` 和原因。
主管必须同时覆盖源正式班（待分班除外）与目标班的部门数据范围。目标必须是服务中、非当前班的
正式班，且班主任仍启用并具备规划师能力。命令原子更新班级、owner 与未完成待办执行人，不重置
接收状态或历史。

## 规划师 BPM 调班

- `POST /zsjos/class-transfer/service/{serviceRelationId}`：当前服务 owner 发起正式班之间调班。
- `GET /zsjos/class-transfer/my-page`、`GET /zsjos/class-transfer/{id}`：查看本人申请及 BPM 引用。

调班申请和审批通过不要求源班与目标班产品分类一致；双端目标选项使用不带分类的正式班候选接口。
权限、部门范围、服务版本、源班归属、目标状态和班主任资格仍按原契约校验。

流程定义 key 为 `zsjos_class_transfer`，business key 为 `class-transfer:{requestId}`，审批节点
key 为 `originalSupervisorReview`。审核人取申请人当前部门 `leaderUserId`，必须启用、不能是
申请人本人，并持有 `bpm:task:update`。流程未部署或主管无效时事务回滚，不残留业务申请。

同一服务关系最多一个 `pending` 申请。审批通过事件重新锁定服务关系和目标班，并核对源班、
源班主任、服务版本、目标班状态及目标班主任快照；发生变化时申请转为 `invalidated`，不强行
调班。通过复用主管调班的原子迁移核心；拒绝为 `rejected`，其他终止/撤销为 `cancelled`。
监听器仅处理 `pending` 记录，因此重复终态事件不会重复迁移待办或发送通知。审批动作和历史
继续由 BPM 统一审批中心承载，ZSJOS 不创建平行任务表。

V188 只交付业务表、API 引用与菜单权限。真实 BPM 流程定义仍需在目标环境单独部署并验收。
