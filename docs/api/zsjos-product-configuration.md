# ZSJOS 课程 SPU/SKU 配置接口

课程目录按租户隔离，结构为“任意层级分类（最多 10 层）→ 叶子分类 → 课程 SPU → SKU/属性组合”。`zsjos_product` 作为 SPU 主表，数据库中的 `product_ref` 是稳定 SPU 引用；具体价格只配置在 SKU，并在客资提交时保存价格快照。

## 管理端

- `/admin-api/zsjos/product`：SPU 创建、编辑、分页、启停和删除。
- `/admin-api/zsjos/product/category`：通用分类树管理；父分类为空或 `0` 表示根分类，移动分类时服务端原子更新整棵子树深度。
- `/admin-api/zsjos/product/sku/attrs?spuId=`：查询或保存 SPU 销售属性。
- `/admin-api/zsjos/product/sku/list?spuId=`：查询 SPU 下的 SKU。
- `/admin-api/zsjos/product/sku/create|update|delete|update-status`：SKU 管理。
- `/admin-api/zsjos/product/sku/generate?spuId=`：根据属性笛卡尔积生成缺失 SKU；新生成 SKU 默认停用、价格为 0，管理员设置价格后启用。

SPU 只能挂在叶子分类；已挂 SPU 的分类不能新增子分类，有子分类的分类不能挂 SPU。同一 SPU 下属性组合唯一。SPU 或任一祖先分类停用后，其 SKU 不进入员工端目录；被 Lead 引用的 SPU/SKU 禁止删除，应改为停用。

## 客资端

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

任意层级改造的增量脚本为 `script/sql/zsjos_product_category_tree.sql`，依赖之前的产品、分类及 SPU/SKU 脚本，不自动执行。
