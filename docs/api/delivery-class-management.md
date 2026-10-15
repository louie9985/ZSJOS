# 班级管理与调班 API

本文档描述租户级交付班级、逐课程服务调班和 BPM 引用契约。接口均为 Admin API，使用
标准 `CommonResult`；菜单权限、部门数据范围和 ZSJOS 对象权限累积生效。

## 班级查询与维护

### 工作台快捷筛选（2026-10-15）

工作台提供项目、班主任、班级名称/编号、考期范围与服务中/已结课/全部筛选。默认服务中，其余不限；条件取交集，切换后从第一页查询，关键词防抖300ms，回车立即查询。刷新保留条件，重置恢复默认。列表总数不包含独立待分班入口。

- /page、/my-page 新增可选 projectCategoryId、examDateStart、examDateEnd、excludeSystemClass。日期为 YYYY-MM-DD，包含首尾当天；倒置范围返回参数校验错误。省略新参数保持旧行为，包括 Admin 和学员管理班级选择调用。旧 categoryId 仍查询历史班级分类，不与新服务项目参数混用。
- 项目取班内 active/paused/completed 服务关联订单商品 product_snapshot 中的 categoryId/categoryName/categoryPath，不读取当前产品分类或旧班级分类作为替代。任一服务项目匹配即可命中班级，不重复班级；服务条数仍为整个班级原有统计。无订单、快照缺失或无有效分类ID的服务不推断项目，空班在不限项目时可见。响应 projects: [{id,name,path}] 为历史快照投影；缺少名称显示“项目名称未记录”，无项目显示“项目未记录”。
- 考期使用班级冻结 exam_schedule_snapshot，与自主日期契约一致，不读取后来修改的考期日历日期。支持单日、区间和旧“名称 · 日期”格式，区间相交即命中；不可解析、无效或倒置区间只在不限日期时可见。SQL在分页前筛选，保留原有排序。
- 快捷范围按 Asia/Shanghai 计算：本月/下月为自然月；未来30/90天包含今天，到今天后第29/89天。自定义选齐范围后生效，清空为不限。
- 新增 GET /filter-context 和 /my-filter-context，分别使用与 /page、/my-page 相同权限；接受 readScope/targetUserId，经同一范围解析，不应用当前筛选条件。返回 {projects,homerooms,pendingClass}。项目与班主任覆盖可见服务中/已结课正式班；班主任采用班级记录的人员ID、姓名和部门快照，停用人员仍可作为历史筛选项。不要求创建/编辑权限，不复用写候选接口。
- pendingClass 仅在原范围允许查看系统班时返回。入口导航到该班学员管理，不携带正式班筛选条件。列表与上下文请求分别处理错误和重试；上下文失败时选项和待分班入口一起提示重试，禁止静态替代。

本次不新增权限、角色授权或数据库结构，不改创建、编辑、分班、调班和结课规则。后端先于工作台发布；Admin保留原界面与兼容参数。验证入口：DeliveryClassFiltersTest、只读可选 DeliveryClassFilterMysqlTest、Workbench日期及页面定向测试。

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
  不需要分类、产品、规格或 SKU 参数；旧筛选参数不再限制结果。精确考期按 `exactDate`、多日考期按
  `endDate`，结束日期次日起不再作为参考候选。响应增加 `exactDate/startDate/endDate`，供弹窗日历定位与选择。
- `POST /zsjos/delivery-class/create`、`PUT /zsjos/delivery-class/{id}`、
  `POST /zsjos/delivery-class/{id}/complete`：创建、编辑和手动结课。

Admin 和 Workbench 的班级表单均只保留一个“考期日期”选择框。“选择已有考期”打开参考月历，按日期查看并点击已有考期，填入当前所选的日历日期，多日参考考期也只填入所选当天，填入后仍可修改。参考数据仅在打开弹窗时加载；加载失败、无权限或无候选不阻止返回手动填写。

正式班编号为 `BJyyyyMMddHHmmss####`，由服务生成并依赖租户级唯一约束及冲突重试。
班级名称 `className` 必须手工填写（非空白，最多 100 字）。新客户端提交 `className/startDate/homeroomUserId`，编辑附带 `version`。日期使用 `YYYY-MM-DD`；`startDate` 必填，前端不提供或提交结束日期。后端保留历史区间和旧客户端 `endDate` 的兼容校验，省略时按单日保存。自主日期不受考期日历是否存在、发布状态或是否结束限制。不关联产品、SKU 或分类，旧客户端提交的目录字段忽略。

班级日期独立存入既有 `exam_schedule_snapshot`，格式为单日 `YYYY-MM-DD` 或区间 `YYYY-MM-DD~YYYY-MM-DD`，无需改表。只要提交自主日期，`examScheduleId` 即不参与校验或保存；编辑时显式清空旧关联。日历参考名称和关联 ID 不成为班级日期的事实来源。旧客户端仅提交 `examScheduleId` 的路径保持兼容，仍校验已发布、未结束并冻结其名称与日期快照；两种路径均受原有权限、部门、班主任资格和版本校验约束。

响应增加 `startDate/endDate`，并兼容 `scheduleType/exactDate`。旧班级从既有日期快照或“名称 · 日期”快照解析，不读取后来被修改的日历日期；无法解析的旧快照保持原文展示，日期留空，编辑时由用户明确选择。历史数据不批量改写。新前端依赖新增日期字段，部署顺序为后端在先、前端随后。
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
