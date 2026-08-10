-- 中世健组织与 admin 账号初始化脚本
-- MySQL 8.x / 默认处理租户 1
--
-- 初始化策略：
--   1. 本脚本可重复执行，不清空租户内任何已有用户、部门、岗位、角色或权限关系；
--   2. 部门按“tenant_id + name”匹配（业务保证同一租户部门名称唯一），岗位和角色按
--      “tenant_id + code”匹配；匹配则更新种子控制字段并恢复逻辑删除，未匹配则新增；
--   3. 部门负责人、电话、邮箱以及已有角色菜单关系不由本脚本覆盖；
--   4. 主键由数据库生成，部门层级和 admin 关联均使用初始化后解析出的实际主键；
--   5. admin 不存在时创建，存在时恢复并重置为初始资料；每次执行都会将其密码重置为
--      下方脚本内置哈希对应的已确认初始密码，并确保其属于总公司、拥有系统管理员岗位
--      和超级管理员角色；
--   6. admin 已有的其他岗位、角色关系会保留，其他账号和非种子组织数据不受影响。

SET @tenant_id = 1;
SET @operator = 'quick-init';
SET @admin_password_hash = '$2b$04$FK7/XnpKy7JrWsVZaAGPJenx8yBnZqj59drNxZds7A4.XZYhLPDUO';

DROP TEMPORARY TABLE IF EXISTS tmp_quick_init_dept;
CREATE TEMPORARY TABLE tmp_quick_init_dept (
    name        varchar(30) NOT NULL,
    parent_name varchar(30) NULL,
    level_no    tinyint NOT NULL,
    sort        int NOT NULL,
    PRIMARY KEY (name)
);

INSERT INTO tmp_quick_init_dept (name, parent_name, level_no, sort)
VALUES
    ('中世健【总公司】',        NULL,                    0,  0),
    ('新媒体与客资中心',       '中世健【总公司】',       1,  1),
    ('新媒体一部',             '新媒体与客资中心',       2,  1),
    ('新媒体二部',             '新媒体与客资中心',       2,  2),
    ('新媒体三部',             '新媒体与客资中心',       2,  3),
    ('销售转化中心',           '中世健【总公司】',       1,  2),
    ('销售转化一部',           '销售转化中心',           2,  1),
    ('报名履约中心',           '中世健【总公司】',       1,  3),
    ('报名履约一部',           '报名履约中心',           2,  1),
    ('财务结算中心',           '中世健【总公司】',       1,  4),
    ('财务结算一部',           '财务结算中心',           2,  1),
    ('学生服务与交付中心',     '中世健【总公司】',       1,  5),
    ('学生服务与交付一部',     '学生服务与交付中心',     2,  1),
    ('考务中心',               '中世健【总公司】',       1,  6),
    ('考务一部',               '考务中心',               2,  1),
    ('就业指导事业部',         '中世健【总公司】',       1,  7),
    ('就业指导一部',           '就业指导事业部',         2,  1),
    ('IP师资与产品研发中心',   '中世健【总公司】',       1,  8),
    ('IP师资与产品研发一部',   'IP师资与产品研发中心',   2,  1),
    ('服务质控中心',           '中世健【总公司】',       1,  9),
    ('服务质控一部',           '服务质控中心',           2,  1),
    ('人力资源中心',           '中世健【总公司】',       1, 10),
    ('人力资源一部',           '人力资源中心',           2,  1),
    ('行政综合事务部',         '中世健【总公司】',       1, 11),
    ('行政综合事务一部',       '行政综合事务部',         2,  1),
    ('AI应用开发部',           '中世健【总公司】',       1, 12),
    ('AI应用开发一部',         'AI应用开发部',           2,  1);

DROP TEMPORARY TABLE IF EXISTS tmp_quick_init_post;
CREATE TEMPORARY TABLE tmp_quick_init_post (
    code   varchar(64) NOT NULL,
    name   varchar(50) NOT NULL,
    sort   int NOT NULL,
    status tinyint NOT NULL,
    remark varchar(500) NULL,
    PRIMARY KEY (code)
);

