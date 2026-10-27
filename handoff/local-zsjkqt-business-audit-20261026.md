# 考勤通业务审查与回归补充

## 登记（2026-10-26）
- ID: local-zsjkqt-business-audit-20261026
- 环境: local（ZSJOS_AGENT_ENV 未设置，/etc/zsjos/agent-environment 不存在）
- 分支: main；工作区: D:/ZSJ-OS；基线: 8279212fb97129ba6b006dbb0452ad843dfde4b5
- Owner: 本聊天主代理；目标: 审查内置考勤通业务链，补充异常转正常回归与问题证据。
- 非目标: 不修改生产计算公式、数据库、权限、桌面版、原始测试归档；不提交或发布。
- 写入范围: 本记录；frontend/workbench/src/features/zsjkqt/core/normalizationAudit.test.ts；docs/testing/zsjkqt/business-audit-20261026.md；docs/testing/zsjkqt/README.md。
- 依赖: 已有分类、汇总、重建与导出契约；其他工作区改动保留。
- 验证: 现有考勤通全部 Vitest、新增转换矩阵和缺陷复现、typecheck；检查 Java Excel/保存边界与既有测试执行条件。
- 集成目标/顺序: None。
- 基线: 14 文件 / 116 条 Vitest 通过。

## 范围补充
- 增加独占文件 backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/zsjkqt/ZsjkqtNormalizationAuditTest.java，验证所有状态改出勤后的保存校验、真实 Excel 导出回读与修订清单既有筛选口径。无生产代码变更。

## 验证进展
- 新增 70 条前端测试：60 条普通断言通过，10 条 A01–A10 以 `it.fails` 确认未修复反例；完整考勤通 186 条执行，其中 176 条普通通过、10 条预期失败复现。
- 最终 `npm.cmd run typecheck` 退出 0；`git diff --check` 无内容错误。
- 原有 Java 专项 45 条通过。首次 JVM 原生内存分配失败；384 MB Maven 堆设置使既有测试成功。
- 新增 Java 测试触发重新编译，384 MB 堆出现老年代 99% 占用且长期停留 javac；仅终止已核实命令行为本次专项的验证进程，改用 1536 MB 堆重新编译。无服务操作和永久配置修改。

## 交付
- 1536 MB Maven 重编译遇到 JVM 原生内存分配失败，不能记为成功。改为沿用仓库已有验证脚本的 JUnit Launcher 方式：读取本轮 `ZsjkqtExcelEngineTest` Surefire XML 的依赖 classpath，加本机已存在的 JUnit Launcher 6.0.3；在 `%TEMP%/zsjkqt-normalization-audit-20261026` 以 `javac -J-Xmx256m -J-XX:ActiveProcessorCount=2 @javac.args` 编译当前 Service、ExcelEngine 和新增测试；`java @java.args` 运行。未增加依赖或测试框架。
- 独立 JUnit 执行结果：26 found / started / successful，0 failed / skipped / aborted。真实 POI 工作簿写出和回读成功。
- 总新增 96 条：前端 70（60 普通回归 + 10 未修复反例）、Java 26。现有前端 116、现有 Java 45 本轮均通过。
- 业务报告完整列出 10 个反例、6 组需明确口径、覆盖映射和验证边界；原始归档保持不变。
- 本轮未修改生产实现、权限、SQL、依赖、历史业务数据，未提交、推送、部署。未运行浏览器或真实账号验收；没有 UI 改动，页面交互链以静态审查为证据。
- 本轮写入 5 个文件：两个新增测试、本报告、测试 README 和本 handoff。工作区其他改动保持原状。登记范围写入所有权释放。

## 修复登记更新
- 用户明确授权修复 A01/A02/A04；沿用本工作流，local/main，Owner 不变。
- 增加写入范围：frontend/workbench/src/features/zsjkqt/core/summary.ts、docs/api/zsjkqt.md；重新持有 normalizationAudit.test.ts、本报告、README、本记录。
- 目标：保留短假期转换的正常工作依据；按剩余时段统计全天旷工；无时段假期全天转换守恒。无时段半天转换不可推定时段，已发起口径澄清，独立修复继续。
- 验证：A01/A02/A04 改普通断言；增加有无正常底稿、加班覆盖、自定义班次、撤销重算、局部旷工及小时守恒回归；完整考勤通 Vitest、typecheck、限定 diff 检查。
- 非目标：其余审查问题、数据库/部署/权限/桌面版。目标分支与集成顺序 None。

## 限定修复交付
- 用户追问无时段来源，已核实并答复：旧测试只有无时段 4 小时合成输入的源汇总保留断言；2 小时转换重复是本轮合成反例，未发现真实月报证据。未得到半天分摊授权，保持此部分原行为，只实现全天统一转换一次计数。
- A01/A02/A04 已转为普通测试；新增 10 条覆盖正常/无正常依据、自定义班次、加班覆盖、上下半天旷工纠正、无时段总量、逆向转换、撤销及序列化。源码只改 summary.ts。
- `npx.cmd vitest run src/features/zsjkqt --maxWorkers=1 --no-file-parallelism`：15 文件，196 条执行（189 普通通过 + 7 未修复预期失败）。首次并行运行 Node 原生内存失败，单 worker 验证完成。
- `npm.cmd run typecheck` 本轮 Node 原生内存失败；`node --max-old-space-size=512 node_modules/typescript/bin/tsc --noEmit --skipLibCheck --strict --target ES2022 --module ESNext --moduleResolution Bundler src/features/zsjkqt/core/summary.ts src/features/zsjkqt/core/normalizationAudit.test.ts` 通过。该检查不等于全工作台 typecheck。
- 同步 API 规则说明、审查报告最新状态及测试 README；无 UI/接口/Java 变更，本轮无浏览器/Java/build 验证。保留人工覆盖、正式表续改与历史草稿；无数据库改写、提交、推送、部署。
- 独占写入范围释放。
