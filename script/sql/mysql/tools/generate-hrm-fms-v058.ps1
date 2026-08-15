[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourceSql,

    [string] $OutputSql = (Join-Path $PSScriptRoot '..\migrations\V058__hrm_fms_metadata_and_data_cleanup.sql')
)

$ErrorActionPreference = 'Stop'

function Split-SqlList([string] $Text) {
    $items = [System.Collections.Generic.List[string]]::new()
    $start = 0
    $quoted = $false
    $escaped = $false
    $depth = 0
    for ($index = 0; $index -lt $Text.Length; $index++) {
        $character = $Text[$index]
        if ($escaped) {
            $escaped = $false
            continue
        }
        if ($quoted) {
            if ($character -eq '\') {
                $escaped = $true
                continue
            }
            if ($character -eq "'") {
                if ($index + 1 -lt $Text.Length -and $Text[$index + 1] -eq "'") {
                    $index++
                    continue
                }
                $quoted = $false
            }
            continue
        }
        if ($character -eq "'") {
            $quoted = $true
        } elseif ($character -eq '(') {
            $depth++
        } elseif ($character -eq ')') {
            $depth--
        } elseif ($character -eq ',' -and $depth -eq 0) {
            $items.Add($Text.Substring($start, $index - $start).Trim())
            $start = $index + 1
        }
    }
    $items.Add($Text.Substring($start).Trim())
    return ,$items.ToArray()
}

function ConvertFrom-SqlString([string] $Value) {
    if ($Value -eq 'NULL') {
        return $null
    }
    if ($Value.StartsWith("'") -and $Value.EndsWith("'")) {
        return $Value.Substring(1, $Value.Length - 2).Replace("''", "'").Replace("\'", "'")
    }
    return $Value
}

function Read-InsertRows([string] $TableName) {
    $prefix = "INSERT INTO ``$TableName``"
    $rows = [System.Collections.Generic.List[object]]::new()
    foreach ($line in [System.IO.File]::ReadLines($SourceSql)) {
        if (-not $line.StartsWith($prefix)) {
            continue
        }
        if ($line -notmatch "^INSERT INTO ``$TableName`` \((.*)\) VALUES \((.*)\);$") {
            throw "Unsupported insert format for $TableName"
        }
        $columns = Split-SqlList $Matches[1]
        $values = Split-SqlList $Matches[2]
        if ($columns.Count -ne $values.Count) {
            throw "Column/value count mismatch for $TableName"
        }
        $raw = [ordered]@{}
        $decoded = [ordered]@{}
        for ($index = 0; $index -lt $columns.Count; $index++) {
            $name = $columns[$index].Trim('`')
            $raw[$name] = $values[$index]
            $decoded[$name] = ConvertFrom-SqlString $values[$index]
        }
        $rows.Add([pscustomobject]@{ Raw = $raw; Value = $decoded })
    }
    return $rows.ToArray()
}

function Add-GuardedDelete(
    [System.Collections.Generic.List[string]] $Lines,
    [string] $Table,
    [string] $Predicate
) {
    $deleteSql = "DELETE FROM ``$Table`` WHERE $Predicate".Replace("'", "''")
    $Lines.Add("SET @v058_sql := IF(@v058_apply = 1 AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '$Table'), '$deleteSql', 'SELECT 1');")
    $Lines.Add('PREPARE v058_stmt FROM @v058_sql;')
    $Lines.Add('EXECUTE v058_stmt;')
    $Lines.Add('DEALLOCATE PREPARE v058_stmt;')
}

$resolvedSource = (Resolve-Path -LiteralPath $SourceSql).Path
$menuRows = Read-InsertRows 'system_menu'
$dictTypeRows = @(Read-InsertRows 'system_dict_type' | Where-Object {
    $_.Value.type.StartsWith('hrm_') -or $_.Value.type.StartsWith('fms_')
})
$dictDataRows = @(Read-InsertRows 'system_dict_data' | Where-Object {
    $_.Value.dict_type.StartsWith('hrm_') -or $_.Value.dict_type.StartsWith('fms_')
})

