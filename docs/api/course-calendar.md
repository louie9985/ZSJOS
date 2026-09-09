# 课程日历 API

课程安排归属 ZSJOS，按租户隔离。租户内持有 `zsjos:course-calendar:query` 的用户可查询，持有
`zsjos:course-calendar:manage` 的用户可新增、修改和逻辑删除。每条记录是一次课程时间段，时间精确到分钟。

固定字段为课程名称、课程形式、课程时间、备注和附件。课程形式使用 System 字典 `zsjos_course_form`，初始值
`LIVE=直播`；业务记录保存 value 和选择时的 label 快照。附件通过 Infra FileApi 管理，业务记录保存文件 ID。

接口：`GET /admin-api/zsjos/course-calendar/page`、`GET /admin-api/zsjos/course-calendar/{id}`、
`POST /admin-api/zsjos/course-calendar`、`PUT /admin-api/zsjos/course-calendar/{id}`、
`DELETE /admin-api/zsjos/course-calendar/{id}`。
