param(
  [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
)

$csvPath = Join-Path $RepositoryRoot 'backend/yudao-framework/yudao-spring-boot-starter-biz-ip/src/main/resources/area.csv'
$outputPath = Join-Path $RepositoryRoot 'script/sql/mysql/migrations/V012__system_area_management.sql'
$rows = Import-Csv -LiteralPath $csvPath
$sortByParent = @{}
$lines = [System.Collections.Generic.List[string]]::new()
$areaQueryName = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('5Zyw5Yy65p+l6K+i'))
$areaCreateName = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('5Zyw5Yy65Yib5bu6'))
$areaUpdateName = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('5Zyw5Yy65pu05paw'))

$lines.Add('-- Moves the bundled administrative-area snapshot into a system-owned editable tree.')
$lines.Add('-- Dependencies: the System module schema, menu 2083, and zsjos_schema_version.')
$lines.Add('-- Execution order: create table, insert missing snapshot rows, add permissions, inherit grants, record version.')
$lines.Add('-- Repeatability: CREATE IF NOT EXISTS and INSERT IGNORE preserve all administrator changes on rerun.')
$lines.Add('-- Data scope: the 3,879 rows bundled in area.csv plus three area permission buttons; no rows are deleted.')
$lines.Add('-- Recovery: forward-only; disable manually added areas if rollback is required.')
$lines.Add('')
$lines.Add("CREATE TABLE IF NOT EXISTS ``zsjos_schema_version`` (``version`` varchar(64) NOT NULL, ``description`` varchar(255) NOT NULL, ``checksum`` varchar(128) DEFAULT NULL, ``installed_at`` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (``version``)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ZSJOS database schema versions';")
$lines.Add('')
$lines.Add("CREATE TABLE IF NOT EXISTS ``system_area`` (``id`` int NOT NULL COMMENT 'Stable administrative area code', ``name`` varchar(100) NOT NULL COMMENT 'Area name', ``type`` tinyint NOT NULL COMMENT 'Area type: 1 country, 2 province, 3 city, 4 district', ``parent_id`` int NOT NULL DEFAULT 0 COMMENT 'Parent area code', ``sort`` int NOT NULL DEFAULT 0 COMMENT 'Display order', ``status`` tinyint NOT NULL DEFAULT 0 COMMENT 'Status: 0 enabled, 1 disabled', ``creator`` varchar(64) DEFAULT '' COMMENT 'Creator', ``create_time`` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time', ``updater`` varchar(64) DEFAULT '' COMMENT 'Updater', ``update_time`` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time', ``deleted`` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Logical deletion flag', PRIMARY KEY (``id``), UNIQUE KEY ``uk_parent_name`` (``parent_id``,``name``,``deleted``), KEY ``idx_parent_status_sort`` (``parent_id``,``status``,``sort``)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System administrative areas';")
$lines.Add('')

for ($offset = 0; $offset -lt $rows.Count; $offset += 500) {
  $end = [Math]::Min($offset + 499, $rows.Count - 1)
  $lines.Add('INSERT IGNORE INTO `system_area` (`id`,`name`,`type`,`parent_id`,`sort`,`status`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES')
  for ($index = $offset; $index -le $end; $index++) {
    $row = $rows[$index]
    $parentKey = [string]$row.parentId
    if (-not $sortByParent.ContainsKey($parentKey)) { $sortByParent[$parentKey] = 0 }
    $sortByParent[$parentKey] = [int]$sortByParent[$parentKey] + 1
    $name = $row.name.Replace("'", "''")
    $suffix = if ($index -eq $end) { ';' } else { ',' }
    $lines.Add("($($row.id),'$name',$($row.type),$($row.parentId),$($sortByParent[$parentKey]),0,'area.csv',NOW(),'area.csv',NOW(),b'0')$suffix")
  }
  $lines.Add('')
}

$lines.Add('INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES')
$lines.Add("(6790,'$areaQueryName','system:area:query',3,1,2083,'','','',NULL,0,b'1',b'1',b'1','migration-V012',NOW(),'migration-V012',NOW(),b'0'),")
$lines.Add("(6791,'$areaCreateName','system:area:create',3,2,2083,'','','',NULL,0,b'1',b'1',b'1','migration-V012',NOW(),'migration-V012',NOW(),b'0'),")
$lines.Add("(6792,'$areaUpdateName','system:area:update',3,3,2083,'','','',NULL,0,b'1',b'1',b'1','migration-V012',NOW(),'migration-V012',NOW(),b'0');")
$lines.Add('')
$lines.Add("INSERT INTO ``system_role_menu`` (``role_id``,``menu_id``,``creator``,``create_time``,``updater``,``update_time``,``deleted``,``tenant_id``) SELECT DISTINCT source.role_id,target.menu_id,'migration-V012',NOW(),'migration-V012',NOW(),b'0',source.tenant_id FROM ``system_role_menu`` source CROSS JOIN (SELECT 6790 menu_id UNION ALL SELECT 6791 UNION ALL SELECT 6792) target WHERE source.menu_id=2083 AND source.deleted=b'0' AND NOT EXISTS (SELECT 1 FROM ``system_role_menu`` existing WHERE existing.role_id=source.role_id AND existing.menu_id=target.menu_id AND existing.tenant_id=source.tenant_id AND existing.deleted=b'0');")
$lines.Add('')
$lines.Add("INSERT IGNORE INTO ``zsjos_schema_version`` (``version``,``description``,``checksum``) VALUES ('V012','Move system areas to an editable database tree','system-area-management-v1');")

$lines | Set-Content -LiteralPath $outputPath -Encoding utf8
Write-Output "Generated $outputPath with $($rows.Count) area rows"
