# 历史学员虚拟订单清理

## 范围与依据

用户确认：历史补录学员不应依赖虚拟销售订单。无真实订单、订单／商品不明确以及真实商品已有服务关系时，保留独立学员服务，不随意重绑或合并服务。仅四个明确补录批次 seed-liudandan、seed-students、seed-llyilin、seed-batch4，租户1，零金额且无审批轮次的109 笔订单（107 effective／2 terminated）。真实零金额订单不按金额删除。

学员列表原本通过 Person和服务关系授权查询，没有统一的订单存在限制；强制关联来自 zsjos_service_relation 的三个 NOT NULL 列以及 seed_student.py 的订单链路生成。V310将 order_id、order_item_id、registration_case_id 改为可空。新补录脚本直接创建 Person、独立服务及协作组，不创建订单／商品／报名单，不再把虚构880元作为未知课程价格。

MyStudentServiceImpl 过滤空订单／商品ID并保护空键映射；详情无订单商品时使用 service_snapshot。联系上下文、联系任务和通知查询不再用空订单ID调用Mapper。前端课程选择、课程卡及订单号列显示“独立服务”，不显示 undefined。保留所有既有授权、负责人、协作组、联系记录、账号、定位卡和交付资料。

## 清理结果

109笔虚拟订单、109条虚拟商品明细、109条虚拟报名单仅逻辑删除，完整原值保存在私有计划和 zsjos_data_repair_backup。109条服务关系全部保留。

- 108 条解除虚拟订单／商品／报名单引用，保留原有课程服务快照。
- OD202609231629388801 的服务唯一对应 OR202609200025，且真实商品无既有服务，改为真实订单／商品／报名单引用及真实商品快照；不另建订单或服务。
- 多候选、课程不一致及已有重复服务的全部独立保留。包括 OD202609201400090027、OD202609201407340037、OD202609201539519002、OD202609201407340092、OD202609231629388800、OD202609231629388802。不因 Person 相同就将不同课程服务关联到订单。

## 执行顺序及恢复

前置V309（其他工作流所有）；本任务的V310__student_service_without_virtual_order.sql只进行兼容性扩容及台账登记，不清业务数据。同步更新当前期望schema及fresh baseline的三个列。MySQL DDL隐式提交，失败后检查实际nullability和两张台账；重复执行检查结构，不只看版本。

先部署已验证的空关联读取后端，再按已审核私有清理计划执行。生产需单独授权，不得只清数据而保留旧读代码。

```powershell
python script/sql/mysql/tools/student_virtual_order_cleanup.py prepare --plan <Git忽略或私有路径>
python script/sql/mysql/tools/student_virtual_order_cleanup_test.py
python script/sql/mysql/tools/student_virtual_order_cleanup.py apply-local --plan <同一计划> --expected-sha256 <摘要> --readers-deployed
python script/sql/mysql/tools/student_virtual_order_cleanup.py verify --plan <同一计划> --expected-sha256 <摘要>
```

prepare只读查询并创建私有文件；apply-local明确限定本地Docker连接，要求已部署新读取器；verify只读。完整前后镜像记录服务关系原始ID及全部字段；sha256绑定计划。执行前重新扫描订单／商品／报名单的关系引用、业绩事实和业务JSON引用，意外引用或原值不匹配则停止。唯一真实关联也保存依赖前镜像并重新验证。

写入事务使用命名锁和SERIALIZABLE，先解除或重绑服务关联，再逻辑删除报名单／商品／订单；无物理删除、不重放通知和BPM。异常全部回滚，重复执行0变更。回退必须先确认所有行仍等于after-image，然后按逆序恢复before-image；保留schema可空，不删除备份或台账。不要强制覆盖后续服务编辑。

## 本地验证记录（2026-10-10）

- 最终私有计划：backups/mysql/student-virtual-cleanup-20261010/plan-final.json，SHA256 `233b0af57ef252530f96ea91c9da912fe61b396806efd887fcc08b67a42c46cf`。
- 隔离schema v310_20261010184304、真实数据副本 v310_20261010184304data：前置失败、台账失败不记成功、DDL部分恢复、重复、空关联插入、原关联保留、数据事务回滚、重放0变更、非目标数据保留、首购计数减少107通过。
- 后端67项测试通过：MyStudentServiceImplTest 18、MediaStudentServiceTest 14、StudentContactServiceImplTest 35；含独立服务课程快照读取。Workbench typecheck通过。服务打包成功。
- 用户单独授权本地重启。旧48080进程占用JAR导致首次repackage失败；停止已确认的本地后端后打包成功，沿用local配置启动新进程，正常响应接口。
- 本地清理436项行更新，重放0，after-image无差异；effective订单392，未删除虚拟订单0，服务109均保留；所有非空服务订单引用无悬空。参见私有 acceptance.json。
- 完整新环境初始化尝试失败在既有V298“requires the ten enabled invalid-reason dictionary entries”，没有执行到V310；未改动既有V298。独立V310及实际数据测试已通过，完整fresh链条未通过，不能宣称发布验收完成。
- 浏览器打开localhost:5174/zsjos/my-students仍为登录页，本会话无认证态，真实页面交互未验收。仅源代码、类型、单元测试和数据库运行验证完成。生产未操作、未提交推送。
