use calamine::{open_workbook_auto, Data, Reader};
use regex::Regex;
use rust_xlsxwriter::{Format, FormatAlign, FormatBorder, Workbook, XlsxError};
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::BufReader;
use std::path::Path;

const SUMMARY_FIELDS: [&str; 18] = [
    "应出勤天数(天)",
    "实际出勤天数(天)",
    "缺卡次数(次)",
    "迟到次数(次)",
    "迟到时长(分钟)",
    "早退次数(次)",
    "早退时长(分钟)",
    "旷工次数(次)",
    "旷工时长(分钟)",
    "事假(小时)",
    "病假(小时)",
    "年假(小时)",
    "调休假(小时)",
    "调休(小时)",
    "婚假(天)",
    "产假(天)",
    "丧假(天)",
    "加班时长(小时)",
];
const EXPORT_SUMMARY_HEADERS: [(&str, &str); 20] = [
    ("应出勤日", "shouldAttendanceDays"),
    ("实出勤日", "actualAttendanceDays"),
    ("计薪日", "payrollDays"),
    ("入职日期", "entryDate"),
    ("缺卡", "missingCardCount"),
    ("迟到", "lateText"),
    ("迟到是否惩罚", "latePunishment"),
    ("早退", "earlyText"),
    ("旷工", "absenceDays"),
    ("事假/小时", "personalLeaveHours"),
    ("病假/天", "sickLeaveDays"),
    ("工伤/天", "injuryLeaveDays"),
    ("丧假/天", "funeralLeaveDays"),
    ("婚假/天", "marriageLeaveDays"),
    ("产假/天", "maternityLeaveDays"),
    ("调休/小时", "compLeaveHours"),
    ("年假/小时", "annualLeaveHours"),
    ("加班/小时", "overtimeHours"),
    ("加班未调休换算成工资", "overtimeToSalary"),
    ("备注", "remark"),
];
const METADATA_SHEET_NAME: &str = "_attendance_metadata";

pub fn parse_source(source_path: &str) -> Result<Value, String> {
    let mut workbook = open_workbook_auto(source_path).map_err(|error| error.to_string())?;
    let sheet_name = workbook
        .sheet_names()
        .first()
        .cloned()
        .ok_or_else(|| "无法读取企业微信月报工作表".to_string())?;
    let range = workbook
        .worksheet_range(&sheet_name)
        .map_err(|error| error.to_string())?;

    let filename = Path::new(source_path)
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or(source_path);
    let stat_cell = cell_text(&range, 1, 0);
    let (year, month, start_date, end_date) = parse_date_range(filename, &stat_cell)?;

    let columns = build_column_map(&range);
    let day_columns = build_day_columns(&range);
    let days_in_month = days_in_month(year, month);
    let mut employees = Vec::new();
    let mut issues = Vec::new();
    let mut seen_names = HashSet::new();

    for row in 4..range.height() {
        let name = cell_text(&range, row, *columns.get("姓名").unwrap_or(&usize::MAX));
        if name.is_empty() {
            continue;
        }

        if seen_names.contains(&name) {
            issues.push(json!({
                "level": "warning",
                "employee": name,
                "day": null,
                "message": "同名人员出现多条记录",
                "rawValue": ""
            }));
        }
        seen_names.insert(name.clone());

        let mut summaries = serde_json::Map::new();
        for field in SUMMARY_FIELDS {
            if let Some(col) = columns.get(field) {
                summaries.insert(field.to_string(), cell_json(&range, row, *col));
            }
        }

        let mut day_details = serde_json::Map::new();
        for (day, col) in &day_columns {
            if *day <= days_in_month {
                day_details.insert(day.to_string(), json!(cell_text(&range, row, *col)));
            }
        }

        employees.push(json!({
            "name": name,
            "account": cell_text(&range, row, *columns.get("账号").unwrap_or(&usize::MAX)),
            "rule": cell_text(&range, row, *columns.get("所属规则").unwrap_or(&usize::MAX)),
            "department": cell_text(&range, row, *columns.get("部门").unwrap_or(&usize::MAX)),
            "position": cell_text(&range, row, *columns.get("职务").unwrap_or(&usize::MAX)),
            "jobNo": cell_text(&range, row, *columns.get("工号").unwrap_or(&usize::MAX)),
            "summaries": summaries,
            "dayDetails": day_details
        }));
    }

    Ok(json!({
        "attendance": {
            "year": year,
            "month": month,
            "startDate": start_date,
            "endDate": end_date,
            "sourcePath": source_path,
            "sourceFileName": filename,
            "sourceFileHash": "",
            "employees": employees
        },
        "issues": issues
    }))
}

pub fn parse_exported_report(source_path: &str) -> Result<Value, String> {
    let mut workbook = open_workbook_auto(source_path).map_err(|error| error.to_string())?;
    let sheet_name = workbook
        .sheet_names()
        .first()
        .cloned()
        .ok_or_else(exported_report_format_error)?;
    let range = workbook
        .worksheet_range(&sheet_name)
        .map_err(|error| error.to_string())?;
    let (year, month) = parse_exported_sheet_month(&sheet_name)?;
    let days_in_month = days_in_month(year, month);
    validate_exported_report_headers(&range, year, month, days_in_month)?;

    let mut employees = Vec::new();
    for row in 1..range.height() {
        let department = cell_text(&range, row, 1);
        let name = cell_text(&range, row, 2);
        if department.is_empty() && name.is_empty() {
            continue;
        }
        if department.is_empty() || name.is_empty() {
            return Err(format!("正式考勤汇总表第 {} 行缺少姓名或部门。", row + 1));
        }

        let mut days = Vec::new();
        for day in 1..=days_in_month {
            let text = cell_text(&range, row, 3 + day);
            let (morning, afternoon) = parse_exported_status_pair(&text, row + 1, day)?;
            days.push(json!({
                "day": day,
                "morning": morning,
                "afternoon": afternoon
            }));
        }

        let mut summary = serde_json::Map::new();
        let summary_start = 4 + days_in_month;
        for (index, (_, key)) in EXPORT_SUMMARY_HEADERS.iter().enumerate() {
            summary.insert(
                key.to_string(),
                exported_cell_json(&range, row, summary_start + index),
            );
        }

        employees.push(json!({
            "name": name,
            "department": department,
            "sourceIndex": employees.len() + 1,
            "days": days,
            "summary": summary
        }));
    }

    if employees.is_empty() {
        return Err(exported_report_format_error());
    }

    let filename = Path::new(source_path)
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or(source_path);

    let recovered_parameters = parse_exported_metadata(&mut workbook, year, month, &employees)
        .unwrap_or_else(|| infer_recovered_parameters(year, month, &employees, Vec::new()));

    Ok(json!({
        "year": year,
        "month": month,
        "sourceFileName": filename,
        "sourceFileHash": "",
        "employees": employees,
        "recoveredParameters": recovered_parameters
    }))
}

