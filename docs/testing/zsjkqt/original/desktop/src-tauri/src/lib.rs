use serde_json::{json, Value};
use std::fs;
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Manager};

mod excel_engine;

#[tauri::command]
fn parse_source(source_path: String) -> Result<Value, String> {
    let response = excel_engine::parse_source(&source_path)?;
    response
        .get("attendance")
        .cloned()
        .ok_or_else(|| "解析结果缺少 attendance 字段".to_string())
}

#[tauri::command]
fn parse_exported_report(source_path: String) -> Result<Value, String> {
    excel_engine::parse_exported_report(&source_path)
}

#[tauri::command]
fn save_draft(app: AppHandle, project: Value) -> Result<(), String> {
    let project_id = project
        .get("id")
        .and_then(Value::as_str)
        .ok_or_else(|| "草稿缺少项目 ID".to_string())?;
    let draft_dir = app
        .path()
        .app_data_dir()
        .map_err(|error| error.to_string())?
        .join("drafts");
    fs::create_dir_all(&draft_dir).map_err(|error| error.to_string())?;
    let safe_name = sanitize_filename(project_id);
    let path = draft_dir.join(format!("{safe_name}.json"));
    let text = serde_json::to_string_pretty(&project).map_err(|error| error.to_string())?;
    fs::write(path, text).map_err(|error| error.to_string())
}

#[tauri::command]
fn load_personnel_profiles(app: AppHandle) -> Result<Value, String> {
    let Some(path) = personnel_config_path_for_read(&app)? else {
        return Ok(empty_object());
    };
    let Ok(text) = fs::read_to_string(path) else {
        return Ok(empty_object());
    };
    let Ok(value) = serde_json::from_str::<Value>(&text) else {
        return Ok(empty_object());
    };
    if value.is_object() {
        Ok(value)
    } else {
        Ok(empty_object())
    }
}

#[tauri::command]
fn upsert_personnel_profile(
    app: AppHandle,
    key: String,
    name: String,
    department: String,
    entry_date: String,
    enabled: bool,
) -> Result<(), String> {
    let key = key.trim();
    if key.is_empty() {
        return Err("人员档案缺少员工标识".to_string());
    }

    let path = personnel_config_path_for_write(&app)?;
    let read_path = if path.exists() {
        Some(path.clone())
    } else {
        personnel_config_path_for_read(&app)?
    };
    let mut profiles = match read_path {
        Some(path) => read_personnel_profiles_for_write(&path)?,
        None => empty_object(),
    };
    upsert_personnel_profile_value(&mut profiles, key, &name, &department, &entry_date, enabled)?;

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let text = serde_json::to_string_pretty(&profiles).map_err(|error| error.to_string())?;
    fs::write(path, text).map_err(|error| error.to_string())
}

#[tauri::command]
fn load_department_schedules(app: AppHandle) -> Result<Value, String> {
    let Some(path) = department_schedules_path_for_read(&app)? else {
        return Ok(default_department_schedules());
    };
    let Ok(text) = fs::read_to_string(path) else {
        return Ok(default_department_schedules());
    };
    let Ok(value) = serde_json::from_str::<Value>(&text) else {
        return Ok(default_department_schedules());
    };
    if value.is_object() {
        Ok(value)
    } else {
        Ok(default_department_schedules())
    }
}

#[tauri::command]
fn save_department_schedules(app: AppHandle, settings: Value) -> Result<(), String> {
    if !settings.is_object() {
        return Err("部门时段配置顶层必须是 JSON 对象".to_string());
    }

    let path = department_schedules_path_for_write(&app)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let text = serde_json::to_string_pretty(&settings).map_err(|error| error.to_string())?;
    fs::write(path, text).map_err(|error| error.to_string())
}

#[tauri::command]
fn export_attendance_report(project: Value, attendance_output_path: String) -> Result<(), String> {
    excel_engine::export_attendance_report(&project, &attendance_output_path)
}

#[tauri::command]
fn export_issue_list(project: Value, issues_output_path: String) -> Result<(), String> {
    excel_engine::export_issue_list(&project, &issues_output_path)
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            parse_source,
            parse_exported_report,
            save_draft,
            load_personnel_profiles,
            upsert_personnel_profile,
            load_department_schedules,
            save_department_schedules,
            export_attendance_report,
            export_issue_list
        ])
        .run(tauri::generate_context!())
        .expect("error while running attendance desktop app");
}

fn department_schedules_path_for_read(app: &AppHandle) -> Result<Option<PathBuf>, String> {
    let app_data_path = department_schedules_path_for_write(app)?;
    if app_data_path.exists() {
        return Ok(Some(app_data_path));
    }

    Ok(candidate_department_schedule_paths()
        .into_iter()
        .find(|path| path.exists()))
}

fn department_schedules_path_for_write(app: &AppHandle) -> Result<PathBuf, String> {
    Ok(app
        .path()
        .app_data_dir()
        .map_err(|error| error.to_string())?
        .join("config")
        .join("department_schedules.json"))
}

fn sanitize_filename(value: &str) -> String {
    value
        .chars()
        .map(|ch| match ch {
            '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' => '_',
            _ => ch,
        })
        .collect()
}

