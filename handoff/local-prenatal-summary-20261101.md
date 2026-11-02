# 产检假汇总
- 环境 local：环境变量及配置文件未设置；分支 main；工作树 D:\ZSJ-OS；base 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425。
- Owner Codex；目标：页面、导出及回读产检假天数；非目标：其他假别、数据库、部署。
- 所有权：考勤 core models/summary/summaryFields/reviewBuilder/employeeIdentity 及相关测试；ZsjkqtExcelEngine 和 ZsjkqtReviewRegressionTest；docs/api/zsjkqt.md；本记录。
- 依赖：现有独立假别及产检假出勤规则；集成顺序/目标分支 None。
- 验证：考勤测试、类型检查、Java 导出回读测试、可用隔离浏览器。

## 交付
- 页面新增产检假/天，正式 Excel 尾部追加同列，旧表允许缺列。旧草稿/旧表仅补新汇总，不覆盖已有人工值。
- 199 项前端测试、TypeScript 检查、ZsjkqtReviewRegressionTest（包含真实 XLSX 导出回读和缺列兼容）通过；diff 检查通过。
- 隔离浏览器 1440/390 下显示值、人工修改、保存重开通过。截图位于 C:/Users/EDY/AppData/Local/Temp/prenatal-summary-{1440,390}.png。390 宽度既有固定宽度布局存在横向裁切，未扩展本次范围调整布局。
- 未部署，真实账号及线上报表尚未验证；所有权释放。