pub fn export_attendance_report(project: &Value, output_path: &str) -> Result<(), String> {
    export_attendance(project, output_path).map_err(|error| error.to_string())
}

pub fn export_issue_list(project: &Value, output_path: &str) -> Result<(), String> {
    export_issues(project, output_path).map_err(|error| error.to_string())
}

fn parse_exported_metadata(
    workbook: &mut calamine::Sheets<BufReader<File>>,
    year: i32,
    month: u32,
    employees: &[Value],
) -> Option<Value> {
    let Ok(range) = workbook.worksheet_range(METADATA_SHEET_NAME) else {
        return None;
    };
    let mut values = HashMap::new();
    for row in 0..range.height() {
        let key = cell_text(&range, row, 0);
        if !key.is_empty() {
            values.insert(key, cell_text(&range, row, 1));
        }
    }

    if values.get("appName").map(String::as_str) != Some("zhongshijian-kaoqintong")
        || values.get("schemaVersion").map(String::as_str) != Some("1")
    {
        return Some(infer_recovered_parameters(
            year,
            month,
            employees,
            vec!["隐藏元数据格式不正确，已改用汇总表推断。".to_string()],
        ));
    }

    let metadata_year = values
        .get("year")
        .and_then(|value| value.parse::<i32>().ok());
    let metadata_month = values
        .get("month")
        .and_then(|value| value.parse::<u32>().ok());
    if metadata_year != Some(year) || metadata_month != Some(month) {
        return Some(infer_recovered_parameters(
            year,
            month,
            employees,
            vec!["隐藏元数据年月与工作表不一致，已改用汇总表推断。".to_string()],
        ));
    }

    Some(json!({
        "source": "metadata",
        "shouldAttendanceDays": values.get("shouldAttendanceDays").and_then(|value| parse_optional_number(value)),
        "payrollDays": values.get("payrollDays").and_then(|value| parse_optional_number(value)),
        "restDays": values.get("restDays").map(|value| parse_day_list(value)).unwrap_or_default(),
        "paidRestDays": values.get("paidRestDays").map(|value| parse_day_list(value)).unwrap_or_default(),
        "restDayCandidates": [],
        "paidRestDayCandidates": [],
        "warnings": []
    }))
}

fn infer_recovered_parameters(
    year: i32,
    month: u32,
    employees: &[Value],
    mut warnings: Vec<String>,
) -> Value {
    let should_attendance_days = common_summary_number(employees, "shouldAttendanceDays");
    let payroll_days = common_summary_number(employees, "payrollDays");
    if should_attendance_days.1 {
        warnings.push("不同员工的应出勤日不一致，请手工确认。".to_string());
    }
    if payroll_days.1 {
        warnings.push("不同员工的计薪日不一致，请手工确认。".to_string());
    }

    let rest_day_candidates = infer_day_candidates(year, month, employees, "休息");
    let paid_rest_day_candidates = infer_day_candidates(year, month, employees, "带薪");
    let rest_days = candidate_day_set(&rest_day_candidates);
    let paid_days = candidate_day_set(&paid_rest_day_candidates);
    for day in rest_days.intersection(&paid_days) {
        warnings.push(format!(
            "{day} 日同时符合休息日和带薪休息日候选，请手工确认。"
        ));
    }

    json!({
        "source": if employees.is_empty() { "none" } else { "inferred" },
        "shouldAttendanceDays": should_attendance_days.0,
        "payrollDays": payroll_days.0,
        "restDays": [],
        "paidRestDays": [],
        "restDayCandidates": rest_day_candidates,
        "paidRestDayCandidates": paid_rest_day_candidates,
        "warnings": warnings
    })
}

fn common_summary_number(employees: &[Value], field: &str) -> (Value, bool) {
    let mut values = HashSet::new();
    for employee in employees {
        if let Some(number) = employee
            .get("summary")
            .and_then(|summary| summary.get(field))
            .and_then(number_from_value)
        {
            values.insert(number_text(number));
        }
    }
    if values.is_empty() {
        return (Value::Null, false);
    }
    if values.len() == 1 {
        let value = values
            .into_iter()
            .next()
            .and_then(|item| item.parse::<f64>().ok())
            .unwrap_or_default();
        return (json!(value), false);
    }
    (Value::Null, true)
}

fn infer_day_candidates(year: i32, month: u32, employees: &[Value], status: &str) -> Vec<Value> {
    let total_employees = employees.len();
    if total_employees == 0 {
        return Vec::new();
    }

    let mut candidates = Vec::new();
    for day in 1..=days_in_month(year, month) {
        let mut matched_employees = 0;
        for employee in employees {
            let matched = employee
                .get("days")
                .and_then(Value::as_array)
                .and_then(|days| {
                    days.iter()
                        .find(|item| item.get("day").and_then(Value::as_u64) == Some(day as u64))
                })
                .is_some_and(|item| {
                    text(item, "morning") == status && text(item, "afternoon") == status
                });
            if matched {
                matched_employees += 1;
            }
        }

        if matched_employees == total_employees {
            candidates.push(json!({
                "day": day,
                "confidence": "high",
                "matchedEmployees": matched_employees,
                "totalEmployees": total_employees
            }));
        } else if (matched_employees as f64) / (total_employees as f64) >= 0.8 {
            candidates.push(json!({
                "day": day,
                "confidence": "medium",
                "matchedEmployees": matched_employees,
                "totalEmployees": total_employees
            }));
        }
    }
    candidates
}

fn candidate_day_set(candidates: &[Value]) -> HashSet<u64> {
    candidates
        .iter()
        .filter_map(|item| item.get("day").and_then(Value::as_u64))
        .collect()
}

fn parse_optional_number(value: &str) -> Option<f64> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return None;
    }
    trimmed.parse::<f64>().ok()
}

fn parse_day_list(value: &str) -> Vec<u64> {
    value
        .split(',')
        .filter_map(|item| item.trim().parse::<u64>().ok())
        .filter(|day| (1..=31).contains(day))
        .collect()
}

fn number_from_value(value: &Value) -> Option<f64> {
    if let Some(number) = value.as_f64() {
        return Some(number);
    }
    value
        .as_str()
        .and_then(|text| text.trim().parse::<f64>().ok())
}