INSERT INTO tmp_quick_init_post (code, name, sort, status, remark)
VALUES
    ('center_head',            '中心负责人',       1,  0, '一级业务中心、事业部或职能部门负责人'),
    ('dept_manager',           '部门主管',         2,  0, '二级部门通用管理岗位'),
    ('content_director',       '编导',             3,  0, '公司级公共编导岗位，可在不同新媒体部门复用'),
    ('new_media_operator',     '新媒体运营',       4,  0, '新媒体内容及学员运营'),
    ('filming_editor',         '剪拍专员',         5,  0, '拍摄、剪辑及素材管理'),
    ('sales_manager',          '销售主管',         6,  0, NULL),
    ('sales_specialist',       '销售专员',         7,  0, NULL),
    ('enrollment_manager',     '报名履约主管',     8,  0, NULL),
    ('enrollment_specialist',  '报名履约专员',     9,  0, NULL),
    ('finance_manager',        '财务主管',        10,  0, NULL),
    ('finance_specialist',     '财务专员',        11,  0, NULL),
    ('study_planner',          '学习规划师',      12,  0, NULL),
    ('academic_specialist',    '教务专员',        13,  0, NULL),
    ('delivery_manager',       '交付主管',        14,  0, NULL),
    ('exam_manager',           '考务主管',        15,  0, NULL),
    ('exam_specialist',        '考务专员',        16,  0, NULL),
    ('career_planner',         '职业规划师',      17,  0, NULL),
    ('career_manager',         '就业指导主管',    18,  0, NULL),
    ('ip_teacher',             'IP老师',          19,  0, NULL),
    ('product_rd_head',        '产品研发负责人',  20,  0, NULL),
    ('teaching_assistant',     '助教',            21,  0, NULL),
    ('quality_manager',        '服务质控主管',    22,  0, NULL),
    ('quality_specialist',     '服务质控专员',    23,  0, NULL),
    ('recruitment_manager',    '招聘主管',        24,  0, NULL),
    ('recruitment_specialist', '招聘专员',        25,  0, NULL),
    ('hr_specialist',          '人力专员',        26,  0, NULL),
    ('admin_manager',          '行政主管',        27,  0, NULL),
    ('admin_specialist',       '行政专员',        28,  0, NULL),
    ('system_administrator',   '系统管理员',      29,  0, NULL),
    ('application_developer',  '应用开发工程师',  30,  0, NULL);

DROP TEMPORARY TABLE IF EXISTS tmp_quick_init_role;
CREATE TEMPORARY TABLE tmp_quick_init_role (
    code       varchar(100) NOT NULL,
    name       varchar(30) NOT NULL,
    sort       int NOT NULL,
    data_scope tinyint NOT NULL,
    status     tinyint NOT NULL,
    type       tinyint NOT NULL,
    remark     varchar(500) NULL,
    PRIMARY KEY (code)
);

