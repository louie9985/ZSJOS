# 班级管理与调班 API

本文档描述租户级交付班级、逐课程服务调班和 BPM 引用契约。接口均为 Admin API，使用
标准 `CommonResult`；菜单权限、部门数据范围和 ZSJOS 对象权限累积生效。

## 班级查询与维护

- `GET /zsjos/delivery-class/page`：按当前用户部门 DataPermission 分页查询管理范围；租户级
  `PENDING` 系统班级作为待分班入口一并返回。
- `GET /zsjos/delivery-class/my-page`：只返回当前用户担任班主任的正式班。
- `GET /zsjos/delivery-class/{id}`、`GET /zsjos/delivery-class/{id}/students`：读取班级及课程
  服务分页；调用方必须传递并消费 `pageNo`、`pageSize` 和响应 `total`，不得截断为固定前 50 条。
  学员行以 `serviceRelationId` 为操作边界，并返回订单商品所属 `categoryId`，使待分班服务也能
  加载同分类目标班。Workbench 从“我的班级”进入学员详情时通过路由 state 传递该 ID；主管管理
  视图不因此获得 owner 专属的学员服务操作权。
- `GET /zsjos/delivery-class/options?categoryId=&includePending=`：返回同分类、服务中的正式班；
  只有显式 `includePending=true` 时附加租户待分班班级。
- `GET /zsjos/delivery-class/homeroom-candidates`：返回主管部门范围内启用且同时持有
`zsjos:delivery-class:query-my`、`zsjos:student:query-my` 的用户。
- `GET /zsjos/delivery-class/product-options`：返回教务端同源的启用产品、规格和 SKU。
- `GET /zsjos/delivery-class/category-options`、`GET /zsjos/delivery-class/exam-options?categoryId=&productId=`：
  返回启用产品分类和同产品范围、已发布且未结束的考期；精确考期按 `exactDate`，粗略考期按
  `roughEndDate`，结束日期次日起不可选。
- `POST /zsjos/delivery-class/create`、`PUT /zsjos/delivery-class/{id}`、
  `POST /zsjos/delivery-class/{id}/complete`：创建、编辑和手动结课。

正式班编号为 `BJyyyyMMddHHmmss####`，由服务生成并依赖租户级唯一约束及冲突重试。
班级保存产品、规格条件、所选 SKU、产品分类完整路径、考期、班主任和创建时部门名称快照。
SKU 可以只选择产品下的部分有效 SKU，但至少选择一个；班内已有任何服务关系后
产品范围永久锁定；名称、同产品范围未结束考期和同班级部门内的合格班主任仍可修改。结课只
关闭新学员入口，不修改已有服务关系、接收状态或服务阶段。

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
主管必须同时覆盖源正式班（待分班除外）与目标班的部门数据范围。目标必须是同订单商品
产品分类、服务中、非当前班的正式班，且班主任仍启用并具备规划师能力。命令原子更新班级、
owner 与未完成待办执行人，不重置接收状态或历史。

## 规划师 BPM 调班

- `POST /zsjos/class-transfer/service/{serviceRelationId}`：当前服务 owner 发起正式班之间调班。
- `GET /zsjos/class-transfer/my-page`、`GET /zsjos/class-transfer/{id}`：查看本人申请及 BPM 引用。

流程定义 key 为 `zsjos_class_transfer`，business key 为 `class-transfer:{requestId}`，审批节点
key 为 `originalSupervisorReview`。审核人取申请人当前部门 `leaderUserId`，必须启用、不能是
申请人本人，并持有 `bpm:task:update`。流程未部署或主管无效时事务回滚，不残留业务申请。

同一服务关系最多一个 `pending` 申请。审批通过事件重新锁定服务关系和目标班，并核对源班、
源班主任、服务版本、目标班状态及目标班主任快照；发生变化时申请转为 `invalidated`，不强行
调班。通过复用主管调班的原子迁移核心；拒绝为 `rejected`，其他终止/撤销为 `cancelled`。
监听器仅处理 `pending` 记录，因此重复终态事件不会重复迁移待办或发送通知。审批动作和历史
继续由 BPM 统一审批中心承载，ZSJOS 不创建平行任务表。

V188 只交付业务表、API 引用与菜单权限。真实 BPM 流程定义仍需在目标环境单独部署并验收。