fn write_metadata_worksheet(
    workbook: &mut Workbook,
    project: &Value,
    year: i32,
    month: u32,
) -> Result<(), XlsxError> {
    let metadata = workbook.add_worksheet();
    metadata.set_name(METADATA_SHEET_NAME)?;
    metadata.set_very_hidden(true);
    let rows = [
        ("schemaVersion", "1".to_string()),
        ("appName", "zhongshijian-kaoqintong".to_string()),
        ("year", year.to_string()),
        ("month", month.to_string()),
        ("sourceKind", text(project, "sourceKind").to_string()),
        (
            "shouldAttendanceDays",
            metadata_number_text(project.get("shouldAttendanceDays")),
        ),
        (
            "payrollDays",
            metadata_number_text(project.get("payrollDays")),
        ),
        ("restDays", metadata_day_list(project.get("restDays"))),
        (
            "paidRestDays",
            metadata_day_list(project.get("paidRestDays")),
        ),
    ];
    for (row, (key, value)) in rows.iter().enumerate() {
        metadata.write_string(row as u32, 0, *key)?;
        metadata.write_string(row as u32, 1, value)?;
    }
    Ok(())
}

fn metadata_number_text(value: Option<&Value>) -> String {
    value
        .and_then(number_from_value)
        .map(number_text)
        .unwrap_or_default()
}

fn metadata_day_list(value: Option<&Value>) -> String {
    value
        .and_then(Value::as_array)
        .map(|items| {
            items
                .iter()
                .filter_map(|item| item.as_u64().map(|day| day.to_string()))
                .collect::<Vec<_>>()
                .join(",")
        })
        .unwrap_or_default()
}

fn export_attendance(project: &Value, output_path: &str) -> Result<(), XlsxError> {
    let mut workbook = Workbook::new();
    let worksheet = workbook.add_worksheet();
    let year = project.get("year").and_then(Value::as_i64).unwrap_or(0) as i32;
    let month = project.get("month").and_then(Value::as_u64).unwrap_or(0) as u32;
    worksheet.set_name(format!("{year}年{month}月考勤"))?;

    let header = Format::new()
        .set_bold()
        .set_align(FormatAlign::Center)
        .set_align(FormatAlign::VerticalCenter)
        .set_text_wrap()
        .set_border(FormatBorder::Thin);
    let front_column = Format::new()
        .set_align(FormatAlign::Center)
        .set_align(FormatAlign::VerticalCenter)
        .set_text_wrap()
        .set_border(FormatBorder::Thin);
    let summary_cell = Format::new()
        .set_align(FormatAlign::Center)
        .set_align(FormatAlign::VerticalCenter)
        .set_text_wrap()
        .set_border(FormatBorder::Thin);
    let status_formats = StatusFormats::new();

    let leading_headers = ["序号", "部门", "姓名", "考勤"];
    for (col, title) in leading_headers.iter().enumerate() {
        worksheet.write_string_with_format(0, col as u16, *title, &header)?;
    }

    let valid_days = days_in_month(year, month).min(31);
    let summary_start_col = summary_start_col(year, month);
    apply_attendance_layout(worksheet, valid_days, summary_start_col)?;
    for day in 1..=valid_days {
        let title = date_header(year, month, day as u32);
        worksheet.write_string_with_format(0, 3 + day as u16, &title, &header)?;
    }

    let summary_headers = [
        "应出勤日",
        "实出勤日",
        "计薪日",
        "入职日期",
        "缺卡",
        "迟到",
        "迟到是否惩罚",
        "早退",
        "旷工",
        "事假/小时",
        "病假/天",
        "工伤/天",
        "丧假/天",
        "婚假/天",
        "产假/天",
        "调休/小时",
        "年假/小时",
        "加班/小时",
        "加班未调休换算成工资",
        "备注",
    ];
    for (index, title) in summary_headers.iter().enumerate() {
        worksheet.write_string_with_format(0, summary_start_col + index as u16, *title, &header)?;
    }

    let employees = project
        .get("employees")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    let employees = sorted_enabled_employees(employees);
    let mut row = 1_u32;
    let mut index = 1;
    for employee in &employees {
        worksheet.set_row_height(row, 38)?;
        let department = text(employee, "displayDepartment").to_string();

        worksheet.write_string_with_format(row, 0, &index.to_string(), &front_column)?;
        worksheet.write_string_with_format(row, 1, blank_text(&department), &front_column)?;
        worksheet.write_string_with_format(
            row,
            2,
            blank_text(text(employee, "displayName")),
            &front_column,
        )?;
        worksheet.write_string_with_format(row, 3, "考勤", &front_column)?;

        if let Some(days) = employee.get("days").and_then(Value::as_array) {
            for day in days {
                let day_num = day.get("day").and_then(Value::as_u64).unwrap_or(0);
                if day_num == 0 || day_num > valid_days as u64 {
                    continue;
                }
                let col = 3 + day_num as u16;
                write_combined_status(
                    worksheet,
                    row,
                    col,
                    text(day, "finalMorning"),
                    text(day, "finalAfternoon"),
                    &status_formats,
                )?;
            }
        }

        write_summary(worksheet, row, employee, summary_start_col, &summary_cell)?;
        row += 1;
        index += 1;
    }

    write_metadata_worksheet(&mut workbook, project, year, month)?;
    workbook.save(output_path)
}

fn apply_attendance_layout(
    worksheet: &mut rust_xlsxwriter::Worksheet,
    valid_days: usize,
    summary_start_col: u16,
) -> Result<(), XlsxError> {
    worksheet.set_row_height(0, 34)?;
    worksheet.set_column_width(0, 6)?;
    worksheet.set_column_width(1, 16)?;
    worksheet.set_column_width(2, 12)?;
    worksheet.set_column_width(3, 8)?;
    for day in 1..=valid_days {
        worksheet.set_column_width(3 + day as u16, 18)?;
    }

    let summary_widths = [
        10.0, 10.0, 9.0, 12.0, 8.0, 10.0, 14.0, 10.0, 10.0, 12.0, 10.0, 10.0, 10.0, 10.0, 10.0,
        12.0, 12.0, 12.0, 26.0, 18.0,
    ];
    for (index, width) in summary_widths.iter().enumerate() {
        worksheet.set_column_width(summary_start_col + index as u16, *width)?;
    }
    Ok(())
}