INSERT INTO tmp_quick_init_role (code, name, sort, data_scope, status, type, remark)
VALUES
    ('center_head',            '中心负责人',       1, 4, 0, 2, '岗位“中心负责人”的同名角色'),
    ('dept_manager',           '部门主管',         2, 3, 0, 2, '岗位“部门主管”的同名角色'),
    ('content_director',       '编导',             3, 5, 0, 2, '岗位“编导”的同名角色'),
    ('new_media_operator',     '新媒体运营',       4, 5, 0, 2, '岗位“新媒体运营”的同名角色'),
    ('filming_editor',         '剪拍专员',         5, 5, 0, 2, '岗位“剪拍专员”的同名角色'),
    ('sales_manager',          '销售主管',         6, 3, 0, 2, '岗位“销售主管”的同名角色'),
    ('sales_specialist',       '销售专员',         7, 5, 0, 2, '岗位“销售专员”的同名角色'),
    ('enrollment_manager',     '报名履约主管',     8, 3, 0, 2, '岗位“报名履约主管”的同名角色'),
    ('enrollment_specialist',  '报名履约专员',     9, 5, 0, 2, '岗位“报名履约专员”的同名角色'),
    ('finance_manager',        '财务主管',        10, 3, 0, 2, '岗位“财务主管”的同名角色'),
    ('finance_specialist',     '财务专员',        11, 5, 0, 2, '岗位“财务专员”的同名角色'),
    ('study_planner',          '学习规划师',      12, 5, 0, 2, '岗位“学习规划师”的同名角色'),
    ('academic_specialist',    '教务专员',        13, 5, 0, 2, '岗位“教务专员”的同名角色'),
    ('delivery_manager',       '交付主管',        14, 3, 0, 2, '岗位“交付主管”的同名角色'),
    ('exam_manager',           '考务主管',        15, 3, 0, 2, '岗位“考务主管”的同名角色'),
    ('exam_specialist',        '考务专员',        16, 5, 0, 2, '岗位“考务专员”的同名角色'),
    ('career_planner',         '职业规划师',      17, 5, 0, 2, '岗位“职业规划师”的同名角色'),
    ('career_manager',         '就业指导主管',    18, 3, 0, 2, '岗位“就业指导主管”的同名角色'),
    ('ip_teacher',             'IP老师',          19, 5, 0, 2, '岗位“IP老师”的同名角色'),
    ('product_rd_head',        '产品研发负责人',  20, 3, 0, 2, '岗位“产品研发负责人”的同名角色'),
    ('teaching_assistant',     '助教',            21, 5, 0, 2, '岗位“助教”的同名角色'),
    ('quality_manager',        '服务质控主管',    22, 3, 0, 2, '岗位“服务质控主管”的同名角色'),
    ('quality_specialist',     '服务质控专员',    23, 5, 0, 2, '岗位“服务质控专员”的同名角色'),
    ('recruitment_manager',    '招聘主管',        24, 3, 0, 2, '岗位“招聘主管”的同名角色'),
    ('recruitment_specialist', '招聘专员',        25, 5, 0, 2, '岗位“招聘专员”的同名角色'),
    ('hr_specialist',          '人力专员',        26, 5, 0, 2, '岗位“人力专员”的同名角色'),
    ('admin_manager',          '行政主管',        27, 3, 0, 2, '岗位“行政主管”的同名角色'),
    ('admin_specialist',       '行政专员',        28, 5, 0, 2, '岗位“行政专员”的同名角色'),
    ('system_administrator',   '系统管理员',      29, 1, 0, 1, '岗位“系统管理员”的同名内置角色'),
    ('application_developer',  '应用开发工程师',  30, 5, 0, 2, '岗位“应用开发工程师”的同名角色'),
    ('super_admin',            '超级管理员',       0, 1, 0, 1, '岗位角色之外额外保留的超级管理员角色'),
    ('normal_user',            '普通员工',        31, 5, 0, 2, '普通员工');

START TRANSACTION;

-- 部门分层插入，确保解析父部门时父记录已经存在。
INSERT INTO system_dept
    (name, parent_id, sort, leader_user_id, phone, email, status,
     creator, create_time, updater, update_time, deleted, tenant_id)
SELECT seed.name, 0, seed.sort, NULL, NULL, NULL, 0,
       @operator, NOW(), @operator, NOW(), b'0', @tenant_id
FROM tmp_quick_init_dept seed
WHERE seed.level_no = 0
  AND NOT EXISTS (
      SELECT 1 FROM system_dept current_dept
      WHERE current_dept.tenant_id = @tenant_id AND current_dept.name = seed.name
  );

INSERT INTO system_dept
    (name, parent_id, sort, leader_user_id, phone, email, status,
     creator, create_time, updater, update_time, deleted, tenant_id)
SELECT seed.name, parent_dept.id, seed.sort, NULL, NULL, NULL, 0,
       @operator, NOW(), @operator, NOW(), b'0', @tenant_id
FROM tmp_quick_init_dept seed
JOIN system_dept parent_dept
  ON parent_dept.tenant_id = @tenant_id AND parent_dept.name = seed.parent_name
WHERE seed.level_no = 1
  AND NOT EXISTS (
      SELECT 1 FROM system_dept current_dept
      WHERE current_dept.tenant_id = @tenant_id AND current_dept.name = seed.name
  );

INSERT INTO system_dept
    (name, parent_id, sort, leader_user_id, phone, email, status,
     creator, create_time, updater, update_time, deleted, tenant_id)
SELECT seed.name, parent_dept.id, seed.sort, NULL, NULL, NULL, 0,
       @operator, NOW(), @operator, NOW(), b'0', @tenant_id
FROM tmp_quick_init_dept seed
JOIN system_dept parent_dept
  ON parent_dept.tenant_id = @tenant_id AND parent_dept.name = seed.parent_name
