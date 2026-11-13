# 考试资料异步导出

- 工作流 local-exam-async-export-20261111，owner Codex，环境 local（未设置环境标识），main，D:/ZSJ-OS，基线 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c，集成 None。
- 用户授权接入现有异步导出。范围为 export 服务接口/实现/Controller、考试导出 Controller 和 provider、相关测试、Workbench 考试抽屉与导出页/API、Admin 导出页/API、直接相关文档和本记录。
- 复用任务排队、重试、租约、取消及清理。考试 ZIP 加密存储，业务下载重新校验所有者和任务权限。保留既有导出配置、预览指纹及命名。无新依赖、SQL 或权限数据变更。
- 验证专项后端测试、双端静态检查、前端请求回归及浏览器交互。真实服务重启部署需单独授权。

## 交付

- 考试资料新增 /material-export/tasks 异步创建入口与 exam_collection provider，复用通用导出任务调度、租约、取消、重试、过期和终态文件清理。同步入口保留兼容。
- 私有 ZIP 经现有材料加密器保存为随机 .bin，新增认证下载接口重新检查任务所有者、类型权限、收集对象权限、有效期和代操作上下文；签名 URL 接口拒绝该私有类型。已有台账导出维持原路径。
- Workbench 点击“提交异步导出”后关闭抽屉并提示任务入口。Workbench/Admin 列表识别考试资料并自动刷新阶段状态，使用认证下载。显示阶段状态，未加入百分比进度。任务列表沿用现有 export:query 菜单配置。
- API 文档同步，未新增依赖、SQL 或角色授权。产物仍使用现有受限 byte[] 通道和单次 2000 附件/512MB 输入限制。
- Maven 六组专项测试共 41 项通过（0 failures/errors），覆盖现有导出、加密存储、私有下载拒绝签名、归属/对象权限/过期拒绝、预览指纹传递和临时 ZIP 清理。Workbench typecheck、2 项请求回归及 scoped diff 检查通过。
- ego-browser 空间 90 使用真实 Workbench 组件和隔离 mock adapter 验证预览→提交→抽屉关闭→自动更新可下载→下载触发，阻断业务网络请求，已关闭空间。临时夹具 frontend/workbench/tmp/exam-async-fixture.html/.tsx。
- Admin 类型检查唯一报错为已有 src/views/mes/pro/task/edit/index.vue:86 handleRefresh 未使用。Admin 浏览器下载尚未实测。真实后端新版本运行、561 附件端到端耗时及部署验收尚未执行，需加载新后端后验证。未重启服务。
- 所有权释放。Ego Lite 提示有更新，继续沿用当前版本完成验证。