fn sorted_enabled_employees(employees: Vec<Value>) -> Vec<Value> {
    let mut rows = employees
        .into_iter()
        .enumerate()
        .filter(|(_, employee)| {
            employee
                .get("enabled")
                .and_then(Value::as_bool)
                .unwrap_or(true)
        })
        .collect::<Vec<_>>();

    let mut department_order: HashMap<String, usize> = HashMap::new();
    for (_, employee) in &rows {
        let department = text(employee, "displayDepartment").to_string();
        if !department_order.contains_key(&department) {
            department_order.insert(department, department_order.len());
        }
    }

    rows.sort_by(|(left_position, left), (right_position, right)| {
        let left_department = text(left, "displayDepartment");
        let right_department = text(right, "displayDepartment");
        let left_order = department_order
            .get(left_department)
            .copied()
            .unwrap_or(usize::MAX);
        let right_order = department_order
            .get(right_department)
            .copied()
            .unwrap_or(usize::MAX);

        left_order
            .cmp(&right_order)
            .then_with(|| source_index(left).cmp(&source_index(right)))
            .then_with(|| left_position.cmp(right_position))
    });

    rows.into_iter().map(|(_, employee)| employee).collect()
}

fn source_index(employee: &Value) -> u64 {
    employee
        .get("sourceIndex")
        .and_then(Value::as_u64)
        .unwrap_or(u64::MAX)
}

fn export_issues(project: &Value, output_path: &str) -> Result<(), XlsxError> {
    let mut workbook = Workbook::new();
    let worksheet = workbook.add_worksheet();
    worksheet.set_name("异常信息")?;
    let header = Format::new()
        .set_bold()
        .set_align(FormatAlign::Center)
        .set_align(FormatAlign::VerticalCenter)
        .set_text_wrap()
        .set_border(FormatBorder::Thin);
    let issue_cell = Format::new()
        .set_align(FormatAlign::VerticalCenter)
        .set_text_wrap()
        .set_border(FormatBorder::Thin);
    let headers = [
        "序号",
        "员工",
        "部门",
        "日期",
        "异常类型",
        "原始明细",
        "系统判断",
        "修改前",
        "修改后",
        "备注",
        "确认时间",
    ];
    apply_issues_layout(worksheet)?;
    for (col, title) in headers.iter().enumerate() {
        worksheet.write_string_with_format(0, col as u16, *title, &header)?;
    }

    let mut row = 1_u32;
    let mut index = 1;
    let confirmed_at = chrono_like_now();
    let employees = project
        .get("employees")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default();
    for employee in employees {
        let enabled = employee
            .get("enabled")
            .and_then(Value::as_bool)
            .unwrap_or(true);
        if !enabled {
            write_issue_row(
                worksheet,
                row,
                index,
                text(&employee, "displayName"),
                text(&employee, "displayDepartment"),
                "",
                "未导出人员",
                "",
                "源表存在该员工",
                "导出",
                "不导出",
                text(&employee, "notes"),
                &confirmed_at,
                &issue_cell,
            )?;
            row += 1;
            index += 1;
        }

        if let Some(days) = employee.get("days").and_then(Value::as_array) {
            for day in days {
                let issues = day
                    .get("issues")
                    .and_then(Value::as_array)
                    .cloned()
                    .unwrap_or_default();
                let reportable_issues = issues
                    .iter()
                    .filter(|issue| !is_manual_review_issue(issue))
                    .cloned()
                    .collect::<Vec<_>>();
                if !should_export_day_issue(day, reportable_issues.len()) {
                    continue;
                }

                let issue_type = if reportable_issues.is_empty() {
                    "人工修改".to_string()
                } else {
                    reportable_issues
                        .iter()
                        .filter_map(|issue| issue.get("message").and_then(Value::as_str))
                        .collect::<Vec<_>>()
                        .join("；")
                };

                let before = format!(
                    "{} / {}",
                    blank_text(text(day, "systemMorning")),
                    blank_text(text(day, "systemAfternoon"))
                );
                let after = format!(
                    "{} / {}",
                    blank_text(text(day, "finalMorning")),
                    blank_text(text(day, "finalAfternoon"))
                );
                let note = [text(day, "morningNote"), text(day, "afternoonNote")]
                    .into_iter()
                    .filter(|value| !value.is_empty())
                    .collect::<Vec<_>>()
                    .join("；");
                write_issue_row(
                    worksheet,
                    row,
                    index,
                    text(&employee, "displayName"),
                    text(&employee, "displayDepartment"),
                    &day.get("day")
                        .and_then(Value::as_u64)
                        .map(|value| value.to_string())
                        .unwrap_or_default(),
                    &issue_type,
                    text(day, "rawDetail"),
                    &before,
                    &before,
                    &after,
                    &note,
                    &confirmed_at,
                    &issue_cell,
                )?;
                row += 1;
                index += 1;
            }
        }
    }

    workbook.save(output_path)
}

fn apply_issues_layout(worksheet: &mut rust_xlsxwriter::Worksheet) -> Result<(), XlsxError> {
    worksheet.set_row_height(0, 28)?;
    for (col, width) in [
        6.0, 12.0, 16.0, 10.0, 14.0, 28.0, 16.0, 16.0, 16.0, 26.0, 24.0,
    ]
    .iter()
    .enumerate()
    {
        worksheet.set_column_width(col as u16, *width)?;
    }
    Ok(())
}

fn should_export_day_issue(day: &Value, issue_count: usize) -> bool {
    if !day_has_abnormal_status(day) {
        return false;
    }

    let changed = text(day, "systemMorning") != text(day, "finalMorning")
        || text(day, "systemAfternoon") != text(day, "finalAfternoon")
        || !text(day, "morningNote").is_empty()
        || !text(day, "afternoonNote").is_empty();
    issue_count > 0 || changed
}

fn is_manual_review_issue(issue: &Value) -> bool {
    issue
        .get("message")
        .and_then(Value::as_str)
        .is_some_and(|message| message == "人工修改，汇总数据待核对")
}

fn day_has_abnormal_status(day: &Value) -> bool {
    [
        "systemMorning",
        "systemAfternoon",
        "finalMorning",
        "finalAfternoon",
    ]
    .iter()
    .any(|field| text(day, field) == "异常")
}

fn build_column_map(range: &calamine::Range<Data>) -> HashMap<String, usize> {
    let mut columns = HashMap::new();
    for col in 0..range.width() {
        let group = cell_text(range, 2, col);
        if !group.is_empty() {
            columns.insert(group, col);
        }
        let header = cell_text(range, 3, col);
        if !header.is_empty() {
            columns.insert(header, col);
        }
    }
    columns
}

fn build_day_columns(range: &calamine::Range<Data>) -> HashMap<usize, usize> {
    let mut columns = HashMap::new();
    let re = Regex::new(r"^\s*(\d{1,2})\s*(?:\n|$)").unwrap();
    for col in 0..range.width() {
        let header = cell_text(range, 3, col);
        if let Some(captures) = re.captures(&header) {
            if let Ok(day) = captures[1].parse::<usize>() {
                columns.insert(day, col);
            }
        }
    }
    columns
}