$menuById = @{}
foreach ($row in $menuRows) {
    $menuById[[long] $row.Value.id] = $row
}
$selectedMenuIds = [System.Collections.Generic.HashSet[long]]::new()
foreach ($row in $menuRows) {
    $permission = [string] $row.Value.permission
    if ($permission.StartsWith('hrm:') -or $permission.StartsWith('fms:')) {
        [void] $selectedMenuIds.Add([long] $row.Value.id)
    }
}
foreach ($id in @($selectedMenuIds)) {
    $parentId = [long] $menuById[$id].Value.parent_id
    while ($parentId -ne 0 -and $menuById.ContainsKey($parentId)) {
        [void] $selectedMenuIds.Add($parentId)
        $parentId = [long] $menuById[$parentId].Value.parent_id
    }
}
$changed = $true
while ($changed) {
    $changed = $false
    foreach ($row in $menuRows) {
        if ($selectedMenuIds.Contains([long] $row.Value.parent_id) -and
            -not $selectedMenuIds.Contains([long] $row.Value.id)) {
            [void] $selectedMenuIds.Add([long] $row.Value.id)
            $changed = $true
        }
    }
}
$selectedMenus = @($selectedMenuIds | ForEach-Object { $menuById[$_] } | Sort-Object { [long] $_.Value.id })

$expected = @{
    MenuTotal = 294
    HrmPermissions = 146
    FmsPermissions = 101
    HrmDictTypes = 43
    FmsDictTypes = 17
    HrmDictData = 169
    FmsDictData = 75
}
$actual = @{
    MenuTotal = $selectedMenus.Count
    HrmPermissions = @($selectedMenus | Where-Object { ([string] $_.Value.permission).StartsWith('hrm:') }).Count
    FmsPermissions = @($selectedMenus | Where-Object { ([string] $_.Value.permission).StartsWith('fms:') }).Count
    HrmDictTypes = @($dictTypeRows | Where-Object { $_.Value.type.StartsWith('hrm_') }).Count
    FmsDictTypes = @($dictTypeRows | Where-Object { $_.Value.type.StartsWith('fms_') }).Count
    HrmDictData = @($dictDataRows | Where-Object { $_.Value.dict_type.StartsWith('hrm_') }).Count
    FmsDictData = @($dictDataRows | Where-Object { $_.Value.dict_type.StartsWith('fms_') }).Count
}
foreach ($key in $expected.Keys) {
    if ($actual[$key] -ne $expected[$key]) {
        throw "Unexpected upstream $key count: expected $($expected[$key]), got $($actual[$key])"
    }
}

