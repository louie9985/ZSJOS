-- PMS (Project / Knowledge Management) desired schema.
-- This file is the authoritative structure reference for the `pms` optional module.
-- Fresh installs are performed through migrations/pms/V001__pms_schema.sql; this file
-- documents the same structure for schema-drift comparison and review.
--
-- Ownership: PMS module. User references are plain System user IDs
-- (system_users.id), matching the repository's cross-module identifier convention.

CREATE TABLE IF NOT EXISTS `pms_iteration` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '迭代编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迭代名称',
  `owner_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `status` int NOT NULL DEFAULT '1' COMMENT '迭代状态，1 未开始，2 进行中，3 已完成',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `target` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '迭代目标',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '迭代描述',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_project_id_status_sort` (`project_id`,`status`,`sort`) USING BTREE,
  KEY `idx_owner_user_id` (`owner_user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 项目迭代';

CREATE TABLE IF NOT EXISTS `pms_knowledge_content_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '内容协作权限编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `open_status` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否对知识库可访问人公开',
  `open_level` tinyint NOT NULL DEFAULT '3' COMMENT '公开等级，1 管理、2 编辑、3 预览、4 下载、5 上传下载',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_library_id` (`library_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识内容协作权限';

CREATE TABLE IF NOT EXISTS `pms_knowledge_content_permission_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '协作者编号',
  `permission_id` bigint NOT NULL COMMENT '内容协作权限编号',
  `user_id` bigint DEFAULT NULL COMMENT '后台用户编号',
  `dept_id` bigint DEFAULT NULL COMMENT '部门编号',
  `level` tinyint NOT NULL COMMENT '协作等级，1 管理、2 编辑、3 预览、4 下载、5 上传下载',
  `mobile_read_only` bit(1) NOT NULL DEFAULT b'0' COMMENT '手机端是否仅查看',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_permission_id` (`permission_id`) USING BTREE,
  KEY `idx_user_id_permission_id` (`user_id`,`permission_id`) USING BTREE,
  KEY `idx_dept_id_permission_id` (`dept_id`,`permission_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识内容协作者';

CREATE TABLE IF NOT EXISTS `pms_knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `permission_id` bigint NOT NULL COMMENT '内容协作权限编号',
  `folder_id` bigint NOT NULL DEFAULT '0' COMMENT '文件夹编号，0 表示不在文件夹中',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父文档编号，0 表示根文档',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文档内容或文件地址',
  `type` int NOT NULL COMMENT '文档类型，3 富文本，4 文件',
  `file_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态，-1 回收站，0 草稿，1 正常，2 模板',
  `label_ids` varchar(5000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签编号列表',
  `delete_user_id` bigint DEFAULT NULL COMMENT '删除人用户编号',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_library_folder_parent_status` (`library_id`,`folder_id`,`parent_id`,`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库文档';

CREATE TABLE IF NOT EXISTS `pms_knowledge_document_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论编号',
  `document_id` bigint NOT NULL COMMENT '文档编号',
  `user_id` bigint NOT NULL COMMENT '评论人用户编号',
  `main_id` bigint NOT NULL DEFAULT '0' COMMENT '主评论编号，0 表示主评论',
  `reply_user_id` bigint DEFAULT NULL COMMENT '回复对象用户编号',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_document_id_create_time` (`document_id`,`create_time`) USING BTREE,
  KEY `idx_main_id` (`main_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库文档评论';

CREATE TABLE IF NOT EXISTS `pms_knowledge_document_label` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档标签编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签颜色',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库文档标签';

CREATE TABLE IF NOT EXISTS `pms_knowledge_document_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '点赞编号',
  `document_id` bigint NOT NULL COMMENT '文档编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_document_create_time` (`tenant_id`,`document_id`,`create_time`) USING BTREE,
  KEY `idx_tenant_user_document` (`tenant_id`,`user_id`,`document_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识文档点赞';

CREATE TABLE IF NOT EXISTS `pms_knowledge_document_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分享编号',
  `document_id` bigint NOT NULL COMMENT '文档编号',
  `share_user_ids` json DEFAULT NULL COMMENT '内部分享成员用户编号列表',
  `token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '外部查看令牌',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '分享状态，0 开启，1 关闭',
  `close_user_id` bigint DEFAULT NULL COMMENT '关闭人用户编号',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_token` (`token`) USING BTREE,
  UNIQUE KEY `uk_tenant_document_deleted` (`tenant_id`,`document_id`,`deleted`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库文档分享';

CREATE TABLE IF NOT EXISTS `pms_knowledge_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关注编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `type` int NOT NULL COMMENT '对象类型，1 知识库，2 文件夹，3 文档，4 文件',
  `entity_id` bigint NOT NULL COMMENT '对象编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_user_create_time_id` (`tenant_id`,`user_id`,`create_time`,`id`) USING BTREE,
  KEY `idx_tenant_user_library_create_time_id` (`tenant_id`,`user_id`,`library_id`,`create_time`,`id`) USING BTREE,
  KEY `idx_tenant_user_type_entity` (`tenant_id`,`user_id`,`type`,`entity_id`) USING BTREE,
  KEY `idx_tenant_library` (`tenant_id`,`library_id`) USING BTREE,
  KEY `idx_tenant_type_entity` (`tenant_id`,`type`,`entity_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识关注';

CREATE TABLE IF NOT EXISTS `pms_knowledge_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件夹编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `permission_id` bigint NOT NULL COMMENT '内容协作权限编号',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父文件夹编号，0 表示根目录',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件夹标题',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态，-1 回收站，1 正常',
  `delete_user_id` bigint DEFAULT NULL COMMENT '删除人用户编号',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_library_id_parent_id_status` (`library_id`,`parent_id`,`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库文件夹';

CREATE TABLE IF NOT EXISTS `pms_knowledge_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库分组编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `type` tinyint NOT NULL DEFAULT '3' COMMENT '分组类型，1 全部知识库，2 未分组，3 自定义分组',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_user_type` (`tenant_id`,`user_id`,`type`) USING BTREE,
  KEY `idx_tenant_user_sort` (`tenant_id`,`user_id`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 个人知识库分组';

CREATE TABLE IF NOT EXISTS `pms_knowledge_group_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `group_id` bigint NOT NULL COMMENT '知识库分组编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_user_library_deleted` (`tenant_id`,`user_id`,`library_id`,`deleted`) USING BTREE,
  KEY `idx_tenant_user_group_library` (`tenant_id`,`user_id`,`group_id`,`library_id`) USING BTREE,
  KEY `idx_tenant_library` (`tenant_id`,`library_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 个人知识库分组关系';

CREATE TABLE IF NOT EXISTS `pms_knowledge_library` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识库名称',
  `description` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知识库简介',
  `open_status` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否公开',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知识库封面',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态，-1 回收站，1 正常，2 模板',
  `delete_user_id` bigint DEFAULT NULL COMMENT '删除人用户编号',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_open_flag_create_time` (`open_status`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库';

CREATE TABLE IF NOT EXISTS `pms_knowledge_library_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `user_id` bigint DEFAULT NULL COMMENT '后台用户编号',
  `dept_id` bigint DEFAULT NULL COMMENT '部门编号',
  `level` tinyint NOT NULL COMMENT '成员等级，1 创建人、2 管理员、3 成员',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  `include_sub_dept` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否包含子部门（兼容当前运行中的后端构建）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_library_user_deleted` (`tenant_id`,`library_id`,`user_id`,`deleted`) USING BTREE,
  UNIQUE KEY `uk_tenant_library_dept_deleted` (`tenant_id`,`library_id`,`dept_id`,`deleted`) USING BTREE,
  KEY `idx_user_id_library_id` (`user_id`,`library_id`) USING BTREE,
  KEY `idx_dept_id_library_id` (`dept_id`,`library_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库成员';

CREATE TABLE IF NOT EXISTS `pms_knowledge_library_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板简介',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板封面地址',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '模板状态，0 开启，1 关闭',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `documents` json NOT NULL COMMENT '模板文档列表，元素包含 title 和 content',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status_sort` (`status`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库模板';

CREATE TABLE IF NOT EXISTS `pms_knowledge_recycle_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回收站记录编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `type` int NOT NULL COMMENT '对象类型，1 知识库，2 文件夹，3 文档，4 文件',
  `entity_id` bigint NOT NULL COMMENT '对象编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象名称快照',
  `delete_user_id` bigint NOT NULL COMMENT '删除人用户编号',
  `delete_time` datetime NOT NULL COMMENT '删除时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_type_entity` (`tenant_id`,`type`,`entity_id`) USING BTREE,
  KEY `idx_tenant_delete_time_id` (`tenant_id`,`delete_time`,`id`) USING BTREE,
  KEY `idx_tenant_library_delete_time` (`tenant_id`,`library_id`,`delete_time`) USING BTREE,
  KEY `idx_tenant_delete_user_type` (`tenant_id`,`delete_user_id`,`type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识库回收站记录';

CREATE TABLE IF NOT EXISTS `pms_knowledge_view_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '浏览记录编号',
  `library_id` bigint NOT NULL COMMENT '知识库编号',
  `type` int NOT NULL COMMENT '对象类型，2 文件夹，3 文档，4 文件',
  `entity_id` bigint NOT NULL COMMENT '对象编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_user_create_time_id` (`tenant_id`,`user_id`,`create_time`,`id`) USING BTREE,
  KEY `idx_tenant_library_create_time_id` (`tenant_id`,`library_id`,`create_time`,`id`) USING BTREE,
  KEY `idx_tenant_type_entity` (`tenant_id`,`type`,`entity_id`) USING BTREE,
  KEY `idx_tenant_user_type_entity` (`tenant_id`,`user_id`,`type`,`entity_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 知识最近浏览记录';

CREATE TABLE IF NOT EXISTS `pms_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目编号',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `status` int NOT NULL DEFAULT '1' COMMENT '项目状态，1 进行中，2 已归档，3 回收站',
  `type` int NOT NULL COMMENT '项目类型，1 通用项目，2 敏捷开发项目',
  `level` int NOT NULL COMMENT '优先级，1 最高，2 较高，3 普通，4 较低，5 最低',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '项目描述',
  `open_status` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否公开',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目图标',
  `sort` int DEFAULT NULL COMMENT '显示顺序',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '截止时间',
  `archive_time` datetime DEFAULT NULL COMMENT '归档时间',
  `recycle_time` datetime DEFAULT NULL COMMENT '移入回收站时间',
  `access_time` datetime DEFAULT NULL COMMENT '最近访问时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_status_access_time` (`status`,`access_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 项目';

CREATE TABLE IF NOT EXISTS `pms_project_announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '公告编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告内容',
  `file_urls` json DEFAULT NULL COMMENT '附件地址列表',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_project_id_create_time` (`project_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 项目公告';

CREATE TABLE IF NOT EXISTS `pms_project_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id_create_time` (`user_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 项目收藏';

CREATE TABLE IF NOT EXISTS `pms_project_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目分组编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分组名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `type` tinyint NOT NULL DEFAULT '3' COMMENT '分组类型，1 全部项目，2 未分组，3 自定义分组',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id_type` (`user_id`,`type`) USING BTREE,
  KEY `idx_user_id_sort` (`user_id`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 个人项目分组';

CREATE TABLE IF NOT EXISTS `pms_project_group_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `group_id` bigint NOT NULL COMMENT '项目分组编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id_project_id` (`user_id`,`project_id`) USING BTREE,
  KEY `idx_user_id_group_id` (`user_id`,`group_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 个人项目分组关系';

CREATE TABLE IF NOT EXISTS `pms_project_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `level` tinyint NOT NULL COMMENT '成员权限级别，1 拥有者、2 管理员、3 编辑、4 只读',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_project_id` (`project_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 项目成员';

CREATE TABLE IF NOT EXISTS `pms_project_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板编号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板描述',
  `project_type` int NOT NULL COMMENT '项目类型，1 通用项目，2 敏捷开发项目',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '模板状态，0 开启，1 关闭',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `item_types` json NOT NULL COMMENT '启用的工作项类型列表',
  `statuses` json NOT NULL COMMENT '工作项状态模板列表',
  `boards` json NOT NULL COMMENT '看板列模板列表',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_project_type_status_sort` (`project_type`,`status`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 项目模板';

CREATE TABLE IF NOT EXISTS `pms_work_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `type` int NOT NULL COMMENT '工作项类型，2 需求，3 任务，4 缺陷',
  `serial_number` int NOT NULL COMMENT '项目内工作项序号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作项标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '工作项描述',
  `priority` int NOT NULL COMMENT '优先级，0 无，1 低，2 中，3 高',
  `assignee_user_id` bigint DEFAULT NULL COMMENT '负责人用户编号',
  `status_id` bigint NOT NULL COMMENT '看板状态编号',
  `status` int NOT NULL COMMENT '语义状态，1 未开始，2 进行中，3 已完成',
  `lifecycle_status` int NOT NULL DEFAULT '1' COMMENT '生命周期状态，1 正常，2 已归档，3 回收站',
  `archive_time` datetime DEFAULT NULL COMMENT '归档时间',
  `recycle_time` datetime DEFAULT NULL COMMENT '移入回收站时间',
  `iteration_id` bigint DEFAULT NULL COMMENT '所属迭代编号',
  `parent_id` bigint DEFAULT NULL COMMENT '父工作项编号',
  `related_requirement_id` bigint DEFAULT NULL COMMENT '关联需求编号',
  `defect_type` int DEFAULT NULL COMMENT '缺陷类型，1 功能，2 界面，3 易用性，4 安全，5 性能，6 代码错误',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '截止时间',
  `estimated_hours` int DEFAULT NULL COMMENT '预估工时，单位：小时',
  `progress` int NOT NULL DEFAULT '0' COMMENT '完成进度，取值范围 0-100',
  `file_urls` json DEFAULT NULL COMMENT '附件地址列表',
  `label_ids` json DEFAULT NULL COMMENT '标签编号列表',
  `sort` int NOT NULL DEFAULT '0' COMMENT '看板内显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_project_id_serial_number_deleted` (`project_id`,`serial_number`,`deleted`) USING BTREE,
  KEY `idx_iteration_id` (`iteration_id`) USING BTREE,
  KEY `idx_assignee_user_id` (`assignee_user_id`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE,
  KEY `idx_related_requirement_id` (`related_requirement_id`) USING BTREE,
  KEY `idx_project_id_type_lifecycle_status_sort` (`project_id`,`type`,`lifecycle_status`,`status_id`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项';

CREATE TABLE IF NOT EXISTS `pms_work_item_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '动态编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `operator_user_id` bigint NOT NULL COMMENT '操作人用户编号',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动态内容',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_work_item_id_create_time` (`work_item_id`,`create_time`) USING BTREE,
  KEY `idx_project_id` (`project_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项动态';

CREATE TABLE IF NOT EXISTS `pms_work_item_board` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '看板列编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `work_item_type` int NOT NULL COMMENT '工作项类型，2 需求，3 任务，4 缺陷',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '看板列名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_project_id_type_sort` (`project_id`,`work_item_type`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项看板列';

CREATE TABLE IF NOT EXISTS `pms_work_item_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `user_id` bigint NOT NULL COMMENT '评论人用户编号',
  `main_id` bigint NOT NULL DEFAULT '0' COMMENT '主评论编号，0 表示主评论',
  `reply_user_id` bigint DEFAULT NULL COMMENT '回复对象用户编号',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_work_item_id_create_time` (`work_item_id`,`create_time`) USING BTREE,
  KEY `idx_main_id` (`main_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项评论';

CREATE TABLE IF NOT EXISTS `pms_work_item_label` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签编号',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签颜色',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_name` (`name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项标签';

CREATE TABLE IF NOT EXISTS `pms_work_item_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_work_item_id` (`work_item_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_project_id` (`project_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项参与人';

CREATE TABLE IF NOT EXISTS `pms_work_item_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '状态编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `work_item_type` int NOT NULL COMMENT '工作项类型，2 需求，3 任务，4 缺陷',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态名称',
  `status_type` int NOT NULL COMMENT '语义状态，1 未开始，2 进行中，3 已完成',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态描述',
  `board_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属看板列名称，为空时不在看板展示',
  `system_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系统状态编码，仅默认状态使用',
  `default_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否初始状态',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_project_type_system_code` (`tenant_id`,`project_id`,`work_item_type`,`system_code`) USING BTREE,
  KEY `idx_project_id_type_sort` (`project_id`,`work_item_type`,`sort`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项看板状态';

CREATE TABLE IF NOT EXISTS `pms_work_item_user_sort` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `user_id` bigint NOT NULL COMMENT '后台用户编号',
  `sort` int NOT NULL DEFAULT '0' COMMENT '个人显示顺序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_project_work_item_user_deleted` (`tenant_id`,`project_id`,`work_item_id`,`user_id`,`deleted`) USING BTREE,
  KEY `idx_project_user_sort` (`project_id`,`user_id`,`sort`) USING BTREE,
  KEY `idx_work_item_id` (`work_item_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项个人排序';

CREATE TABLE IF NOT EXISTS `pms_work_item_work_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工时记录编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `actual_hours` int NOT NULL COMMENT '实际投入工时，单位：小时',
  `remaining_hours` int NOT NULL DEFAULT '0' COMMENT '本次登记后的剩余工时，单位：小时',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工时说明',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_work_item_id_create_time` (`work_item_id`,`create_time`) USING BTREE,
  KEY `idx_project_id` (`project_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='PMS 工作项工时记录';