fn parse_date_range(filename: &str, stat_cell: &str) -> Result<(i32, u32, String, String), String> {
    let filename_re = Regex::new(r"(20\d{2})(\d{2})(\d{2})-(20\d{2})(\d{2})(\d{2})").unwrap();
    if let Some(captures) = filename_re.captures(filename) {
        let year = captures[1]
            .parse::<i32>()
            .map_err(|error| error.to_string())?;
        let month = captures[2]
            .parse::<u32>()
            .map_err(|error| error.to_string())?;
        return Ok((
            year,
            month,
            format!("{}-{}-{}", &captures[1], &captures[2], &captures[3]),
            format!("{}-{}-{}", &captures[4], &captures[5], &captures[6]),
        ));
    }

    let stat_re = Regex::new(r"统计时间:(\d{2})-(\d{2})\s*[～~-]\s*(\d{2})-(\d{2})").unwrap();
    if let Some(captures) = stat_re.captures(stat_cell) {
        let year = 2026;
        let month = captures[1]
            .parse::<u32>()
            .map_err(|error| error.to_string())?;
        return Ok((
            year,
            month,
            format!("{year}-{}-{}", &captures[1], &captures[2]),
            format!("{year}-{}-{}", &captures[3], &captures[4]),
        ));
    }

    Err("无法从文件名或统计时间识别月份".to_string())
}

fn parse_exported_sheet_month(sheet_name: &str) -> Result<(i32, u32), String> {
    let re = Regex::new(r"^(20\d{2})年(\d{1,2})月考勤$").unwrap();
    let Some(captures) = re.captures(sheet_name) else {
        return Err(exported_report_format_error());
    };
    let year = captures[1]
        .parse::<i32>()
        .map_err(|_| exported_report_format_error())?;
    let month = captures[2]
        .parse::<u32>()
        .map_err(|_| exported_report_format_error())?;
    if !(1..=12).contains(&month) {
        return Err(exported_report_format_error());
    }
    Ok((year, month))
}

fn validate_exported_report_headers(
    range: &calamine::Range<Data>,
    year: i32,
    month: u32,
    days_in_month: usize,
) -> Result<(), String> {
    for (col, header) in ["序号", "部门", "姓名", "考勤"].iter().enumerate() {
        if cell_text(range, 0, col) != *header {
            return Err(exported_report_format_error());
        }
    }

    for day in 1..=days_in_month {
        if cell_text(range, 0, 3 + day) != date_header(year, month, day as u32) {
            return Err(exported_report_format_error());
        }
    }

    let summary_start = 4 + days_in_month;
    for (index, (header, _)) in EXPORT_SUMMARY_HEADERS.iter().enumerate() {
        if cell_text(range, 0, summary_start + index) != *header {
            return Err(exported_report_format_error());
        }
    }

    Ok(())
}

fn parse_exported_status_pair(
    text: &str,
    row: usize,
    day: usize,
) -> Result<(String, String), String> {
    let re = Regex::new(r"上午[:：]\s*([^\r\n]+)\s*[\r\n]+\s*下午[:：]\s*([^\r\n]+)").unwrap();
    let Some(captures) = re.captures(text) else {
        return Err(format!(
            "正式考勤汇总表第 {row} 行 {day} 日状态格式不正确。"
        ));
    };
    let morning = normalize_exported_status(&captures[1], row, day, "上午")?;
    let afternoon = normalize_exported_status(&captures[2], row, day, "下午")?;
    Ok((morning, afternoon))
}

fn normalize_exported_status(
    value: &str,
    row: usize,
    day: usize,
    half: &str,
) -> Result<String, String> {
    let status = value.trim();
    let normalized = match status {
        "--" => "",
        "带薪假" => "带薪",
        _ => status,
    };
    if is_allowed_exported_status(normalized) {
        return Ok(normalized.to_string());
    }
    Err(format!(
        "正式考勤汇总表第 {row} 行 {day} 日{half}状态不支持：{status}"
    ))
}

fn is_allowed_exported_status(value: &str) -> bool {
    matches!(
        value,
        "" | "出勤"
            | "休息"
            | "带薪"
            | "迟到"
            | "早退"
            | "缺卡"
            | "异常"
            | "加班"
            | "旷工"
            | "事假"
            | "病假"
            | "年假"
            | "调休"
            | "婚假"
            | "产假"
            | "丧假"
            | "工伤"
    )
}

fn exported_report_format_error() -> String {
    "请选择由中世健考勤通导出的正式考勤汇总表。".to_string()
}

fn cell_text(range: &calamine::Range<Data>, row: usize, col: usize) -> String {
    if col == usize::MAX {
        return String::new();
    }
    match range.get_value((row as u32, col as u32)) {
        Some(Data::String(value)) => value.trim().to_string(),
        Some(Data::Float(value)) => number_text(*value),
        Some(Data::Int(value)) => value.to_string(),
        Some(Data::Bool(value)) => value.to_string(),
        Some(Data::DateTime(value)) => value.to_string(),
        Some(Data::DateTimeIso(value)) => value.trim().to_string(),
        Some(Data::DurationIso(value)) => value.trim().to_string(),
        Some(Data::Error(value)) => value.to_string(),
        Some(Data::Empty) | None => String::new(),
    }
}

fn cell_json(range: &calamine::Range<Data>, row: usize, col: usize) -> Value {
    match range.get_value((row as u32, col as u32)) {
        Some(Data::Float(value)) => json!(value),
        Some(Data::Int(value)) => json!(value),
        Some(Data::Bool(value)) => json!(value),
        Some(Data::Empty) | None => Value::Null,
        _ => json!(cell_text(range, row, col)),
    }
}

fn exported_cell_json(range: &calamine::Range<Data>, row: usize, col: usize) -> Value {
    match range.get_value((row as u32, col as u32)) {
        Some(Data::Float(value)) => json!(value),
        Some(Data::Int(value)) => json!(value),
        Some(Data::Bool(value)) => json!(value),
        Some(Data::Empty) | None => Value::Null,
        _ => {
            let text = cell_text(range, row, col);
            if text.is_empty() || text == "--" {
                Value::Null
            } else {
                json!(text)
            }
        }
    }
}

fn number_text(value: f64) -> String {
    if value.fract() == 0.0 {
        format!("{}", value as i64)
    } else {
        value.to_string()
    }
}

