# local-version-latest-20261116
- 环境：local（环境变量和环境文件均未设置）；owner：Codex。
- 目标：版本更新自动弹窗仅展示最新发布且未知悉的记录，保留历史查询与未读计数。
- 非目标：后端、数据库、权限、部署、其他已有改动。
- 分支：main；工作树：D:\ZSJ-OS；基线：21631a8a98a62c41a3c71596a6739e03a535c3b6。
- 所有权：本记录、frontend/workbench/src/components/versionUpdates/VersionUpdateEntry.tsx、docs/api/version-updates.md。
- 依赖：既有 my-page 发布时间倒序；目标分支与集成顺序：None。
- 验证：typecheck、定向差异检查、浏览器可用性与交互验证。

## 交付
- 已改为查询全部可见发布记录的第一条，仅该记录未知悉时自动弹出；旧记录仍可在历史中查看，原未读计数保留。
- 已核实服务端按 publish_time、id 倒序，接口与权限无变更；同步 API 交互说明。
- npm --prefix frontend/workbench run typecheck 通过；限定文件 git diff --check 通过。
- 浏览器交互未验证：本机默认工作台端口 5173 未监听，未启动服务；真实账号和重新登录流程仍待运行环境验收。
- 未部署、未提交、未修改数据库。所有权释放。
