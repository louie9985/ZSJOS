# ZSJOS 课程 SPU/SKU 配置接口

课程目录按租户隔离，结构为“任意层级分类（最多 10 层）→ 叶子分类 → 课程 SPU → SKU/属性组合”。`zsjos_product` 作为 SPU 主表，数据库中的 `product_ref` 是稳定 SPU 引用；具体价格只配置在 SKU，并在客资提交时保存价格快照。

管理端保存按钮和分类、SPU、SKU 的状态切换、删除、缺失组合生成操作在请求完成前必须禁用对应操作。列表操作按业务对象和动作独立锁定，避免快速重复点击发出相同写请求，同时不阻塞其他对象的正常管理操作。

## 管理端

- `/admin-api/zsjos/product`：SPU 创建、编辑、分页、启停和删除。
- `/admin-api/zsjos/product/category`：通用分类树管理；父分类为空或 `0` 表示根分类，移动分类时服务端原子更新整棵子树深度。`GET /tree?status=0|1` 按状态返回分类树；过滤后父节点不在结果中的分类作为当前结果的根节点返回，保证停用分类始终可见。
- `/admin-api/zsjos/product/sku/attrs?spuId=`：查询或保存 SPU 销售属性。
- `/admin-api/zsjos/product/sku/list?spuId=`：查询 SPU 下的 SKU。
- `/admin-api/zsjos/product/sku/create|update|delete|update-status`：SKU 管理。所属 SPU 及完整分类链启用时，手工创建默认启用；普通更新不修改状态，后续启停只通过 `update-status` 完成；SKU 创建后不可更换所属 SPU。
- `/admin-api/zsjos/product/sku/generate?spuId=`：根据属性笛卡尔积生成缺失 SKU；所属 SPU 及完整分类链启用时，新生成 SKU 默认启用、价格为 0。服务端在分配组合列表前校验笛卡尔积数量，配置 `zsjos.product.sku.max-generated-combinations` 默认限制为 500，非正配置和超限均返回稳定业务错误。

SPU 只能挂在叶子分类；已挂 SPU 的分类不能新增子分类，有子分类的分类不能挂 SPU。同一 SPU 下属性组合唯一。属性保存、SKU 手工创建、组合生成和普通更新都在事务内锁定所属 SPU，避免并发请求写入重复组合。SPU 或任一祖先分类停用后，其 SKU 不进入员工端目录；被 Lead 引用的 SPU/SKU 禁止删除，应改为停用。

分类采用逐级停用，不做级联：存在启用子分类或启用课程时不能停用当前分类；应先停用课程，再从叶子分类向父分类逐级处理。启用也只影响当前分类，父分类仍停用时不能启用子分类。专用状态接口和分类编辑接口执行同一套状态迁移校验，不能通过编辑保存绕过。Admin 与 Workbench 的主分类区只查询启用分类，已停用分类在独立折叠树中查看，并按按钮权限提供编辑、启用和删除；新课程分类选择器只使用启用分类树。

## 客资端

产品 SKU 响应和业务历史投影增加结构化 `specs`：`attrKey/attrName/value/label/labelMissing`。
新选择保存名称和值标签快照；同一产品与 SKU 的资料修改保留原快照。旧记录缺失标签时显示原始字段和值并标记“历史标签缺失”，不回查当前配置伪造历史含义。
Admin、Workbench、H5 使用各自的展示组件，显示“字段：值”；规格分列的表格以列标题作为字段名。
只有考期允许部分规格条件。客资、资料修改、成交等既有完整 SKU 匹配、价格、提交和未明确分支不变。
完整入口与验收清单见 [产品规格展示清单](../frontend/product-spec-presentation.md)。

- `GET /admin-api/zsjos/lead/product/catalog` 返回 `{ categoryTree, spus, skus }`。该只读目录同时服务“提交客资”和“修改基础信息”，调用者持有 `zsjos:lead:submit` 或 `zsjos:lead:update` 任一权限即可读取；保存动作仍由各自命令权限和对象权限独立校验。分类树可直接作为 Cascader 数据源；SPU 返回 `categoryId/categoryName/categoryPath`，并暂时保留旧两级字段兼容。
- `POST /admin-api/zsjos/lead/create` 使用 `intendedProducts` 提交已经点击“添加意向课程”的项目。

请求示例：

```json
{
  "intendedProducts": [
    {
      "spuRef": "course_xxx",
      "spuUnknown": false,
      "skuRef": "sku_xxx",
      "skuUnknown": false,
      "primary": true
    }
  ]
}
```

服务端忽略前端课程名称、分类名称和价格，重新校验 SPU/SKU 及完整祖先链后，写入 `category_id`、`category_name_snapshot`、`category_path_snapshot`、SKU 名称和 `price_snapshot`。旧的 `level1_*`、`level2_*` 仍兼容填充，旧客户端的 `products[].productRef` 继续兼容为 SPU 引用。

分类或课程后续停用、改名或在满足引用约束时删除，都不改变已提交客资、订单及其他业务记录保存的名称、分类路径和价格快照；历史展示不得回查当前产品字典覆盖快照。

资料修改时，未改变的产品选择保留原名称、分类路径和规格标签快照；价格仍遵循该修改流程原有的实时校验和取价规则，不因展示快照保留而固定为旧价格。未修改的历史记录不主动重算。

SKU／客资目录响应的 `specs` 包含 `attrKey/attrName/value/label/labelMissing`；客户端优先使用该结构，旧字段继续保留。配置缺失的原字段不丢弃，明确标记标签缺失。SKU 列表每个产品只读取一次规格元数据；考期选项以每批 200 个产品批量读取规格、值与有效 SKU，分类路径由单次分类集合解析，不逐 SKU／产品重复查询。

考期保存／发布和产品、规格、SKU、分类写入遵循同一事务锁顺序，详见考期 API。锁内重新校验不改变客资及成交的必填、匹配、金额或提交规则。仅考期允许部分规格条件。

任意层级改造的增量脚本为 `script/sql/zsjos_product_category_tree.sql`，依赖之前的产品、分类及 SPU/SKU 脚本，不自动执行。