fn days_in_month(year: i32, month: u32) -> usize {
    match month {
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        4 | 6 | 9 | 11 => 30,
        2 if is_leap_year(year) => 29,
        2 => 28,
        _ => 31,
    }
}

fn is_leap_year(year: i32) -> bool {
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

fn date_header(year: i32, month: u32, day: u32) -> String {
    if year <= 0 || !(1..=12).contains(&month) {
        return day.to_string();
    }
    format!("{month}月{day}日{}", weekday_name(year, month, day))
}

fn summary_start_col(year: i32, month: u32) -> u16 {
    4 + days_in_month(year, month).min(31) as u16
}

fn weekday_name(year: i32, month: u32, day: u32) -> &'static str {
    let month_offsets = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4];
    let mut adjusted_year = year;
    if month < 3 {
        adjusted_year -= 1;
    }
    let weekday = (adjusted_year + adjusted_year / 4 - adjusted_year / 100
        + adjusted_year / 400
        + month_offsets[(month - 1) as usize]
        + day as i32)
        .rem_euclid(7);

    match weekday {
        0 => "星期日",
        1 => "星期一",
        2 => "星期二",
        3 => "星期三",
        4 => "星期四",
        5 => "星期五",
        _ => "星期六",
    }
}

fn write_combined_status(
    worksheet: &mut rust_xlsxwriter::Worksheet,
    row: u32,
    col: u16,
    morning: &str,
    afternoon: &str,
    formats: &StatusFormats,
) -> Result<(), XlsxError> {
    let value = combined_status_text(morning, afternoon);
    worksheet
        .write_string_with_format(
            row,
            col,
            &value,
            formats.for_pair(display_status_text(morning), display_status_text(afternoon)),
        )
        .map(|_| ())
}

fn combined_status_text(morning: &str, afternoon: &str) -> String {
    format!(
        "上午：{}\n下午：{}",
        blank_text(display_status_text(morning)),
        blank_text(display_status_text(afternoon))
    )
}

struct StatusFormats {
    normal: Format,
    late: Format,
    missing_card: Format,
    personal_leave: Format,
    benefit_leave: Format,
    early_leave: Format,
    abnormal: Format,
    absence: Format,
}

impl StatusFormats {
    fn new() -> Self {
        Self {
            normal: status_format(),
            late: status_format()
                .set_bold()
                .set_font_color("#9C6500")
                .set_background_color(status_background_color(StatusStyleKey::Late).unwrap()),
            missing_card: status_format()
                .set_bold()
                .set_font_color("#9C0006")
                .set_background_color(
                    status_background_color(StatusStyleKey::MissingCard).unwrap(),
                ),
            personal_leave: status_format()
                .set_bold()
                .set_font_color("#1F4E79")
                .set_background_color(
                    status_background_color(StatusStyleKey::PersonalLeave).unwrap(),
                ),
            benefit_leave: status_format()
                .set_bold()
                .set_font_color("#0F5132")
                .set_background_color(
                    status_background_color(StatusStyleKey::BenefitLeave).unwrap(),
                ),
            early_leave: status_format()
                .set_bold()
                .set_font_color("#FF6600")
                .set_background_color(status_background_color(StatusStyleKey::EarlyLeave).unwrap()),
            abnormal: status_format()
                .set_bold()
                .set_font_color("#C000FF")
                .set_background_color(status_background_color(StatusStyleKey::Abnormal).unwrap()),
            absence: status_format()
                .set_bold()
                .set_font_color("#FFFFFF")
                .set_background_color(status_background_color(StatusStyleKey::Absence).unwrap()),
        }
    }

    fn for_pair(&self, morning: &str, afternoon: &str) -> &Format {
        match status_style_key_for_pair(morning, afternoon) {
            StatusStyleKey::Normal => &self.normal,
            StatusStyleKey::Late => &self.late,
            StatusStyleKey::MissingCard => &self.missing_card,
            StatusStyleKey::PersonalLeave => &self.personal_leave,
            StatusStyleKey::BenefitLeave => &self.benefit_leave,
            StatusStyleKey::EarlyLeave => &self.early_leave,
            StatusStyleKey::Abnormal => &self.abnormal,
            StatusStyleKey::Absence => &self.absence,
        }
    }
}

fn status_format() -> Format {
    Format::new()
        .set_align(FormatAlign::Center)
        .set_align(FormatAlign::VerticalCenter)
        .set_text_wrap()
        .set_border(FormatBorder::Thin)
}

fn display_status_text(value: &str) -> &str {
    match value {
        "√" => "出勤",
        "△" | "休" => "休息",
        "带薪假" => "带薪",
        _ => value,
    }
}

#[cfg(test)]
fn is_plain_status(value: &str) -> bool {
    matches!(status_style_key(value), StatusStyleKey::Normal)
}

#[derive(Debug, PartialEq, Eq, Copy, Clone)]
enum StatusStyleKey {
    Normal,
    Late,
    MissingCard,
    PersonalLeave,
    BenefitLeave,
    EarlyLeave,
    Abnormal,
    Absence,
}

fn status_background_color(key: StatusStyleKey) -> Option<&'static str> {
    match key {
        StatusStyleKey::Normal => None,
        StatusStyleKey::Late => Some("#FFEB9C"),
        StatusStyleKey::MissingCard => Some("#FFC7CE"),
        StatusStyleKey::PersonalLeave => Some("#DDEBF7"),
        StatusStyleKey::BenefitLeave => Some("#D9EAD3"),
        StatusStyleKey::EarlyLeave => Some("#FCE4D6"),
        StatusStyleKey::Abnormal => Some("#EADCF8"),
        StatusStyleKey::Absence => Some("#7F1D1D"),
    }
}

fn status_style_key_for_pair(morning: &str, afternoon: &str) -> StatusStyleKey {
    let keys = [status_style_key(morning), status_style_key(afternoon)];
    for preferred in [
        StatusStyleKey::Abnormal,
        StatusStyleKey::Absence,
        StatusStyleKey::MissingCard,
        StatusStyleKey::PersonalLeave,
        StatusStyleKey::Late,
        StatusStyleKey::EarlyLeave,
        StatusStyleKey::BenefitLeave,
    ] {
        if keys.contains(&preferred) {
            return preferred;
        }
    }
    StatusStyleKey::Normal
}

fn status_style_key(value: &str) -> StatusStyleKey {
    match value {
        "出勤" | "休息" | "带薪" => StatusStyleKey::Normal,
        "迟到" => StatusStyleKey::Late,
        "缺卡" => StatusStyleKey::MissingCard,
        "事假" => StatusStyleKey::PersonalLeave,
        "病假" | "工伤" | "丧假" | "婚假" | "产假" | "年假" => {
            StatusStyleKey::BenefitLeave
        }
        "早退" => StatusStyleKey::EarlyLeave,
        "异常" => StatusStyleKey::Abnormal,
        "旷工" => StatusStyleKey::Absence,
        _ => StatusStyleKey::Normal,
    }
}

