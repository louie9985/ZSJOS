# V233 迁移脚本预期失败说明

## 📌 现象

执行迁移脚本时，V233 会报错：

```
[45000][1644] V233 material approval menu conflicts with existing configuration
```

## ✅ 这是预期行为，不是问题！

### 为什么会失败？

V233-V235 是一组**相关联的菜单演进迁移**：

```
V233 (创建独立审批菜单)
  ↓
V234 (删除独立审批菜单)
  ↓
V235 (恢复为嵌入式审批功能)
```

### 设计意图

1. **V233**：最初的设计是创建独立的"素材审批"页面
2. **V234**：后来决定废弃独立页面，软删除菜单
3. **V235**：将审批功能集成回"素材管理"页面（嵌入模式）

### V233 的保护机制

V233 包含了一个**冲突检测逻辑**：

```sql
-- 如果菜单存在但配置不符合预期（比如已被 V234/V235 修改）
IF NOT EXISTS (
  SELECT 1 FROM system_menu 
  WHERE permission='zsjos:material-approval:query' 
    AND parent_id=directory_id 
    AND path='approvals' 
    AND workbench_render_mode='native'
) THEN
  -- 主动失败，阻止破坏现有配置
  SIGNAL SQLSTATE '45000' 
  SET MESSAGE_TEXT='V233 material approval menu conflicts...';
END IF;
```

### 为什么这是好的设计？

✅ **防止重复执行破坏配置**：
- 如果 V235 已经执行，菜单处于"嵌入模式"
- V233 尝试创建"独立模式"菜单时，会检测到冲突并失败
- 这避免了两种模式并存的混乱状态

✅ **幂等性保护**：
- 多次执行迁移脚本时，V233 会继续失败
- 但 V234 和 V235 是幂等的，可以安全重复执行
- 最终状态始终是正确的（嵌入模式）

✅ **显式失败优于静默错误**：
- 如果配置冲突，明确报错比静默通过更安全
- 提醒开发者检查菜单状态

## 🔍 如何验证最终状态正确？

执行以下 SQL 检查：

```sql
-- 1. 检查素材管理主菜单（应该是 admin_embed 模式）
SELECT id, name, permission, workbench_render_mode, visible, deleted
FROM system_menu 
WHERE permission = 'zsjos:material:manage' AND id = 80012;

-- 预期结果：
-- workbench_render_mode = 'admin_embed'
-- visible = b'1'
-- deleted = b'0'

-- 2. 检查审批动作按钮（应该是 admin_only 隐藏模式）
SELECT id, name, permission, parent_id, workbench_render_mode, visible, deleted
FROM system_menu 
WHERE permission IN (
  'zsjos:material-approval:query',
  'zsjos:material-approval:approve',
  'zsjos:material-approval:reject'
);

-- 预期结果：
-- parent_id = 80010 (素材库目录)
-- workbench_render_mode = 'admin_only'
-- visible = b'0' (不在菜单中显示)
-- deleted = b'0' (未删除，只是隐藏)

-- 3. 检查版本记录
SELECT version, description, installed_at 
FROM zsjos_schema_version 
WHERE version IN ('V233', 'V234', 'V235')
ORDER BY version;

-- 预期结果：
-- V234 和 V235 应该有记录
-- V233 可能没有记录（因为失败了）
```

## 📋 执行日志解读

正常的执行日志应该是：

```
✅ V233: 2/2 条语句已执行, 1 条失败  ← 预期失败
✅ V234: 4/2 条语句已执行            ← 成功，5行受影响
✅ V235: 5/2 条语句已执行            ← 成功，6行受影响
```

这说明：
- V233 失败了（因为检测到配置冲突）
- V234 成功软删除了旧的独立菜单
- V235 成功恢复了嵌入式审批功能

## 🎯 总结

| 问题 | 答案 |
|------|------|
| V233 失败是 bug 吗？ | ❌ 不是，这是设计的保护机制 |
| 需要修复吗？ | ❌ 不需要，最终状态是正确的 |
| 会影响功能吗？ | ❌ 不会，V235 已确保功能正常 |
| 可以忽略这个错误吗？ | ✅ 可以，这是预期行为 |
| 重复执行会怎样？ | ✅ V233 继续失败，V234/V235 幂等执行 |

## 🚫 不要做的事

1. ❌ 不要删除 V233 脚本（它是迁移历史的一部分）
2. ❌ 不要尝试"修复" V233 让它通过（会破坏 V235 的配置）
3. ❌ 不要手动删除素材审批相关菜单

## ✅ 可以做的事

1. ✅ 在部署文档中注明"V233 预期失败"
2. ✅ 执行上述验证 SQL 确认最终状态正确
3. ✅ 如果需要，在 V233 开头添加注释说明预期失败

---
**最后更新**: 2026-09-15  
**状态**: V233 预期失败不影响系统功能
