# 学员收集表校验反馈

- Environment: local (环境变量及环境文件未设置)
- Workstream/owner: local-collection-validation-20261103 / Codex
- Goal: 字段错误就近提示并定位，提交失败弹窗提醒。
- Non-goals: 后端校验规则、数据库、部署、其他工作流。
- Branch/worktree/base: main / D:\ZSJ-OS / f7abc09b91d30e140ef8bf4895b4b8b8181f0681
- Ownership: frontend/h5/src/pages/studentInfo/index.vue; docs/api/student-information-collection.md; 本记录。
- Dependencies: 现有 Vue/Vant 及收集接口；无新增依赖。
- Integration: None
- Verification: H5 类型检查、范围 diff；尝试浏览器校验。

## Delivery
- 已增加字段容器定位、展开后 nextTick 滚动、字段旁错误文案及通用提交错误弹窗；保留填写内容。
- vue-tsc --noEmit -p tsconfig.json 通过；git diff --check 通过。
- 浏览器工具可连接，但未建立可用填写链接及隔离测试数据；桌面/移动真实交互未验证，滚动与焦点效果仍需浏览器验收。未部署。
- 所有权已释放。
