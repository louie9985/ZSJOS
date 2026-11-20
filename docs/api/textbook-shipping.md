# 教材邮寄

独立菜单 `/zsjos/textbook-shipping` 使用 `zsjos:textbook-shipping:query`。默认只显示当前用户是正式成交负责人（`formalSalesUserId`）的生效订单，分页总数和关键词筛选均在此范围内。没有负责人的历史订单仅在全部范围显示。管理端和工作台嵌入页共用页面及 API。

| 角色管理中配置的权限 | 作用 |
| --- | --- |
| 教材邮寄 | 查询本人负责成交的订单及邮寄信息 |
| 查看所有邮寄信息（`zsjos:textbook-shipping:view-all`） | 在页面查询权限基础上，扩大到本租户全部生效订单 |
| 填写邮寄信息（`zsjos:textbook-shipping:update`） | 在本人已有的可见范围内填写、修改快递公司及运单号 |

`GET /admin-api/zsjos/textbook-shipping/page` 接收 pageNo、pageSize、keyword，total 为订单数。同一订单按各明细绑定表单最新正式修订中的教材地址全文、报名姓名及手机号分组，缺地址归入待补组。展开可看商品、规格和数量。主表邮寄信息列逐项显示快递公司与运单号，同址商品可填写同一运单，也可分寄。历史未填写的明细显示待填写。

`PUT /admin-api/zsjos/textbook-shipping/{orderId}/items/{itemId}` 接收 carrier、trackingNo（均必填，最多 100 字）及列表中的 version（首次为 0）。服务端锁定订单复查成交归属及状态，限定租户、订单、商品明细和版本更新；发生冲突时刷新重填。运单号按文本保存。V382 创建字段及菜单按钮元数据，由管理员在角色管理中独立分配。
