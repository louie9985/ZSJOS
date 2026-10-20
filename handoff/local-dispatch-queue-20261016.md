# 派单轮询队列重连顺序修复

## 登记
- Workstream: local-dispatch-queue-20261016；Owner: Codex；环境: local（环境变量及环境文件未设置）。
- 目标：首次上线及离线重连进入等待队尾，避免抢占持续在线销售的下一次普通轮询机会。
- 非目标：新人优先额度、接单资格、前端连接管理、SQL、运行中 Redis 数据、部署或重启。
- Branch: main；Worktree: D:/ZSJ-OS；Base: d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2。
- Ownership: LeadDispatchRedisRepository.java、对应测试 LeadDispatchRedisRepositoryTest.java、docs/api/zsjos-lead-submission-dispatch.md、本记录。
- Dependencies: 现有 StringRedisTemplate、Lua 原子脚本及右出左入轮询；保留其他任务未提交改动。
- 验证：隔离构建输出；首次上线顺序、接单释放后反复重连、心跳保序、过期重入去重、空队列；实际 Redis 命令验证。无依赖新增。
- Target branch / integration order: None。

## 交付（2026-10-16）
- 修改 HEARTBEAT_SCRIPT：RPUSH 改为 LPUSH；保留 LREM 去重、90 秒 TTL、有效心跳保序和 rightPopAndLeftPush。首次上线及重连进入等待队尾；反复重连会反复重新排队，不保留离线前位置。
- 同步派单 API 文档；新增 LeadDispatchRedisRepositoryTest，使用项目已有 jedis-mock 与 Redisson，随机端口、每例独立数据。
- 验证通过：5 tests / 0 failures / 0 errors，覆盖首次上线顺序、释放接单预留后反复重连与六人持续在线、重复心跳保序及偏好更新、过期状态重入去重、空队列和单人重连。仅编译目标仓库类及测试，未做整个模块构建或应用层接单验收。
- 构建目录：C:/Users/EDY/AppData/Local/Temp/zsjos-dispatch-queue-20261016；命令：mvn -B -f <目录>/pom.xml -Dtest=LeadDispatchRedisRepositoryTest -Dflatten.skip=true test。临时 POM 复用模块依赖与根父配置，显式解析 revision，限定目标编译文件；未更改仓库构建配置或增加依赖。报告在 target/surefire-reports，日志在 test.log。初期父配置未正确加载造成依赖版本混用，修正临时 relativePath 后验证通过。
- 真实 Redis 5.0.14.1 独立临时实例验证通过：从修改后的 Java 源码提取 Lua，以 RPUSH 变体复现同一测试销售连续三次被取出；LPUSH 下其他六人依次获得机会，停止重连后的完整轮转通过。实例仅监听本机随机端口、关闭持久化，验证后已退出；未连接业务 Redis。
- git diff --check 通过（只有 Windows 换行提示）。保留其他任务工作区改动。
- 未提交、推送、部署、重启或修改测试环境数据。测试环境仍需部署新后端后验证真实派单；无需清空或重建已有队列，新代码对后续上线/重连生效，不追溯历史派单。
- Ownership released；本地源码修复与聚焦验证完成。
