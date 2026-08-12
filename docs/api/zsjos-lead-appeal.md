# 客资三级申诉 API

统一前缀为 `/admin-api/zsjos/lead/appeal`，所有请求使用当前登录用户与租户上下文。

| 方法 | 路径 | 权限 |
|---|---|---|
| `GET` | `/lead/{leadId}/list` | `zsjos:lead:appeal:query` 或提交权限；提交人/负责人对象权限 |
| `POST` | `/lead/{leadId}/submit` | `zsjos:lead:appeal:create`；仅客资提交人、当前无效状态、下一合法轮次 |
| `GET` | `/inbox-page?handled=false|true` | `zsjos:lead:appeal:query`；BPM 待办/已办任务归属 |
| `PUT` | `/{appealId}/overturn` | 对应复核权限、BPM 任务和客资对象权限 |
| `PUT` | `/{appealId}/uphold` | 对应复核权限、BPM 任务和客资对象权限 |
| `POST` | `/attachment/upload` | 提交或复核权限；仅 JPG/PNG/WebP |

`GET /inbox-page` 在当前用户没有对应 BPM 待办或已办时返回 `list=[]`、`total=0`，空结果不是服务异常。

提交和裁决请求均必须携带非空 `idempotencyKey`；同一次申诉或裁决意图的快速重复点击、上传失败和请求重试必须复用该键，只有服务端确认成功后才生成下一意图的键。裁决还必须携带当前 BPM `taskId` 与理由。每个环节最多 9 张图片，图片引用只保存基础设施文件 ID 和不可变元数据快照，读取时返回私有预签名地址。

状态按 `sales_manager_reviewing`、`quality_reviewing`、`chairman_reviewing`、`overturned`、`upheld`、`withdrawn` 解释。`upheld` 仅表示当前轮维持无效，第三轮 `upheld` 才是最终无效；没有期限、自动升级或第四轮申诉。
