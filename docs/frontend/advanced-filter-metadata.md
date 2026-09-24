# 中视简高级筛选字段元数据契约

2026-09-23。适用 `GET /admin-api/zsjos/advanced-filter/catalog?scene=...`。覆盖118个静态字段及按scene生成的 `duration.diff`；[逐字段目录](advanced-filter-field-catalog.md)、[盘点矩阵](advanced-filter-inventory.md)继续保留缺口状态。本次新增描述信息，不新增筛选能力或改变授权。

## 字段定义

| 属性 | 类型 | 来源及语义 |
|---|---|---|
| fieldKey | string | 稳定字段标识，保存模板及查询条件使用；不因中文名称变化而改名 |
| label / group | string | 后端登记的名称与业务分组 |
| valueType | text/select/number/date/duration | 决定条件值结构；date继续使用现有时间协议 |
| operators | string[] | 已实现且允许的操作符，不允许客户端自造SQL |
| optionSource | string或null | 兼容现有客户端：需加载的字典/实体来源；实体选项已填充时为null |
| options | Option[] | `{value,label}`；选项可能受租户、数据范围、账号状态影响 |
| optionsState | unresolved/ready/empty | 需要加载的来源尚未解析、已解析且有选项、已解析但无选项；前端另有loading/error状态 |
| optionsErrorCode | string或null | 现有选项错误扩展字段；不捏造尚未存在的后端错误分类 |
| supportedScenes | string[] | 从真实查询Binding键生成、排序去重，不按前端标签推断 |
| supportedPages | string[] | 上述场景在已核对页面中的pageKey并集；描述已知接入，不是用户可见菜单，也不是授权或服务端pageKey校验 |
| permission | inherit_query_authorization | 当前无独立字段级权限，沿用目录Controller及实际业务查询接口授权；这是策略标识，不是可以传给hasPermission的权限码 |
| dataScope | inherit_query_data_scope | 沿用实际查询的租户、列表数据范围、对象关系及关联查询约束；不是ALL、SELF等用户数据范围结果 |
| sensitive | boolean | 除standard外为true；仅元数据分类，不自动隐藏、不授予读取权限，不保证查询本身不会产生信息推断 |
| sensitivity | standard/personal/financial/free_text | 逐字段显式登记，禁止按名称前后缀自动猜测 |
| sortable | boolean | 全部为false：通用高级筛选协议不接受fieldKey排序；个别列表独立sortField能力仍归该列表API |
| deprecated | boolean | 全部为false：本次未退役任何字段。未来弃用必须同步模板兼容与迁移说明 |
| optionSourceType | none/business_contract/business_api/dictionary/visible_users/visible_departments/catalog_dates | 普通值输入、固定业务契约选项、所属业务API、字典、授权人员、授权部门、可作差日期字段 |
| declaredOptionSource | string或null | 保留最初登记的来源；即便人员/部门已填options也不丢失visible-users/visible-departments；none/固定选项为null |

## 页面及权限语义

| scene | 已知正确接入的pageKey |
|---|---|
| lead | lead_management、lead_claim_pool、lead_aging_pool、subordinate_sales_leads |
| order | sales_order_management、sales_order_approval:registration、sales_order_approval:finance、sales_order_supervisor_confirm |
| lead_appeal | lead_appeal |
| duplicate_review | lead_duplicate_review |
| registration | registration_pool |
| student | student_my |
| subordinate_sales | subordinate_sales |

页面列表描述两端已有接入的并集，不表示每个pageKey两端均存在。错误使用lead的cashback入口不登记成支持页面；尚未接入的媒体学员也不冒充student_my。财务场景当前无注册字段，不因补元数据而产生字段。配置页的模板编辑器不是业务查询页面，不加入supportedPages。

`supportedScenes/supportedPages`描述字段能力上限，不是当前用户授权列表；客户端不得据此生成导航、推断角色或放行操作。真正目录功能权限仍由AdvancedFilterController现有注解执行；真正查询权限和数据范围仍由各业务接口执行。元数据不维护另一套权限码列表，避免与授权注解漂移。

## 敏感性登记