fn personnel_config_path_for_read(app: &AppHandle) -> Result<Option<PathBuf>, String> {
    let app_data_path = personnel_config_path_for_write(app)?;
    if app_data_path.exists() {
        return Ok(Some(app_data_path));
    }

    Ok(candidate_config_paths()
        .into_iter()
        .find(|path| path.exists()))
}

fn personnel_config_path_for_write(app: &AppHandle) -> Result<PathBuf, String> {
    Ok(app
        .path()
        .app_data_dir()
        .map_err(|error| error.to_string())?
        .join("config")
        .join("personnel_overrides.json"))
}

fn read_personnel_profiles_for_write(path: &Path) -> Result<Value, String> {
    if !path.exists() {
        return Ok(empty_object());
    }

    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    let value = serde_json::from_str::<Value>(&text)
        .map_err(|error| format!("人员档案不是合法 JSON：{error}"))?;
    if value.is_object() {
        Ok(value)
    } else {
        Err("人员档案顶层必须是 JSON 对象".to_string())
    }
}

fn upsert_personnel_profile_value(
    profiles: &mut Value,
    key: &str,
    name: &str,
    department: &str,
    entry_date: &str,
    enabled: bool,
) -> Result<(), String> {
    let root = profiles
        .as_object_mut()
        .ok_or_else(|| "人员档案顶层必须是 JSON 对象".to_string())?;
    let entry = root
        .entry(key.to_string())
        .or_insert_with(|| Value::Object(serde_json::Map::new()));
    let profile = entry
        .as_object_mut()
        .ok_or_else(|| "该员工档案必须是 JSON 对象".to_string())?;

    profile.insert("name".to_string(), Value::String(name.trim().to_string()));
    profile.insert(
        "department".to_string(),
        Value::String(department.trim().to_string()),
    );
    profile.insert(
        "entry_date".to_string(),
        Value::String(entry_date.trim().to_string()),
    );
    profile.insert("enabled".to_string(), Value::Bool(enabled));
    Ok(())
}

fn candidate_config_paths() -> Vec<PathBuf> {
    let mut paths = Vec::new();
    push_config_ancestors(Path::new(env!("CARGO_MANIFEST_DIR")), &mut paths);
    if let Ok(current_dir) = std::env::current_dir() {
        push_config_ancestors(&current_dir, &mut paths);
    }
    paths
}

fn candidate_department_schedule_paths() -> Vec<PathBuf> {
    let mut paths = Vec::new();
    push_department_schedule_ancestors(Path::new(env!("CARGO_MANIFEST_DIR")), &mut paths);
    if let Ok(current_dir) = std::env::current_dir() {
        push_department_schedule_ancestors(&current_dir, &mut paths);
    }
    paths
}

fn push_config_ancestors(start: &Path, paths: &mut Vec<PathBuf>) {
    for ancestor in start.ancestors() {
        let candidate = ancestor.join("config").join("personnel_overrides.json");
        if !paths.contains(&candidate) {
            paths.push(candidate);
        }
    }
}

fn push_department_schedule_ancestors(start: &Path, paths: &mut Vec<PathBuf>) {
    for ancestor in start.ancestors() {
        let candidate = ancestor.join("config").join("department_schedules.json");
        if !paths.contains(&candidate) {
            paths.push(candidate);
        }
    }
}

fn empty_object() -> Value {
    Value::Object(serde_json::Map::new())
}

fn default_department_schedules() -> Value {
    json!({
        "default": {
            "morning": { "start": "08:30", "end": "12:00" },
            "afternoon": { "start": "13:30", "end": "18:00" }
        },
        "departments": {}
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn upsert_personnel_profile_adds_new_employee() {
        let mut profiles = empty_object();

        upsert_personnel_profile_value(
            &mut profiles,
            "alice",
            " Alice ",
            " Operations ",
            " 2026-06-01 ",
            true,
        )
        .unwrap();

        assert_eq!(profiles["alice"]["name"], "Alice");
        assert_eq!(profiles["alice"]["department"], "Operations");
        assert_eq!(profiles["alice"]["entry_date"], "2026-06-01");
        assert_eq!(profiles["alice"]["enabled"], true);
    }

    #[test]
    fn upsert_personnel_profile_updates_existing_employee_and_preserves_other_data() {
        let mut profiles = json!({
            "alice": {
                "name": "Old Name",
                "department": "Old Department",
                "entry_date": "",
                "enabled": true,
                "note": "keep me"
            },
            "bob": {
                "name": "Bob"
            }
        });

        upsert_personnel_profile_value(
            &mut profiles,
            "alice",
            "New Name",
            "New Department",
            "2026-06-09",
            false,
        )
        .unwrap();

        assert_eq!(profiles["alice"]["name"], "New Name");
        assert_eq!(profiles["alice"]["department"], "New Department");
        assert_eq!(profiles["alice"]["entry_date"], "2026-06-09");
        assert_eq!(profiles["alice"]["enabled"], false);
        assert_eq!(profiles["alice"]["note"], "keep me");
        assert_eq!(profiles["bob"]["name"], "Bob");
    }

    #[test]
    fn upsert_personnel_profile_rejects_non_object_root() {
        let mut profiles = json!([]);

        let result =
            upsert_personnel_profile_value(&mut profiles, "alice", "Alice", "Ops", "", true);

        assert!(result.is_err());
    }
}
