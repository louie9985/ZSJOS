# 中食健课程服务定位卡与账号应用

2026-09-17。Workbench 和学员 H5 消费本契约；Admin 仅维护既有模板，不消费新增业务接口。数据库和运行服务尚未启用本变更。

## 归属与操作

定位卡正文仅有一份可编辑来源：学员课程服务主卡。完成定位访谈后由责任编导填写，不选择账号。无账号也可保存、提交、运营审核和学员确认。历史多卡保留，责任编导明确选主卡；不会按更新时间自动合并或删除。选定不可再次切换，其他卡历史只读。

底部操作为取消、保存草稿、保存并关闭、保存并提交审核。草稿允许缺必填项，提交验证完整性及有效责任运营。输入、选素材、选文件、下载和关闭不会写草稿。新附件暂存本地；手动保存按创建草稿（仅新卡）、逐个上传、保存关联、提交审核的顺序执行，使用最近返回版本。部分失败保留成功文件 ID 和失败 File，重试不重复上传成功文件。保存失败不提交，提交失败明确说明草稿已保存。保存期间禁止重复提交与编辑。遮罩及 Escape 不关闭，脏数据关闭需确认放弃。

JSON 导入仅填写表单。历史提交导入需先保存主卡草稿，按钮为“导入并保存”，仅允许同学员同课程服务，沿用字段和素材、附件、字典快照映射。

## 接口（ADMIN 前缀 /admin-api）

| 方法与路径 | 契约 |
| --- | --- |
| GET /zsjos/positioning-card/service-overview?serviceRelationId= | 返回 masterCardId、canSelectMaster、canCreate、canSubmit、candidates、current、effective、history；卡含 availableActions、submissionId 和反馈意见 |
| POST /zsjos/positioning-card/select-master?serviceRelationId=&cardId= | create 按钮权限及课程服务 read；必须为服务责任编导且所选卡属于本学员、本服务及本人；重复选同卡可重试 |
| GET /zsjos/positioning-card/application-options?accountId= | query 权限及账号 read；返回 version、submissionId、canApply、newerAvailable、candidates；候选限定同学员同服务且有卡 read 权限的 confirmed/superseded/student_agreed 提交 |
| POST /zsjos/positioning-card/apply | JSON `{accountId,submissionId,version,idempotencyKey}`；version 是应用关系并发版本，尚未应用为 0；成功递增。需 apply 按钮权限及账号责任编导/运营对象权限、目标卡 read |
| GET /zsjos/positioning-card/account-overview?accountId= | effective 返回应用关系对应完整快照；兼容 current=null、history=[] |

保留现有 draft、上传、读取附件、submit-review、operator-approve/reject、student-link、start-revision 路径。创建时旧 accountId 入参不再绑定。提交快照 accountId 可空，历史值保留。同一次版本提交重复调用不产生第二个审核轮次。真实并发仍报 1900014003；权限不符使用 1900014004；需要主卡选择为 1900014030，已选主卡不能切换为 1900014031。

应用命令在账号行锁内校验当前关系版本及幂等键，记录操作者、时间、原提交和新提交。同键同请求重试不重复写；同键不同目标拒绝。一个账号一条当前关系，同一提交可被多个账号使用。此次不提供解除应用。

新版确认不覆盖旧确认资格、不改应用关系。账号概览和拍剪工单创建读取应用关系；新工单冻结 submissionId 和字段快照，已有工单不随账号换版变化。旧学员详情中的账号 taskLine/effective 标志也使用应用关系；独立草稿和全部提交以 service-overview 为准。

学员确认链接仍采用已有令牌校验与失效机制，展示课程服务名称、定位卡号及完整字段快照。字典使用历史标签、素材使用历史标题、附件使用冻结文件名，不按当前字典重解释历史。

## 数据与上线边界

V258__positioning_service_application.sql 以 V257 对应表结构为前置，仅放宽提交 account_id 的非空约束，创建 zsjos_positioning_service_card、zsjos_positioning_application、zsjos_positioning_application_log，并新增 apply 按钮元数据。SQL 使用 utf8mb4，不写 system_role_menu，不代角色授权。

历史单卡服务直接建立主卡关系；多卡服务不自动指定。账号应用回填遵循升级前最新有效提交逻辑（confirmed 或非归档卡 student_agreed），不改原账号、卡和提交内容。初始回填操作人为空，避免捏造人工操作。仅回填缺失关系，重复执行不覆盖人工应用，不随新确认版本移动账号。

启用前备份涉及表，执行 V258，再部署后端及两个消费前端，由管理员配置 apply 权限；不得先启用依赖新表的后端。回退应用时保留新表和历史行，已有空 account_id 提交不允许直接恢复 NOT NULL。已应用脚本在部署环境的兼容与校验和需单独审核。

当前按用户限制不变更正在使用的开发数据库或服务。隔离数据库已验证重复执行、非空历史回填、跨租户同服务编号分离、多卡不选主卡、旧应用保持不变、中文 HEX 与原卡指纹保留；不能替代上线后的真实权限和接口联调。