fn write_summary(
    worksheet: &mut rust_xlsxwriter::Worksheet,
    row: u32,
    employee: &Value,
    start_col: u16,
    format: &Format,
) -> Result<(), XlsxError> {
    let fields = [
        "shouldAttendanceDays",
        "actualAttendanceDays",
        "payrollDays",
        "entryDate",
        "missingCardCount",
        "lateText",
        "latePunishment",
        "earlyText",
        "absenceDays",
        "personalLeaveHours",
        "sickLeaveDays",
        "injuryLeaveDays",
        "funeralLeaveDays",
        "marriageLeaveDays",
        "maternityLeaveDays",
        "compLeaveHours",
        "annualLeaveHours",
        "overtimeHours",
        "overtimeToSalary",
        "remark",
    ];
    let Some(summary) = employee.get("summary") else {
        return Ok(());
    };
    for (index, field) in fields.iter().enumerate() {
        let col = start_col + index as u16;
        if let Some(value) = summary.get(*field) {
            write_value(worksheet, row, col, value, format)?;
        }
    }
    Ok(())
}

fn write_value(
    worksheet: &mut rust_xlsxwriter::Worksheet,
    row: u32,
    col: u16,
    value: &Value,
    format: &Format,
) -> Result<(), XlsxError> {
    if let Some(number) = value.as_f64() {
        worksheet
            .write_number_with_format(row, col, number, format)
            .map(|_| ())
    } else if let Some(text) = value.as_str() {
        if text.is_empty() {
            Ok(())
        } else {
            worksheet
                .write_string_with_format(row, col, text, format)
                .map(|_| ())
        }
    } else {
        Ok(())
    }
}

#[allow(clippy::too_many_arguments)]
fn write_issue_row(
    worksheet: &mut rust_xlsxwriter::Worksheet,
    row: u32,
    index: i32,
    employee: &str,
    department: &str,
    day: &str,
    issue_type: &str,
    raw_detail: &str,
    system_result: &str,
    before_value: &str,
    after_value: &str,
    note: &str,
    confirmed_at: &str,
    format: &Format,
) -> Result<(), XlsxError> {
    worksheet.set_row_height(row, 34)?;
    worksheet.write_number_with_format(row, 0, index as f64, format)?;
    worksheet.write_string_with_format(row, 1, blank_text(employee), format)?;
    worksheet.write_string_with_format(row, 2, blank_text(department), format)?;
    worksheet.write_string_with_format(row, 3, blank_text(day), format)?;
    worksheet.write_string_with_format(row, 4, blank_text(issue_type), format)?;
    worksheet.write_string_with_format(row, 5, blank_text(raw_detail), format)?;
    worksheet.write_string_with_format(row, 6, blank_text(system_result), format)?;
    worksheet.write_string_with_format(row, 7, blank_text(before_value), format)?;
    worksheet.write_string_with_format(row, 8, blank_text(after_value), format)?;
    worksheet.write_string_with_format(row, 9, blank_text(note), format)?;
    worksheet.write_string_with_format(row, 10, blank_text(confirmed_at), format)?;
    Ok(())
}

fn text<'a>(value: &'a Value, key: &str) -> &'a str {
    value.get(key).and_then(Value::as_str).unwrap_or("")
}

fn blank_text(value: &str) -> &str {
    if value.is_empty() {
        "--"
    } else {
        value
    }
}