WHERE seed.level_no = 2
  AND NOT EXISTS (
      SELECT 1 FROM system_dept current_dept
      WHERE current_dept.tenant_id = @tenant_id AND current_dept.name = seed.name
  );

-- 仅更新层级、排序、启用状态和审计字段；保留负责人及联系方式。
UPDATE system_dept current_dept
JOIN tmp_quick_init_dept seed ON seed.name = current_dept.name
LEFT JOIN system_dept parent_dept
  ON parent_dept.tenant_id = @tenant_id AND parent_dept.name = seed.parent_name
SET current_dept.parent_id = CASE WHEN seed.level_no = 0 THEN 0 ELSE parent_dept.id END,
    current_dept.sort = seed.sort,
    current_dept.status = 0,
    current_dept.updater = @operator,
    current_dept.update_time = NOW(),
    current_dept.deleted = b'0'
WHERE current_dept.tenant_id = @tenant_id;

INSERT INTO system_post
    (code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT seed.code, seed.name, seed.sort, seed.status, seed.remark,
       @operator, NOW(), @operator, NOW(), b'0', @tenant_id
FROM tmp_quick_init_post seed
WHERE NOT EXISTS (
    SELECT 1 FROM system_post current_post
    WHERE current_post.tenant_id = @tenant_id AND current_post.code = seed.code
);

UPDATE system_post current_post
JOIN tmp_quick_init_post seed ON seed.code = current_post.code
SET current_post.name = seed.name,
    current_post.sort = seed.sort,
    current_post.status = seed.status,
    current_post.remark = seed.remark,
    current_post.updater = @operator,
    current_post.update_time = NOW(),
    current_post.deleted = b'0'
WHERE current_post.tenant_id = @tenant_id;

INSERT INTO system_role
    (name, code, sort, data_scope, data_scope_dept_ids, status, type, remark,
     creator, create_time, updater, update_time, deleted, tenant_id)
SELECT seed.name, seed.code, seed.sort, seed.data_scope, '', seed.status, seed.type, seed.remark,
       @operator, NOW(), @operator, NOW(), b'0', @tenant_id
FROM tmp_quick_init_role seed
WHERE NOT EXISTS (
    SELECT 1 FROM system_role current_role
    WHERE current_role.tenant_id = @tenant_id AND current_role.code = seed.code
);

UPDATE system_role current_role
JOIN tmp_quick_init_role seed ON seed.code = current_role.code
SET current_role.name = seed.name,
    current_role.sort = seed.sort,
    current_role.data_scope = seed.data_scope,
    current_role.data_scope_dept_ids = '',
    current_role.status = seed.status,
    current_role.type = seed.type,
    current_role.remark = seed.remark,
    current_role.updater = @operator,
    current_role.update_time = NOW(),
    current_role.deleted = b'0'
WHERE current_role.tenant_id = @tenant_id;

SET @root_dept_id = (
    SELECT id FROM system_dept
    WHERE tenant_id = @tenant_id AND name = '中世健【总公司】'
    LIMIT 1
);
SET @admin_post_id = (
    SELECT id FROM system_post
    WHERE tenant_id = @tenant_id AND code = 'system_administrator'
    LIMIT 1
);
SET @super_admin_role_id = (
    SELECT id FROM system_role
    WHERE tenant_id = @tenant_id AND code = 'super_admin'
    LIMIT 1
);

INSERT INTO system_users
    (username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar,
     status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 'admin', @admin_password_hash, '中世健管理员', '系统超级管理员', @root_dept_id,
       JSON_ARRAY(@admin_post_id), '', '', 0, '', 0, '', NULL,
       @operator, NOW(), @operator, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM system_users current_user
    WHERE current_user.tenant_id = @tenant_id AND current_user.username = 'admin'
);

SET @admin_user_id = (
    SELECT id FROM system_users
    WHERE tenant_id = @tenant_id AND username = 'admin'
    LIMIT 1
);

-- 明确按初始化约定重置 admin 密码、资料、所属部门和启用状态。
UPDATE system_users
SET password = @admin_password_hash,
    nickname = '中世健管理员',
    remark = '系统超级管理员',
    dept_id = @root_dept_id,
    status = 0,
    updater = @operator,
    update_time = NOW(),
    deleted = b'0'
WHERE id = @admin_user_id AND tenant_id = @tenant_id;

-- 已有逻辑删除关系时只恢复一条；没有有效关系时再新增，避免重复执行产生重复关系。
UPDATE system_user_role
SET deleted = b'0', updater = @operator, update_time = NOW()
WHERE id = (
    SELECT chosen.id
    FROM (
        SELECT id
        FROM system_user_role
        WHERE tenant_id = @tenant_id
          AND user_id = @admin_user_id
          AND role_id = @super_admin_role_id
        ORDER BY deleted ASC, id ASC
        LIMIT 1
    ) chosen
);

INSERT INTO system_user_role
    (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @admin_user_id, @super_admin_role_id, @operator, NOW(), @operator, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM system_user_role
    WHERE tenant_id = @tenant_id
      AND user_id = @admin_user_id
      AND role_id = @super_admin_role_id
      AND deleted = b'0'
);

UPDATE system_user_post
SET deleted = b'0', updater = @operator, update_time = NOW()
WHERE id = (
    SELECT chosen.id
    FROM (
        SELECT id
        FROM system_user_post
        WHERE tenant_id = @tenant_id
          AND user_id = @admin_user_id
          AND post_id = @admin_post_id
        ORDER BY deleted ASC, id ASC
        LIMIT 1
    ) chosen
);

INSERT INTO system_user_post
    (user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT @admin_user_id, @admin_post_id, @operator, NOW(), @operator, NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM system_user_post
    WHERE tenant_id = @tenant_id
      AND user_id = @admin_user_id
      AND post_id = @admin_post_id
      AND deleted = b'0'
);

-- post_ids 与有效岗位关系保持一致，同时保留 admin 原有的其他有效岗位。
UPDATE system_users
SET post_ids = COALESCE(
        (SELECT JSON_ARRAYAGG(active_post.post_id)
         FROM system_user_post active_post
         WHERE active_post.tenant_id = @tenant_id
           AND active_post.user_id = @admin_user_id
           AND active_post.deleted = b'0'),
        JSON_ARRAY(@admin_post_id)
    ),
    updater = @operator,
    update_time = NOW()
WHERE id = @admin_user_id AND tenant_id = @tenant_id;

COMMIT;

-- 初始化结果核对：只展示本脚本管理的种子数据及 admin 关联。
SELECT dept.id, dept.name, dept.parent_id, dept.sort, dept.status, dept.deleted
FROM system_dept dept
JOIN tmp_quick_init_dept seed ON seed.name = dept.name
WHERE dept.tenant_id = @tenant_id
ORDER BY seed.level_no, seed.sort, dept.id;

SELECT post.id, post.code, post.name, post.sort, post.status, post.deleted
FROM system_post post
JOIN tmp_quick_init_post seed ON seed.code = post.code
WHERE post.tenant_id = @tenant_id
ORDER BY post.sort, post.id;

SELECT role.id, role.code, role.name, role.sort, role.data_scope, role.status, role.deleted
FROM system_role role
JOIN tmp_quick_init_role seed ON seed.code = role.code
WHERE role.tenant_id = @tenant_id
ORDER BY role.sort, role.id;

SELECT current_user.id, current_user.username, current_user.dept_id, current_user.post_ids,
       current_user.status, current_user.deleted, current_user.tenant_id
FROM system_users current_user
WHERE current_user.tenant_id = @tenant_id AND current_user.username = 'admin';

SELECT relation.user_id, relation.role_id, role.code AS role_code, relation.deleted
FROM system_user_role relation
JOIN system_role role ON role.id = relation.role_id AND role.tenant_id = relation.tenant_id
WHERE relation.tenant_id = @tenant_id AND relation.user_id = @admin_user_id
ORDER BY relation.id;

SELECT relation.user_id, relation.post_id, post.code AS post_code, relation.deleted
FROM system_user_post relation
JOIN system_post post ON post.id = relation.post_id AND post.tenant_id = relation.tenant_id
WHERE relation.tenant_id = @tenant_id AND relation.user_id = @admin_user_id
ORDER BY relation.id;

DROP TEMPORARY TABLE IF EXISTS tmp_quick_init_role;
DROP TEMPORARY TABLE IF EXISTS tmp_quick_init_post;
DROP TEMPORARY TABLE IF EXISTS tmp_quick_init_dept;
