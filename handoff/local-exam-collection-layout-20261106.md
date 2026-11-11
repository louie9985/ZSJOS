# 考试资料收集交互调整

- 环境 local（环境变量及环境文件未设置）
- 工作流 exam-collection-layout-20261106，负责人 Codex
- 分支 main，工作树 D:\ZSJ-OS，基线 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c
- 目标 搜索筛选单行、系统主从/表格偏好、内嵌详情及抽屉、收集链接与图片分享
- 范围 ExamCollectionPage.tsx、StudentInfoQrCard.tsx、exam-collection.css、ExamTaskPanels.tsx（仅筛选宽度）、docs/ui-guidelines.md、本记录
- 非目标 后端业务、权限、数据库、发布
- 依赖 已有布局 hook、BusinessTable、报名二维码卡片
- 集成 None
- 验证 typecheck、相关测试、桌面与移动浏览器检查

## 交付

已完成任务搜索/名单搜索与筛选单行组合，接入系统布局偏好，主从右侧详情、表格抽屉，二维码链接/图片分享。StudentInfoQrCard 新增可选文案参数，原报名调用保留默认文案。窄屏采用工具栏局部横向滚动及详情抽屉。

验证
- Workbench typecheck 通过。
- 本地 5174 真实账号只读浏览器验证，搜索与状态坐标同一行，表格管理任务抽屉、主从右侧详情、图片下载成功。复制图片按钮已操作，未独立读取系统剪贴板验证内容。
- 桌面截图已检查。Ego CDP 尺寸覆盖未实际改变 2048px 视口，移动宽度尚未验证。
- 样式与全局布局守卫共 33 项，28 通过、5 失败，失败涉及既有其他页面/样式。BusinessTable entry guard 失败，包含既有动态 tableKey 的 ExamTaskPanels 及其他文件，与本次筛选容器修改无关。
- 未执行数据库、部署或提交发布操作。

所有权于本次交付释放。

## 直接展示二维码调整登记

沿用原工作流与 local/main 执行上下文。按用户纠正，二维码卡片和复制/下载操作直接展示在统一收集链接区域。重新领取 ExamCollectionPage.tsx、exam-collection.css、docs/ui-guidelines.md 和本记录所有权。验证类型检查和浏览器直接可见性。

直接展示调整已交付。typecheck 与限定 diff 检查通过，真实浏览器打开任务后确认二维码 canvas、复制图片与保存二维码图片按钮直接存在，弹窗数量为 0。沿用原有图片生成实现。移动宽度本轮未复验。所有权释放。

## 紧凑二维码布局登记

沿用 local/main，用户反馈卡片撑高详情并挤出名单。范围 StudentInfoQrCard.tsx、ExamCollectionPage.tsx、exam-collection.css、docs/ui-guidelines.md、本记录。增加可选紧凑预览，考试启用，导出继续完整图片。验证 typecheck、真实浏览器区域高度与名单首屏可见性、图片下载。

紧凑布局交付。typecheck 与 scoped diff 检查通过。真实浏览器检查收集区域约 190px，高度 994px 的视口内名单标签位于约 475px，四条名单全部可见。二维码与按钮直接可见，完整品牌图片下载成功。桌面截图已检查，移动宽度本轮未复验。所有权释放。

## 通用链接组件接入登记

沿用 local/main 工作流，重新领取 ExamCollectionPage.tsx、docs/ui-guidelines.md、本记录。将手写 Typography 可复制链接替换为既有 ResourceLink resource 展示，验证组件测试、类型检查与浏览器打开/复制入口。

通用链接调整已交付。ResourceLink 38 项测试与 typecheck 通过，限定 diff 检查通过。真实浏览器确认 resource-link-card、考试资料收集表标题、新标签打开属性、复制链接按钮及二维码同时存在。所有权释放。

## 班主任收集链接和二维码登记

沿用 local/main 工作流，范围增加 components/examCollection/ExamCollectionShare.tsx、ExamPlannerProvider.tsx、test/exam-planner-share.html、test/exam-planner-share.tsx，重新领取 ExamCollectionPage.tsx、docs/ui-guidelines.md、本记录。提取任务管理现有分享区域，班主任通知提醒和跟进窗口复用同一组件。仅使用既有待办返回 URL，验证 typecheck、组件测试、隔离 fixture 浏览器展示与图片下载。无真实通知发送或权限调整。

班主任分享展示交付。任务管理、通知提醒及跟进窗口复用 ExamCollectionShare，显示通用链接、紧凑二维码及复制/下载按钮。跟进窗口筛选保持单行。ResourceLink 38 项测试通过。隔离浏览器 fixture 验证提醒和跟进窗口直接展示、下载图片成功，截图检查通过。未发送真实通知，真实班主任账号通知链路及移动端未验证。所有权释放。

## 完整链接展示登记

沿用 local/main 上下文。范围 ExamCollectionShare.tsx、docs/ui-guidelines.md、本记录。通用 ResourceLink 的显示标题直接使用完整 URL，覆盖任务管理和班主任入口。复用现有长链接换行样式。验证 typecheck 与隔离浏览器完整文本、href 一致性。

完整链接展示交付。typecheck、限定 diff 检查通过，隔离浏览器确认可见地址文本与完整 href 一致，二维码继续展示。所有权释放。
