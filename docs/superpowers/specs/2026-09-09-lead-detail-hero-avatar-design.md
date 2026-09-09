# Lead Detail Hero Avatar Design

## Goal

在 Workbench 客资详情和学员详情的 `lead-detail-hero` 最左侧显示业务主体头像，复用现有 `SubjectAvatar`，不引入新的数据接口或权限逻辑。

## Design

- 客资详情使用 `lead.id` 作为头像稳定 seed。
- 学员详情优先使用 `service.leadId`，没有时使用 `student.personId`。
- 头像固定为 44px 圆形，标题与现有提醒信息继续作为 Hero 的主要内容。
- 真实头像字段当前不存在；`SubjectAvatar` 的确定性生成头像作为当前展示来源，并保留其既有失败回退能力。
- 桌面端保持头像在内容最左侧、标题居中；移动端沿用 Hero 现有纵向布局，避免提醒内容与头像重叠。

## Scope and Non-goals

只修改 Workbench 的两个 Hero 组件和对应布局样式。 不修改后端 DTO/API、数据库、权限、菜单或依赖，不抽取新的共享 Hero 组件。

## Verification

运行 Workbench 测试、TypeScript 类型检查和生产构建，并在桌面与移动宽度检查头像位置、标题截断、提醒内容和无数据 seed 的稳定渲染。
