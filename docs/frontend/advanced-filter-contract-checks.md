# 中视简高级筛选自动化完整性检查

## 运行

在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File script/verify-advanced-filter-contract.ps1
```

脚本依次运行后端 AdvancedFilter 测试、两端契约测试、Admin 与 Workbench 类型检查；失败立即退出，日志位于 `output/advanced-filter-contract-*.log`。不启动服务、不访问真实账号或修改数据库、不安装依赖。仅复用当前 Maven、npm、pnpm 和已有测试设施。

`-SkipTypecheck` 适合只调整测试清单后的快速回归，不代表类型检查通过。`-RequireFullCoverage` 用于完整盘点验收：只要清单 `pending` 仍有未关闭项，脚本即失败。普通回归通过仅表示已登记页面没有退化，不能据此宣称全仓库字段已经补齐。

## 七项检查与边界

| 要求 | 自动验证 | 证据位置 |
|---|---|---|
| 页面目录非空 | 人工维护的16个pageKey与后端元数据双向对齐；前端解析React/Vue所有统一筛选入口，新增、遗漏或错场景均失败 | AdvancedFilterCompletenessTest；AdvancedFilterContract.test.ts |
| 每个字段可校验 | 遍历实际目录的每个场景、每个字段及每个声明操作符，通过真实validate；包含运行时duration.diff | AdvancedFilterCompletenessTest动态用例 |
| 操作符可编译 | SQL场景检查真实编译结果中的列/表达式、比较符、参数引用、租户及关联删除条件；下属销售场景检查真实内存匹配结果 | 同上；原有AdvancedFilterServiceTest继续验证同一行、否定、AND/OR、日期与输入错误 |
| 选项权威来源 | 外部选择器必须声明受支持的字典/可见人员/组织/商品服务来源，且不允许静态回退；业务状态和技术计算选项必须逐字段登记所属契约文件，禁止不明来源新增选项 | 清单embeddedOptionAuthorities；选项测试及可见人员测试 |
| 关键业务字段覆盖 | 每个页面映射到独立维护的list/detail/export必需fieldKey；删除任何已要求的字段都会失败。无独立导出的场景必须注明原因 | 清单scenes与pages |
| 无权限不可见/不可用 | 使用真实Spring PreAuthorize拦截器执行无权限与错场景请求；目录拒绝时不得加载字段/选项；所有登记查询Controller的search-page入口都必须在进入业务代码前拒绝无权限请求。禁止字段与外场景字段不能通过校验 | AdvancedFilterAccessContractTest；CompletenessTest；既有可见范围测试 |
| 两端共用契约 | 文本/选择/数值/日期/时间作差五类JSON报文先由Java序列化对照，再分别用Admin和Workbench实际类型声明编译；非法类型样例必须被拒绝。场景、类型联合、操作符标签和具体页面绑定也双向对照 | AdvancedFilterContract.test.ts；两端typecheck |

目录完整性清单位于[advanced-filter-contract.json](../../backend/yudao-module-zsjos/src/test/resources/advanced-filter-contract.json)。它是测试规格，不是生产选项、菜单、权限或第二套字段目录；禁止根据当前目录自动重写必需字段以消除测试失败。

权限检查遵循目前真实契约：字段继承查询授权/数据范围，人员候选由授权范围裁剪；`sensitive`是敏感性分类，不是独立字段权限。当前没有细粒度字段授权配置，本套测试不声称验证了不存在的字段权限系统。SQL编译检查与方法拦截测试也不等同于真实MySQL执行、HTTP端到端或部署后验收。

## 维护方式

- 新页面：在pages登记具体两端文件、scene、pageKey；在scenes补关键字段。React动态pageKey需明确允许的展开值；不能把任意表达式当成已覆盖。
- 新字段：自动进入后端全部已声明操作符测试；业务关键字段还应加入独立必需清单。目录和清单不能仅改数量来规避失败。
- 新选择器：外部来源需扩展真实解析器及空/错/权限测试；固定业务状态或技术计算选项必须注明所属契约。字典实际内容仍由管理员维护，本测试不造业务选项。
- 新类型/操作符：更新共享wireTypes/operatorsByType、后端真实编译器和两端类型/控件；没有编译断言的操作符会直接失败。
- 新导出：补export关键字段，并测试请求向导出业务查询传递高级条件；字段存在性不能替代导出链路验证。
- 已知缺口：先完成实现与验证，再关闭pending。审批轮次、动态字段开放策略和未接入媒体页面仍沿用[盘点矩阵](advanced-filter-inventory.md)，不会因为增加自动化检查而自动变成“完成”。
