# 通用工单中心 API

通用工单中心使用 ADMIN 身份、租户上下文和标准 `CommonResult`。模板、菜单、按钮权限、角色、部门、用户和字典均以 System 数据为准。反馈中心使用 `business_type=FEEDBACK`，不进入本接口的目录、列表或命令。

## 模板与发布版本

模板根记录保存当前草稿；发布会创建不可变版本，并冻结名称、分类值与标签、动态字段、发起/接收资格、指派方式、拒单策略、编号规则和组织标签。停用仅阻止新建，运行实例继续引用原发布版本。

- `GET /admin-api/zsjos/work-order/scene/page`、`scene/get`：管理端模板分页和详情。
- `POST /admin-api/zsjos/work-order/scene/create`、`PUT scene/update`：创建或保存草稿。
- `GET scene/publish-validation`、`POST scene/publish`：发布校验和发布。
- `PUT scene/disable`、`GET scene/versions`：停用和版本历史。
- `GET audit/page`、`GET audit/{id}`：管理员只读运行审计，不授予业务命令权限。

处理器只接受注册值 `GENERIC` 和 `PRODUCTION_TICKET`。分类来自空初始化的 `zsjos_work_order_category` 字典。动态字段支持 `text`、`textarea`、`number`、`date`、`datetime`、`user`、`department`、`dictionary`；字典、用户和部门值在创建时保存显示标签快照。

## 员工查询与创建

- `GET /admin-api/zsjos/work-order/catalog`：返回当前用户实时有权发起的已发布模板。
- `GET candidate-page`：按模板发布版本中的角色/部门规则分页查询有效接收人。
- `GET candidate-department-page`：从符合发布版本接收资格的有效员工中归并其当前有效部门；指定部门只能从该结果中选择，避免创建无人可认领的部门池。
- `POST file/upload`：上传请求或完成附件，返回稳定 Infra 文件 ID 和元数据。
- `POST create`：创建普通工单；拍剪模板（`PRODUCTION_TICKET`）可选携带 `relatedAccountId`，携带时服务端加载该账号已确认的定位卡并冻结快照，未携带时按无账号工单创建，两种情况都在同一事务创建拍剪记录和统一信封。
- `GET pool`、`GET my-page`、`GET {id}`：候选池、我的五类视图和详情时间线。

`GET pool` 的 `sceneCode` 是可选筛选参数（最长 64 字符）；省略时分页查询当前用户有资格接收的全部类型，传入时只查询指定类型。两种查询均保留租户隔离、候选资格和接收部门限制，不因省略类型而扩大授权范围。

候选池 SQL 使用 JSON 包含判断匹配发布快照中的数值角色/部门 ID，并保留实时有效用户、角色和部门定向范围条件。查询及分页计数必须经过 MyBatis-Plus 租户 SQL 解析与注入；不使用当前解析器不支持的 `JSON_TABLE`，也不通过跳过租户拦截器规避解析错误。

创建请求固定要求 `remark`、`values`、`idempotencyKey`，附件最多 20 个；指派人和指派部门只能按模板允许方式二选一。服务端校验当前账号状态、角色、部门、对象权限、字段类型、字典有效性和附件归属。响应中的 `availableActions` 是当前用户可执行动作的唯一前端依据。

账号绑定只在学员账号页强制：账号页发起剪拍/外勤工单时必须选择该学员名下的账号；工单中心直接发起时 `relatedAccountId` 可省略，创建出的拍剪记录 `account_id` 为空，`account_ids_json` 为 `[]`，不冻结账号快照与定位卡。未绑定账号时接收人归属改用模板发布版本的候选列表校验，不跳过接收人合法性检查。

## 状态命令

命令地址为 `POST /admin-api/zsjos/work-order/{id}/{action}`，其中 `action` 为 `take`、`claim`、`reject`、`withdraw`、`complete`、`accept`、`return` 或 `terminate`。请求必须包含当前 `version` 和 `idempotencyKey`；拒单、撤回、打回和不合格终止要求 `reason`，提交完成要求 `resultRemark`，可附结果文件 ID。

每个命令同时校验菜单权限、对象参与关系、实时候选资格、租户、状态和版本。幂等键仅在操作人、操作、版本、参数和附件指纹完全一致时重放；不同请求复用同一键返回稳定冲突。并发认领仅一个请求成功。轮次从 1 开始，只有打回重做增加轮次，时间线按轮保存完成说明、结果附件和验收结果。

## 拍剪扩展

`PRODUCTION_TICKET` 保留专用业务状态事实源，统一信封保存模板版本、账号关系、参与人、候选池范围、表单和附件快照，并提供统一列表投影。账号页与工单中心使用同一模板目录、创建上下文和资格边界；客户端不能提交自造定位卡快照。旧拍剪命令仍由 `ProductionTicketService` 执行，并同步统一信封投影。

学员概览可分别发起剪辑设计和拍摄外勤工单。创建请求可提交同一学员下的多个 `accountIds`，但只创建一张拍剪工单；旧 `accountId` 保留为首个账号的兼容主键。服务端逐个验证账号对象权限和学员归属，并冻结每个账号的昵称、平台、`homepage_url` 和 `cover` 文件 ID。模板中的 `account_link` 由服务端以冻结主页链接列表覆盖，Workbench 用通用链接组件展示链接。

动态表单字段支持 `attachment`。字段值提交文件 ID 数组，单字段最多 20 个且不得重复；服务端将动态字段附件与工单顶层附件合并后校验上传人和 `zsjos/work-order/` 文件命名空间，并保存文件名、类型、大小快照。字段值保留字段到文件 ID 的关系，历史详情使用附件快照名称展示，不重新解释为其他文件。

## 通知与可见性

指定派单、进入候选池、接单、拒单、提交验收、打回、通过、终止和撤回通过 System 通知场景发送持久化站内信。收件人去重并排除当前操作人。普通详情仅发起人、当前及历史处理人、当前实时候选池成员可见；管理员全量查看必须使用独立审计接口。

## 当前租户管理员读取

my-page 新增可选 readScope=SELF|ALL|USER、targetUserId，默认本人。管理员全量／指定人员查询保留 view/status 与业务类型过滤，availableActions 为空；候选池和办理命令不变。管理审计页面继续使用现有审计接口。