fn chrono_like_now() -> String {
    // Keep the engine dependency-light; exact local display can be improved later.
    format!("{:?}", std::time::SystemTime::now())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn date_header_uses_month_day_and_weekday() {
        assert_eq!(date_header(2026, 6, 1), "6月1日星期一");
        assert_eq!(date_header(2026, 6, 30), "6月30日星期二");
        assert_eq!(date_header(2024, 2, 29), "2月29日星期四");
    }

    #[test]
    fn exported_sheet_month_requires_year() {
        assert_eq!(
            parse_exported_sheet_month("2026年6月考勤").unwrap(),
            (2026, 6)
        );
        assert!(parse_exported_sheet_month("6月考勤").is_err());
    }

    #[test]
    fn exported_attendance_report_can_be_reimported() {
        let path = std::env::temp_dir().join(format!(
            "attendance-export-reimport-{}.xlsx",
            std::process::id()
        ));
        let path_text = path.to_string_lossy().to_string();
        let days = (1..=30)
            .map(|day| {
                if day == 1 {
                    json!({"day": day, "finalMorning": "出勤", "finalAfternoon": "事假"})
                } else {
                    json!({"day": day, "finalMorning": "", "finalAfternoon": ""})
                }
            })
            .collect::<Vec<_>>();
        let project = json!({
            "sourceKind": "wechat-monthly",
            "year": 2026,
            "month": 6,
            "restDays": [7, 14],
            "paidRestDays": [1],
            "shouldAttendanceDays": 21,
            "payrollDays": 23,
            "employees": [{
                "displayName": "张三",
                "displayDepartment": "教务部",
                "enabled": true,
                "sourceIndex": 1,
                "days": days,
                "summary": {
                    "shouldAttendanceDays": 21,
                    "actualAttendanceDays": 99,
                    "payrollDays": 23,
                    "entryDate": "2026-06-01",
                    "personalLeaveHours": 7,
                    "remark": "原表备注"
                }
            }]
        });

        export_attendance_report(&project, &path_text).unwrap();
        let report = parse_exported_report(&path_text).unwrap();

        assert_eq!(report["year"], 2026);
        assert_eq!(report["month"], 6);
        assert_eq!(report["employees"][0]["name"], "张三");
        assert_eq!(report["employees"][0]["department"], "教务部");
        assert_eq!(report["employees"][0]["days"][0]["morning"], "出勤");
        assert_eq!(report["employees"][0]["days"][0]["afternoon"], "事假");
        assert_eq!(
            report["employees"][0]["summary"]["actualAttendanceDays"],
            99.0
        );
        assert_eq!(report["employees"][0]["summary"]["remark"], "原表备注");
        assert_eq!(report["recoveredParameters"]["source"], "metadata");
        assert_eq!(report["recoveredParameters"]["shouldAttendanceDays"], 21.0);
        assert_eq!(report["recoveredParameters"]["payrollDays"], 23.0);
        assert_eq!(report["recoveredParameters"]["restDays"], json!([7, 14]));
        assert_eq!(report["recoveredParameters"]["paidRestDays"], json!([1]));

        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn summary_columns_follow_actual_month_length() {
        assert_eq!(summary_start_col(2026, 1), 35);
        assert_eq!(summary_start_col(2026, 6), 34);
        assert_eq!(summary_start_col(2024, 2), 33);
        assert_eq!(summary_start_col(2026, 2), 32);
    }

    #[test]
    fn status_display_uses_text_for_plain_statuses() {
        assert_eq!(display_status_text("√"), "出勤");
        assert_eq!(display_status_text("△"), "休息");
        assert_eq!(display_status_text("带薪假"), "带薪");
        assert_eq!(display_status_text("迟到"), "迟到");
    }

    #[test]
    fn combined_status_text_labels_both_halves() {
        assert_eq!(
            combined_status_text("出勤", "事假"),
            "上午：出勤\n下午：事假"
        );
        assert_eq!(combined_status_text("", ""), "上午：--\n下午：--");
    }

    #[test]
    fn only_plain_statuses_use_normal_text_style() {
        assert!(is_plain_status("出勤"));
        assert!(is_plain_status("休息"));
        assert!(is_plain_status("带薪"));
        assert!(is_plain_status("调休"));
        assert!(is_plain_status("加班"));
        assert!(!is_plain_status("迟到"));
        assert!(!is_plain_status("缺卡"));
        assert!(!is_plain_status("事假"));
        assert!(!is_plain_status("病假"));
        assert!(!is_plain_status("工伤"));
        assert!(!is_plain_status("丧假"));
        assert!(!is_plain_status("婚假"));
        assert!(!is_plain_status("产假"));
        assert!(!is_plain_status("年假"));
    }

    #[test]
    fn notable_statuses_use_separate_style_keys() {
        assert_eq!(status_style_key("迟到"), StatusStyleKey::Late);
        assert_eq!(status_style_key("缺卡"), StatusStyleKey::MissingCard);
        assert_eq!(status_style_key("事假"), StatusStyleKey::PersonalLeave);
        assert_eq!(status_style_key("病假"), StatusStyleKey::BenefitLeave);
        assert_eq!(status_style_key("工伤"), StatusStyleKey::BenefitLeave);
        assert_eq!(status_style_key("丧假"), StatusStyleKey::BenefitLeave);
        assert_eq!(status_style_key("婚假"), StatusStyleKey::BenefitLeave);
        assert_eq!(status_style_key("产假"), StatusStyleKey::BenefitLeave);
        assert_eq!(status_style_key("年假"), StatusStyleKey::BenefitLeave);
        assert_eq!(status_style_key("早退"), StatusStyleKey::EarlyLeave);
        assert_eq!(status_style_key("异常"), StatusStyleKey::Abnormal);
        assert_eq!(status_style_key("旷工"), StatusStyleKey::Absence);
        assert_ne!(status_style_key("迟到"), status_style_key("缺卡"));
        assert_ne!(status_style_key("早退"), status_style_key("异常"));
    }

    #[test]
    fn non_plain_status_styles_have_background_fills() {
        let statuses = [
            "出勤", "休息", "带薪", "调休", "加班", "迟到", "缺卡", "事假", "病假", "工伤", "丧假",
            "婚假", "产假", "年假", "早退", "异常", "旷工",
        ];

        for status in statuses {
            let key = status_style_key(status);
            if key == StatusStyleKey::Normal {
                assert_eq!(status_background_color(key), None, "{status}");
            } else {
                assert!(
                    status_background_color(key).is_some(),
                    "{status} should have an Excel background fill"
                );
            }
        }
    }

    #[test]
    fn combined_status_style_uses_most_notable_half() {
        assert_eq!(
            status_style_key_for_pair("出勤", "异常"),
            StatusStyleKey::Abnormal
        );
        assert_eq!(
            status_style_key_for_pair("迟到", "早退"),
            StatusStyleKey::Late
        );
        assert_eq!(
            status_style_key_for_pair("出勤", "休息"),
            StatusStyleKey::Normal
        );
        assert_eq!(
            status_style_key_for_pair("年假", "出勤"),
            StatusStyleKey::BenefitLeave
        );
        assert_eq!(
            status_style_key_for_pair("病假", "出勤"),
            StatusStyleKey::BenefitLeave
        );
        assert_eq!(
            status_style_key_for_pair("年假", "迟到"),
            StatusStyleKey::Late
        );
    }

    #[test]
    fn employees_are_grouped_by_first_seen_department_order() {
        let employees = sorted_enabled_employees(vec![
            json!({"displayName": "张三", "displayDepartment": "教务部", "sourceIndex": 1}),
            json!({"displayName": "李四", "displayDepartment": "财务部", "sourceIndex": 2}),
            json!({"displayName": "王五", "displayDepartment": "教务部", "sourceIndex": 3}),
            json!({"displayName": "赵六", "displayDepartment": "财务部", "sourceIndex": 4, "enabled": false}),
        ]);
        let names = employees
            .iter()
            .map(|employee| text(employee, "displayName"))
            .collect::<Vec<_>>();

        assert_eq!(names, vec!["张三", "王五", "李四"]);
    }

    #[test]
    fn blank_manual_status_change_is_not_exported_as_issue() {
        let day = json!({
            "systemMorning": "",
            "systemAfternoon": "",
            "finalMorning": "出勤",
            "finalAfternoon": "事假",
            "morningNote": "",
            "afternoonNote": ""
        });

        assert!(!should_export_day_issue(&day, 0));
    }

    #[test]
    fn blank_statuses_are_not_exported_even_when_system_issues_exist() {
        let day = json!({
            "systemMorning": "",
            "systemAfternoon": "",
            "finalMorning": "",
            "finalAfternoon": "",
            "morningNote": "",
            "afternoonNote": ""
        });

        assert!(!should_export_day_issue(&day, 1));
    }

    #[test]
    fn system_abnormal_issue_is_exported() {
        let day = json!({
            "systemMorning": "异常",
            "systemAfternoon": "",
            "finalMorning": "异常",
            "finalAfternoon": "",
            "morningNote": "",
            "afternoonNote": ""
        });

        assert!(should_export_day_issue(&day, 1));
    }

    #[test]
    fn manual_abnormal_status_change_is_exported() {
        let day = json!({
            "systemMorning": "出勤",
            "systemAfternoon": "出勤",
            "finalMorning": "异常",
            "finalAfternoon": "出勤",
            "morningNote": "",
            "afternoonNote": ""
        });

        assert!(should_export_day_issue(&day, 0));
    }
}
