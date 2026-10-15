# 兼职邀请码易读字符集

## Registration — 2026-10-15
- ID: local-partner-invite-readable-20261015
- Environment: local (fallback).
- Goal: 新邀请码前四位大写字母排除 I/O/L，后四位数字仅使用 2–9。
- Non-goals: 不修改历史邀请码、激活输入校验、兼职编号、数据库、权限或前端交互；不提交、推送或重启服务。
- Branch / worktree / base: main / D:\ZSJ-OS / 315efd729f2e049fc2639194567d86077ae91184
- Owner: Codex current chat.
- Ownership: PartnerInvitationServiceImpl.java、PartnerInvitationServiceImplTest.java（zsjos service/personnel）；docs/api/partner-app-api.md；frontend/h5/兼职端API接口.md；本记录。
- Dependencies: 用户已确认字符规则；普通邀请和学员邀请共用生成方法。上述目标当前无 Git 修改，未发现其他 local handoff 占用该 Service。保留其他并发修改。
- Target branch / integration order: None / None.
- Verification plan: 更新既有生成格式断言，执行聚焦服务测试（含历史码激活）及编译；检查差异和接口文档；使用隔离输出避免共享构建资源冲突。

## Delivery — 2026-10-15
- 新邀请码统一使用 ABCDEFGHJKMNPQRSTUVWXYZ + 23456789，仍为前四位字母、后四位数字；随机源和冲突检查保留。兼职编号继续使用原数字规则。
- 更新普通邀请、学员邀请的格式断言；旧码激活参数化覆盖 abcd1234 和 iolo0101，保留大小写及旧字符兼容。两份接口文档同步。
- 在临时源码副本 C:\Users\EDY\AppData\Local\Temp\partner-invite-verify-384f0cde0fba4af0ab665de0052dc433 执行 Maven reactor 聚焦测试，21 个构建模块成功，PartnerInvitationServiceImplTest 共 25 项测试通过，0 失败/错误/跳过。命令：mvn -f <snapshot>/pom.xml -pl yudao-module-zsjos -am test -Dtest=PartnerInvitationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -Dflatten.skip=true -Dstyle.color=never。日志位于该目录 verification.log。
- 验证源码副本与当前两个 Java 文件哈希一致；字母集精确等于 A–Z 去除 I/O/L（23 个字母）；git diff --check 通过。
- 未重启、部署或调用真实生成/激活接口；当前结果是源码与隔离单元测试证据，运行服务需加载新版后端后才能应用新规则。无数据库修改、依赖变更或 Git 提交。
- Status: Delivered; ownership released.