$hrmDeleteOrder = @(
    'hrm_performance_assessment_quota_score', 'hrm_performance_assessment_action_record',
    'hrm_performance_assessment_appeal_record', 'hrm_performance_assessment_quota',
    'hrm_performance_assessment_dimension', 'hrm_performance_assessment_stage',
    'hrm_performance_assessment', 'hrm_performance_plan', 'hrm_performance_result_template',
    'hrm_performance_assessment_template', 'hrm_insurance_month_employee_record',
    'hrm_insurance_month_record', 'hrm_insurance_employee_info', 'hrm_insurance_scheme_project',
    'hrm_insurance_scheme', 'hrm_salary_slip', 'hrm_salary_slip_send_record',
    'hrm_salary_month_employee_record', 'hrm_salary_month_record', 'hrm_salary_change_record',
    'hrm_salary_employee_info', 'hrm_salary_group', 'hrm_salary_tax_rule', 'hrm_salary_option',
    'hrm_salary_config', 'hrm_salary_change_template', 'hrm_employee_personal_note',
    'hrm_employee_file', 'hrm_employee_salary_card', 'hrm_employee_quit_info',
    'hrm_employee_change_record', 'hrm_employee_contract', 'hrm_employee_contact',
    'hrm_employee_certificate', 'hrm_employee_training_experience',
    'hrm_employee_work_experience', 'hrm_employee_education_experience', 'hrm_attendance_leave',
    'hrm_attendance_clock', 'hrm_employee', 'hrm_attendance_group', 'hrm_attendance_holiday',
    'hrm_recruit_interview', 'hrm_recruit_candidate', 'hrm_recruit_post',
    'hrm_recruit_channel', 'hrm_recruit_post_type', 'hrm_config'
)
$fmsDeleteOrder = @(
    'fms_closing_voucher', 'fms_closing', 'fms_closing_period', 'fms_closing_template',
    'fms_cash_flow_extend_data', 'fms_cash_flow_extend_config', 'fms_cash_flow_statement_report',
    'fms_cash_flow_statement_config', 'fms_income_statement_report', 'fms_income_statement_config',
    'fms_balance_sheet_report', 'fms_balance_sheet_config', 'fms_voucher_entry', 'fms_voucher',
    'fms_voucher_template', 'fms_voucher_template_category', 'fms_digest',
    'fms_finance_indicator', 'fms_voucher_word', 'fms_initial_balance', 'fms_assist_combination',
    'fms_auxiliary_item', 'fms_auxiliary_type', 'fms_subject', 'fms_currency',
    'fms_finance_parameter', 'fms_account_user', 'fms_account_set'
)

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('-- V058: repair imported HRM/FMS tenant data and install authoritative metadata.')
$lines.Add('-- Source: Yudao upstream MySQL baseline at commit 2bbe79b34ab8c9c7b0148300599dc8d4881c8db1.')
$lines.Add('-- Dependencies/order: apply after V057 and after the HRM/FMS business tables are installed.')
$lines.Add('-- Destructive scope: physically deletes HRM rows for tenants 0, 1, and 121, except the explicit global salary-slip template; physically deletes FMS rows for tenants 1 and 121.')
$lines.Add('-- Preserved scope: System users/departments/posts/roles/tenants, ZSJOS rows, fms_subject_template, fms_report_template, hrm_salary_option_template, and hrm_salary_slip_template id 1 for tenant 0.')
$lines.Add('-- Metadata scope: installs 294 remapped menu nodes, 60 dictionary types, 244 dictionary entries, and grants those menus only to tenant 1 super_admin.')
$lines.Add('-- Repeatability: destructive and metadata statements run only while V058 is absent; the version rows are recorded in the same transaction.')
$lines.Add('-- Rollback: restore the pre-migration logical backup. Deleted business/demo rows cannot be reconstructed from this migration.')
$lines.Add('-- Execution requirement: take and verify an external backup before applying this file.')
$lines.Add('')
$lines.Add("SET @v058_apply := NOT EXISTS (SELECT 1 FROM ``zsjos_schema_version`` WHERE ``version`` = 'V058');")
$lines.Add('START TRANSACTION;')
$lines.Add('')
$lines.Add('-- Delete HRM imported tenant/demo rows from children to parents.')
foreach ($table in $hrmDeleteOrder) {
    Add-GuardedDelete $lines $table 'tenant_id IN (0, 1, 121)'
}
Add-GuardedDelete $lines 'hrm_salary_slip_template' 'tenant_id IN (1, 121) OR (tenant_id = 0 AND id <> 1)'
$lines.Add('')
$lines.Add('-- Delete FMS imported tenant/demo rows while preserving global subject and report templates.')
foreach ($table in $fmsDeleteOrder) {
    Add-GuardedDelete $lines $table 'tenant_id IN (1, 121)'
}
$lines.Add('')
$lines.Add('-- Install HRM/FMS dictionary types and entries by stable protocol keys; IDs remain local auto-increment values.')
foreach ($row in $dictTypeRows | Sort-Object { [string] $_.Value.type }) {
    $columns = @($row.Raw.Keys | Where-Object { $_ -ne 'id' })
    $columnSql = ($columns | ForEach-Object { "``$_``" }) -join ', '
    $valueSql = ($columns | ForEach-Object { $row.Raw[$_] }) -join ', '
    $lines.Add("INSERT INTO ``system_dict_type`` ($columnSql) SELECT $valueSql FROM DUAL WHERE @v058_apply = 1 AND NOT EXISTS (SELECT 1 FROM ``system_dict_type`` WHERE ``type`` = $($row.Raw.type) AND ``deleted`` = b'0');")
}
foreach ($row in $dictDataRows | Sort-Object { [string] $_.Value.dict_type }, { [int] $_.Value.sort }, { [long] $_.Value.id }) {
    $columns = @($row.Raw.Keys | Where-Object { $_ -ne 'id' })
    $columnSql = ($columns | ForEach-Object { "``$_``" }) -join ', '
    $valueSql = ($columns | ForEach-Object { $row.Raw[$_] }) -join ', '
    $lines.Add("INSERT INTO ``system_dict_data`` ($columnSql) SELECT $valueSql FROM DUAL WHERE @v058_apply = 1 AND NOT EXISTS (SELECT 1 FROM ``system_dict_data`` WHERE ``dict_type`` = $($row.Raw.dict_type) AND ``value`` = $($row.Raw.value) AND ``deleted`` = b'0');")
}
$lines.Add('')
$lines.Add('-- Remap upstream menu IDs by adding 600000, preserving the full parent-child tree.')
$mappedIds = [System.Collections.Generic.List[string]]::new()
foreach ($row in $selectedMenus) {
    $mappedId = 600000 + [long] $row.Value.id
    $mappedParentId = if ([long] $row.Value.parent_id -eq 0) { 0 } else { 600000 + [long] $row.Value.parent_id }
    $mappedIds.Add([string] $mappedId)
    $columns = @($row.Raw.Keys)
    $values = foreach ($column in $columns) {
        switch ($column) {
            'id' { [string] $mappedId }
            'parent_id' { [string] $mappedParentId }
            'creator' { "'v058'" }
            'create_time' { 'NOW()' }
            'updater' { "'v058'" }
            'update_time' { 'NOW()' }
            default { $row.Raw[$column] }
        }
    }
    $columnSql = ($columns | ForEach-Object { "``$_``" }) -join ', '
    $valueSql = $values -join ', '
    $lines.Add("INSERT INTO ``system_menu`` ($columnSql) SELECT $valueSql FROM DUAL WHERE @v058_apply = 1 AND NOT EXISTS (SELECT 1 FROM ``system_menu`` WHERE ``id`` = $mappedId);")
}
$menuIdSql = $mappedIds -join ', '
$lines.Add('')
$lines.Add('-- Remove any pre-existing grants for this exact remapped menu set, then grant only tenant 1 super_admin.')
$lines.Add("DELETE FROM ``system_role_menu`` WHERE @v058_apply = 1 AND ``menu_id`` IN ($menuIdSql);")
$lines.Add("INSERT INTO ``system_role_menu`` (``role_id``, ``menu_id``, ``creator``, ``create_time``, ``updater``, ``update_time``, ``deleted``, ``tenant_id``) SELECT r.``id``, m.``id``, 'v058', NOW(), 'v058', NOW(), b'0', 1 FROM ``system_role`` r JOIN ``system_menu`` m ON m.``id`` IN ($menuIdSql) AND m.``creator`` = 'v058' AND m.``deleted`` = b'0' WHERE @v058_apply = 1 AND r.``tenant_id`` = 1 AND r.``code`` = 'super_admin' AND r.``deleted`` = b'0' AND NOT EXISTS (SELECT 1 FROM ``system_role_menu`` rm WHERE rm.``role_id`` = r.``id`` AND rm.``menu_id`` = m.``id`` AND rm.``tenant_id`` = 1 AND rm.``deleted`` = b'0');")
$lines.Add('')
$lines.Add("INSERT INTO ``zsjos_schema_version`` (``version``, ``description``, ``checksum``, ``installed_at``) SELECT 'V058', 'HRM/FMS metadata and imported data cleanup', 'V058__hrm_fms_metadata_and_data_cleanup.sql', NOW() FROM DUAL WHERE @v058_apply = 1;")
$lines.Add("INSERT INTO ``zsjos_module_schema_version`` (``module_code``, ``version``, ``description``, ``checksum``, ``release_version``, ``installed_at``) SELECT 'core', 'V058', 'HRM/FMS metadata and imported data cleanup', SHA2('V058__hrm_fms_metadata_and_data_cleanup.sql', 256), 'baseline', NOW() FROM DUAL WHERE @v058_apply = 1;")
$lines.Add('COMMIT;')
$lines.Add('')

$outputPath = [System.IO.Path]::GetFullPath($OutputSql)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($outputPath)) | Out-Null
[System.IO.File]::WriteAllLines($outputPath, $lines, [System.Text.UTF8Encoding]::new($false))

Write-Output "Generated $outputPath"
Write-Output "Menus=$($actual.MenuTotal); HRM permissions=$($actual.HrmPermissions); FMS permissions=$($actual.FmsPermissions)"
Write-Output "Dictionary types=$($dictTypeRows.Count); dictionary entries=$($dictDataRows.Count)"