- personal：姓名、账号、手机、微信、人员/部门引用及部分个人属性。
- financial：订单金额、金额指标、支付方式/时间、凭证存在性等财务属性。
- free_text：备注、裁决意见、学员特殊要求、教材联系等可能混入个人信息的自由文本。
- standard：其他已登记业务属性；不等于公开数据，仍必须受原有查询授权保护。

当前118个字段：personal 31、financial 7、free_text 11、standard 69。各字段在Provider调用中显式给出分类；工厂没有省略分类的重载，因此新增字段必须作出分类选择。此分类仅服务目录说明与未来按权限展示设计，不改变当前允许查询的范围。

## 动态字段与选项解析

`duration.diff`仅返回当前scene及其已知页面，操作符、日期候选来自该scene实际目录；类型为duration、optionSourceType为catalog_dates，不标记支持没有两个日期字段的场景。

人员、部门填充统一使用 `FieldVO.withResolvedOptions`，保留全部元数据、declaredOptionSource和sourceType，只更新options、optionsState和已解析后的optionSource。空人员选项仍为empty，不回退成全系统用户。字典加载仍由现有客户端按dict来源执行。

## 示例

```json
{
  "fieldKey": "lead.ownerDeptId",
  "label": "负责人所属组织（含下级）",
  "group": "归属与人员",
  "valueType": "select",
  "operators": ["in", "not_in"],
  "optionSource": null,
  "options": [],
  "optionsState": "empty",
  "optionsErrorCode": null,
  "supportedScenes": ["lead"],
  "supportedPages": ["lead_management", "lead_claim_pool", "lead_aging_pool", "subordinate_sales_leads"],
  "permission": "inherit_query_authorization",
  "dataScope": "inherit_query_data_scope",
  "sensitive": true,
  "sensitivity": "personal",
  "sortable": false,
  "deprecated": false,
  "optionSourceType": "visible_departments",
  "declaredOptionSource": "visible-departments"
}
```

这是已解析但没有可选部门的契约示例，不是实时账号响应。序列化配置可能省略null值；两端将这些属性声明为可空或可选。

## 兼容与验收

响应仍保留fields/relativeDateOptions及原字段属性；查询条件、模板、选项值和SQL Binding不变。Workbench和Admin同时更新协议类型；现有组件通过展开字段对象保存元数据，暂不新增敏感字段隐藏、排序控件或页面裁剪。

后端测试覆盖全目录元数据、场景与Binding一致、时间作差、人员/部门填充保留元数据、JSON无SQL泄露，以及原有条件/模板/权限回归。两端类型检查和Workbench筛选测试验证消费兼容。生产构建和视觉检查不适用于本次仅协议元数据扩展；部署后真实鉴权HTTP响应仍需在可用账号下验收，不能将单元测试当作运行态证明。

实现：[元数据投影](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterMetadata.java)、[字段描述符](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterFields.java)、[响应VO](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/advancedfilter/vo/AdvancedFilterCatalogRespVO.java)。

## 场景查询修正后的增量

2026-09-23：新增 32 个财务静态字段，当前总数 150；财务场景页映射为 `cashback`、`withdrawal`。订单增加管理端 `sales_order_approval` 页面标识。新增字段逐项定义见[字段目录财务增量](advanced-filter-field-catalog.md#财务场景新增目录2026-09-23-查询契约修正)。日期字段自动参与同场景时间作差；元数据不增加独立字段权限或排序能力。

## 2026-09-23 选项来源更新

`lead.status`、`lead.assignmentStatus`、`appeal.status` 的 optionSourceType 改为 dictionary，原始来源分别是既有字典类型，内嵌 options 为空，由 System 字典接口加载。其余常量/状态契约和计算选项的来源依据见[字段目录来源记录](advanced-filter-field-catalog.md)。已解析人员/部门列表即使为空，也不会重新查询宽泛实体列表；无法识别的客户端来源显示错误而非假装空结果。


产品/SKU选项来源新增 `business_api`：`declaredOptionSource` 为 `product-catalog:spu` / `product-catalog:sku`。后端通过商品模块已有服务解析引用与标签，实际响应的 `optionSource` 置空；空结果为 `empty`，数据源异常使目录请求失败并走原有重试。两端联合类型已同步，不增加客户端商品目录请求或静态选项。
