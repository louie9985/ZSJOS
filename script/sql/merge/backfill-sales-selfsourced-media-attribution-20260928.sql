-- 销售自拓客资：回填新媒体提供方归属（依据 parttimecrm-cst+0800-20260920-222103 dump）
--
-- 背景：这 16 条在旧库 leads_lead 中 submitter_is_internal_media_snapshot=1，
--       即"由新媒体员工提供、销售代为提交"。迁移时未落 provider/contribution 归属，
--       导致 provider_owner_id 等 11 个字段为空（LD202609160068 除外，被 backfill-20260921
--       误写成提交人本人）。
--
-- 映射链：旧库 leads_lead.submitter_media_staff_id_snapshot
--           -> employees_employee_profile.id (64=栗伊琳 / 66=程旭)
--           -> accounts_user.phone
--           -> zsjos.system_users (15955100553=31 / 19305687808=26)
--         上级 submitter_media_supervisor_id_snapshot=61 陈薇 -> zsjos.system_users.id=20
--         部门固定 1011 新媒体一部（leader_user_id=20，与旧库上级自洽）
--
-- 重要：source_provider_user_id / source_provider_recorded / provider_owner_id / provider_owner_type
--       四者必须整体一致，否则 LeadNotifySceneProvider:230 会判定
--       LEAD_SOURCE_ATTRIBUTION_MISMATCH。快照组字段一并写入，使整行看起来
--       与 LeadProviderAttributionService.applySystemUser() 刚执行过一致。
--
-- source_user_id（销售本人）与 source_dept_id 一律不动。
--
-- 回滚：旧值见 zsjos_lead_bak_20260928_selfsourced_media（16 行全量快照）。
--
-- 不在范围内（旧库 staff_id 为 NULL，无归属人员，保持全空）：
--   LD202608210043, LD202608240003, LD202608240043, LD202608280054,
--   LD202608300074, LD202609090054, LD202609090055, LD202609150034, LD202609150070

-- 栗伊琳 uid=31（旧 staff_id=64），4 条
UPDATE zsjos_lead SET
  source_provider_user_id                    = 31,
  source_provider_recorded                   = b'1',
  provider_owner_type                        = 'system_user',
  provider_owner_id                          = 31,
  provider_owner_name_snapshot               = '栗伊琳',
  contribution_user_id_snapshot              = 31,
  contribution_user_name_snapshot            = '栗伊琳',
  contribution_dept_id_snapshot              = 1011,
  contribution_dept_name_snapshot            = '新媒体一部',
  contribution_supervisor_user_id_snapshot   = 20,
  contribution_supervisor_name_snapshot      = '陈薇'
WHERE id IN (2888, 3082, 3181, 4909);

-- 程旭 uid=26（旧 staff_id=66），12 条
UPDATE zsjos_lead SET
  source_provider_user_id                    = 26,
  source_provider_recorded                   = b'1',
  provider_owner_type                        = 'system_user',
  provider_owner_id                          = 26,
  provider_owner_name_snapshot               = '程旭',
  contribution_user_id_snapshot              = 26,
  contribution_user_name_snapshot            = '程旭',
  contribution_dept_id_snapshot              = 1011,
  contribution_dept_name_snapshot            = '新媒体一部',
  contribution_supervisor_user_id_snapshot   = 20,
  contribution_supervisor_name_snapshot      = '陈薇'
WHERE id IN (2048, 2372, 2584, 2971, 2981, 3067, 4360, 4419, 4758, 4789, 4922, 5927);
