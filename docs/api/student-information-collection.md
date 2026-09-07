# 学员信息收集表

中仕建学员信息收集表归属于客资。销售在已成交客资概览点击“生成信息收集表”，通过链接或二维码交给学员填写。提交记录独立保存，不回写客资、订单或服务关系，不触发 BPM。

## 字段与版本

管理员页面 `frontend/admin/src/views/zsjos/studentInfoFormConfig/index.vue` 配置固定 16 个字段：报名分类、技能等级名称、姓名、性别、年龄、身份证号码、户籍所在地、手机号、现学历层次、毕业院校、毕业时间、工作单位、岗位、学习目的、邮寄地址、报名老师。

字段名称、类型、敏感属性由服务端定义；管理员维护启用、必填、排序、备注和字典绑定。性别固定绑定 `system_user_sex`，其他业务字典必须由管理员选择，不预填业务选项。地区读取 System 地区树。毕业时间仍为单行文本。发布至少需要一个启用字段，启用字典必须有有效选项。

租户配置锁串行化保存与发布，`id + revision` 防止旧页面覆盖新配置。生成时绑定已发布版本；之后发布新版本或轮换链接均不修改已有表单版本。历史详情按保存时字段名称、顺序、备注、字典标签和完整地区路径展示。

## 链接与状态

一个客资仅有一个有效待填实例。首次生成创建记录，重试生成返回已有记录。随机 token 为 32 字节安全随机数的 URL-safe Base64，数据库保存 SHA-256 检索摘要与加密原文。token 放在 URL fragment，并通过 `X-Student-Info-Token` 请求头传递，避免进入查询字符串日志。

链接有效期 30 天。重新生成需独立权限及当前 `formId`，旧实例保留但立即失效；撤销也需独立权限。生成、轮换、撤销和提交按客资行、表单行顺序加锁，数据库唯一索引限制每客资一个 DRAFT。已提交后不能修改、重新生成或撤销。公开端只返回当前状态、字段、选项和地区 API 所需租户上下文，不返回客资、订单或销售内部标识。无效链接、过期、已提交分别处理。

公开查询只在 token 定位阶段忽略租户；验证租户有效后切换到实例租户进行读取与写入，并在 finally 恢复上下文。提交再次检查成交状态、有效期、提交状态、字段集合、类型、长度、必填、手机号、身份证及字典/地区有效性。

## 接口与权限

下列管理接口使用 ADMIN 认证及租户上下文，前缀为 `/admin-api`。

| 接口 | 方法 | 权限后缀 |
| --- | --- | --- |
| `/zsjos/student-info-form/config` | GET | `config:query` |
| `/zsjos/student-info-form/config/draft` | POST | `config:update` |
| `/zsjos/student-info-form/config/publish` | POST | `config:publish` |
| `/zsjos/student-info-form/config/preview` | POST | `config:query` |
| `/zsjos/lead/{leadId}/student-info-form` | POST | `create` |
| 上述客资路径加 `/link` | GET | `link-read` |
| 上述客资路径加 `/regenerate` 或 `/revoke` | POST | `regenerate` 或 `revoke` |
| 上述客资路径加 `/detail` | GET | `read` |
| 上述客资路径加 `/sensitive` | GET | `read` 且 `sensitive-read` |
| 上述客资路径加 `/export` | GET | `read` 且 `export` |

权限统一前缀为 `zsjos:student-info-form:`。所有客资操作还需 `student-info` 对象权限。生成与链接管理限定当前销售归属/管理范围；阅读复用客资详情可见关系，包括既有规划师、编导协作关系。角色名称不参与运行时授权。规划师、编导需在现有角色配置中分配 `read`，不默认授予敏感查看或导出。

公开接口为 GET `/public-api/zsjos/student-info-form/detail` 与 POST `/public-api/zsjos/student-info-form/submit`，无需登录，均须 token 请求头。提交体为 `{ "values": { "字段标识": "文本或字典code" } }`；地区值为完整数字编码数组。服务端自行解析标签，不信任客户端标签。

## 敏感数据

所有普通文本答案和 token 原文使用既有 `EncryptTypeHandler` 加密，依赖 `mybatis-plus.encryptor.password`；密钥丢失会使历史数据不可读。身份证、手机号默认脱敏，完整查看需显式操作及独立权限。导出有独立权限，没有敏感读取权限时导出仍脱敏。响应禁止缓存，API 访问日志禁用正文；专用异常处理器避免框架将个人信息正文写入异常日志。生产环境不得开启包含提交参数的 SQL DEBUG 日志。

## 数据库与启用

V183 是未发布的开发基线修正，已同时同步 `00-bootstrap-schema.sql` 和 `schema/core.sql`；若其他环境已应用不同版本 V183，应先核对结构，不能仅因版本号相同直接上线。四张表为 `zsjos_student_info_form`、`zsjos_student_info_form_value`、`zsjos_student_info_form_config`、`zsjos_student_info_config_lock`。

顺序为 V182 → V183 → V184。V183 仅创建空业务表和 10 个 System 菜单/按钮定义，不创建学员数据、字典选项或实际角色授权。SQL 使用 `SET NAMES utf8mb4`；执行后运行 `verify-student-information-collection.sql` 并核对中文字节及结构。重复执行保留已配置数据。回退可禁用功能权限，但必须保留历史表、配置和加密密钥。

部署 H5 时须支持 history 路由 `/student-info-form` 和 `/public-api`、`/app-api` 代理。后端配置 `zsjos.student-info.public-base-url` 或 `ZSJOS_PUBLIC_H5_BASE_URL` 为真实 H5 根地址，无 localhost 默认值。发布字段配置、配置销售与协作角色权限后才可生成实际链接。

## 验证边界

本次包含 Service、配置、对象权限及 Controller 方法权限/MockMvc 测试；三个前端生产构建；独立 MySQL 库内 V183 两次执行、权限计数、中文字节和基线/迁移字段结构比较。浏览器夹具仅用于验证真实前端组件的布局与交互，位于各前端 `tests`，不进入生产入口，也不替代真实账号联调。

完整 bootstrap 在已有 V158 因菜单 ID 79913 冲突中止；不能宣称全库初始化已通过。真实业务库尚未激活 V183，实际角色授权、真实三端联调和数据库并发事务压力验证仍需在启用环境完成。

2026-09-07 验证记录：收集表及关联回归共 50 项后端测试通过；H5 身份证校验 2 项和 Workbench 标签工具 11 项测试通过；服务器及三个前端生产构建通过，Admin 新页面 ESLint 通过。Admin 全量类型检查仍有其他页面的已有错误；扩展 LeadManagement 全量回归还存在 `detailAlwaysProjectsSubmitterAssistWhenReadableAndPermitted` 的既有协助入口断言失败，本次未改动该规则。当前本地业务库未记录 V182/V183/V184，启用时应先确认迁移依赖，不能直接以版本标记代替结构核验。
