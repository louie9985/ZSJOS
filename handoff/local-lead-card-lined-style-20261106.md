# 关联客资卡片细分隔线样式

## 登记
- 环境：local（ZSJOS_AGENT_ENV 未设置，环境标记不存在）；分支 main；工作区 D:/ZSJ-OS；基线 e23674d600e61ac87f3b6c8a13a8d81051d7b6a0。
- 工作流 ID：local-lead-card-lined-style-20261106；所有者 Codex /root；目标分支及集成顺序：None。
- 用户确认：采用已预览的细分隔线样式，直接展示全部字段，以已有卡片为基础调整样式。
- 范围：Workbench 共享 OrderLeadSelectionModal 的卡片 CSS、ui-guidelines.md、docs/api/zsjos-sales-order.md、本记录；独占验证目录 frontend/workbench/tmp/lead-card-lined-style/。
- 非目标：不变更卡片网格布局、字段顺序、字段对齐、业务/API/权限或 Admin 样式；不提交、推送、部署或操作数据库。
- 依赖及并发：复用现有 React/Ant Design 和主题 token；原关联工作流已释放所有权。保留目标 CSS 中既有未提交的 span-2 dd 右对齐修复，以及其他全部未提交文件。
- 文档冲突：旧 API 文档描述两端放大上浮；依照用户本次确认，更新为 Workbench 细分隔线及稳定选中态，Admin 保留原有呈现说明。
- 验证计划：既有卡片与样式守卫测试、差异检查；使用已运行本地 Vite、实际共享组件的独立 fixture，检查一/多候选、选中/未选中、禁用、完整字段及桌面/手机/暗色样式。仅本地隔离渲染验证，不作为真实账号录单验收。

## 交付
- 已应用细分隔线：保留原卡片网格、字段顺序和全部档案，字段间浅色 1px 横线、末行无底线；16px 主题内边距、字段 6px 上下内边距、字段 gap 0。标题间距收紧。候选区外侧留白使用现有间距变量，默认上下从 28/20px 收至 24/16px，列间距仍为 24px。
- 普通卡片轻阴影；选中仅主题主色细边框及浅色外环，删除放大、上浮和强光晕。原有 span-2 右对齐改动保留。影响 Workbench 历史客户复购页与录单选择弹窗，不改变 API 或 Admin。
- 更新工作台 UI 规范与订单 API 文档的展示描述。
- 验证：既有 OrderLeadSelectionModal 2/2 测试通过；全局样式守卫 26/30 通过，4 个失败仍来自其他文件（class-statistics-preview、layout、calendar-time-grid、content-review-attachments、calendar-colors、bpm-approval-center）；本次 CSS 已不在失败明细。未修改这些无关文件。git diff --check 通过。纯 CSS/文档修改，无新增依赖、路由或打包输入，无需扩展类型检查及生产构建。
- 实际共享组件的本地 Vite fixture，经 ego-browser 空间 43 验证 1440/390 宽度、一/三候选、每卡 12/13 个字段直接可见、无折叠控件、末行无分隔线、兼职归属右对齐、无页面横向溢出；切换选中前后尺寸位置完全相同，鼠标/方向键选择和禁用通过；弹窗及暗色渲染检查通过。desktop.png、mobile.png、dark.png 已逐一查看。空间已 finish。
- 验证脚本第一次将缩放下 0.8px 的边框误判为非 1px，第二次将 Ant Design 隐藏缓存弹窗计入可见卡片；修正检查口径后通过，产品代码无相关故障。证据：frontend/workbench/tmp/lead-card-lined-style/browser-01.mjs 至 browser-04.mjs 及截图。
- 未执行真实账号订单提交、数据库变更、服务操作、部署、提交或推送。保留工作区其他改动，所有权释放。

## 续办登记：恢复选中放大
- 用户纠正需要保留原有选中放大效果，覆盖上一轮“尺寸位置稳定”的展示决定；环境仍 local，复用原工作流、所有者、文件范围及工作区。重新领取上述 CSS、两份展示文档、本记录及隔离验证目录。
- 恢复原 `translateY(-6px) scale(1.025)` 与 transform 过渡，保留细分隔线、浅外环和完整字段；验证桌面/手机实际选中与切换效果及无横向溢出。

## 续办交付：恢复选中放大
- 已恢复原 1.025 倍缩放及向上 6px，主题过渡、细分隔线及新间距保留；同步 UI/API 展示文档，以上更新替代前次选中位置稳定说明。
- ego-browser 空间 46 使用实际共享组件 fixture 验证：1440px 多候选初始和切换选中后仅所选卡片 transform 为 matrix(1.025, 0, 0, 1.025, 0, -6)，390px 单候选同样生效，均无页面横向溢出、全部 12/13 字段及行分隔线保留。scale-desktop.png、scale-mobile.png 已查看；空间已 finish。纯样式恢复，未重复运行未受影响的业务测试；差异检查通过。
- 未提交、推送或部署，未改变业务数据；所有权释放。
