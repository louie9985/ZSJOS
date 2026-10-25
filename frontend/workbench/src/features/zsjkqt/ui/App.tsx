import { Popover } from "antd";
import { HomeRestDayImport } from "./HomeRestDayImport";
import { mergeHomeRestDays } from "../core/homeRestDays";
import { CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Clock3, Download, FileSpreadsheet, Plus, RotateCcw, Save, Search, Settings2, Trash2 } from "./icons";
import { ChangeEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  cloneDepartmentScheduleSettings,
  attendanceWorkSchedule,
  departmentScheduleStatus,
  ensureDepartments,
  formatHours,
  formatScheduleText,
  knownDepartmentsFromMonth,
  knownDepartmentsFromProfiles,
  normalizeDepartmentScheduleSettings,
  resolveDepartmentSchedule,
  scheduleForOverride,
  validateDepartmentScheduleSettings,
  type DepartmentScheduleSettings,
  type WorkSchedule,
} from "../core/departmentSchedules";
import { ATTENDANCE_STATUSES, AttendanceMonth, AttendanceProject, AttendanceStatus, DayReview, EmployeeReview, ExportedAttendanceReport, PersonnelProfiles, RecoveredDayCandidate, RecoveredMonthlyParameters, SummaryReview } from "../core/models";
import { applyReimportMonthlyParameters, buildAttendanceProject, buildEmployeeReview, buildProjectFromExportedReport, mergeRebuiltProjectWithReviewState, projectMonthlyParametersChanged } from "../core/reviewBuilder";
import { dayHasManualReviewReminder, dayHasReviewIssue, employeeReviewIssueCount } from "../core/reviewIssues";
import { appendSourceSummaryReviewIssue, refreshSourceSummaryReviewIssue } from "../core/sourceSummaryReview";
import { statusStyleClass } from "../core/statusStyles";
import { recalculateSummary } from "../core/summary";
import { reclassifyDaysForSchedule } from "../core/dayClassifier";
import { SUMMARY_FIELD_DEFINITIONS, type SummaryFieldDefinition } from "../core/summaryFields";
import { zsjkqtApi, type ZsjkqtSnapshot } from "../../../services/zsjkqt";
import type { ZsjkqtEditorProps } from "../ZsjkqtPage";
import { employeeProfileKey, normalizeProjectRowKeys } from '../core/employeeIdentity';
import { filterReviewEmployees, nextReviewIndex, type EmployeeFilter } from '../core/reviewNavigation';


import { EMPTY_RECOVERED_PARAMETERS, recoverMonthlyParameters } from "../core/recoveredMonthlyParameters";

type Step = "home" | "department-settings" | "settings" | "reimport-settings" | "review";
type RestDayKind = "paid" | "rest";

const WEEKDAY_LABELS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];

export function App(props: ZsjkqtEditorProps) {
  const initial = props.initial;
  const [step, setStep] = useState<Step>(initial?.step || "home");
  const [sourcePath, setSourcePath] = useState(initial?.sourcePath || "");
  const [month, setMonth] = useState<AttendanceMonth | null>(initial?.month || null);
  const [restDays, setRestDays] = useState<number[]>(initial?.restDays || []);
  const [paidRestDays, setPaidRestDays] = useState<number[]>(initial?.paidRestDays || []);
  const [restDayKind, setRestDayKind] = useState<RestDayKind>(initial?.restDayKind || "paid");
  const [shouldAttendanceDays, setShouldAttendanceDays] = useState<number | null>(initial?.shouldAttendanceDays ?? null);
  const [payrollDays, setPayrollDays] = useState<number | null>(initial?.payrollDays ?? null);
  const [personnelProfiles, setPersonnelProfiles] = useState<PersonnelProfiles>(initial?.personnelProfiles || props.profiles);
  const [departmentSchedules, setDepartmentSchedules] = useState<DepartmentScheduleSettings>(() =>
    normalizeDepartmentScheduleSettings(initial?.departmentSchedules || props.schedules),
  );
  const [newDepartmentName, setNewDepartmentName] = useState("");
  const [project, setProject] = useState<AttendanceProject | null>(() => initial?.project ? normalizeProjectRowKeys(initial.project) : null);
  const [activeIndex, setActiveIndex] = useState(initial?.activeIndex || 0);
  const [query, setQuery] = useState(initial?.query || "");
  const [filter, setFilter] = useState<EmployeeFilter>(initial?.filter || "all");
  const [message, setMessage] = useState(initial ? "已恢复草稿，可继续核对和修改。" : "请选择企业微信月报开始生成考勤表。");
  const [recoveredParameters, setRecoveredParameters] = useState(initial?.recoveredParameters || EMPTY_RECOVERED_PARAMETERS);
  const [busy, setBusy] = useState(false);
  const scheduleBaseline = useRef(JSON.stringify(departmentSchedules));
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const exportedReportInputRef = useRef<HTMLInputElement | null>(null);

  const filteredEmployees = useMemo(() => {
    if (!project) return [];
    return filterReviewEmployees(project.employees, query, filter);
  }, [project, query, filter]);

  const currentEmployee = filteredEmployees[activeIndex] || filteredEmployees[0] || null;
  const totalIssues = project?.employees.reduce((sum, employee) => sum + employeeReviewIssueCount(employee), 0) || 0;
  const confirmedCount = project?.employees.filter((employee) => employee.confirmed).length || 0;
  const scheduleIssues = useMemo(() => validateDepartmentScheduleSettings(departmentSchedules), [departmentSchedules]);
  const knownDepartments = useMemo(() => {
    const departments = new Set(Object.keys(departmentSchedules.departments));
    for (const department of knownDepartmentsFromProfiles(personnelProfiles)) departments.add(department);
    if (month) {
      for (const department of knownDepartmentsFromMonth(month.employees, personnelProfiles)) departments.add(department);
    }
    return [...departments].sort((left, right) => left.localeCompare(right, "zh-CN"));
  }, [departmentSchedules.departments, month, personnelProfiles]);
  const currentMonthDepartments = useMemo(
    () => (month ? knownDepartmentsFromMonth(month.employees, personnelProfiles) : []),
    [month, personnelProfiles],
  );

  const snapshot = useMemo<ZsjkqtSnapshot>(() => ({ schemaVersion: 1, step, month, project, restDays, paidRestDays, shouldAttendanceDays, payrollDays, departmentSchedules, personnelProfiles, recoveredParameters, activeIndex, query, filter, restDayKind, sourcePath }), [step, month, project, restDays, paidRestDays, shouldAttendanceDays, payrollDays, departmentSchedules, personnelProfiles, recoveredParameters, activeIndex, query, filter, restDayKind, sourcePath]);
  useEffect(() => { if (month || project) props.onSnapshot(snapshot); }, [snapshot, props.onSnapshot]);
  useEffect(() => { props.onActivity(busy, !month && !project && JSON.stringify(departmentSchedules) !== scheduleBaseline.current); }, [busy, month, project, departmentSchedules, props.onActivity]);
  async function handlePickFile() { fileInputRef.current?.click(); }
  async function handlePickExportedReport() { exportedReportInputRef.current?.click(); }
  async function handleBrowserFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setBusy(true);
    try {
      const [parsed, profiles, schedules] = await Promise.all([zsjkqtApi.parseSource(file), zsjkqtApi.profiles().then(c => c.value), Promise.resolve(departmentSchedules)]);
      if (!await props.beforeImport(parsed.attendance.sourceFileHash || "", parsed.attendance.year, parsed.attendance.month)) return;
      setSourcePath(file.name);
      setMonth(parsed.attendance);
      setProject(null);
      setPersonnelProfiles(profiles);
      setDepartmentSchedules(ensureDepartments(schedules, knownDepartmentsFromMonth(parsed.attendance.employees, profiles)));
      setRecoveredParameters(EMPTY_RECOVERED_PARAMETERS);
      resetSettings();
      setStep("settings");
      setMessage(`已读取 ${file.name}，请确认月度参数。`);
    } catch (error) {
      setMessage(`读取月报失败：${formatError(error)}`);
    } finally { setBusy(false); }
  }

  async function handleBrowserExportedReportChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setBusy(true);
    try {
      const [report, profiles, schedules] = await Promise.all([zsjkqtApi.parseReport(file), zsjkqtApi.profiles().then(c => c.value), Promise.resolve(departmentSchedules)]);
      if (!await props.beforeImport(report.sourceFileHash || "", report.year, report.month)) return;
      openExportedReportProject(report, file.name, profiles, schedules);
    } catch (error) {
      setMessage(`读取正式考勤汇总表失败：${formatError(error)}`);
    } finally { setBusy(false); }
  }

  function openExportedReportProject(
    report: ExportedAttendanceReport,
    selectedPath: string,
    profiles: PersonnelProfiles,
    schedules: DepartmentScheduleSettings,
  ) {
    const ensuredSchedules = ensureDepartments(schedules, departmentsFromExportedReport(report));
    const recovered = recoverMonthlyParameters(report);
    const nextProject = buildProjectFromExportedReport(report, ensuredSchedules);
    setSourcePath(selectedPath);
    setMonth(null);
    setProject(nextProject);
    setPersonnelProfiles(profiles);
    setDepartmentSchedules(ensuredSchedules);
    setRecoveredParameters(recovered);
    setRestDays(nextProject.restDays);
    setPaidRestDays(nextProject.paidRestDays);
    setRestDayKind("paid");
    setShouldAttendanceDays(nextProject.shouldAttendanceDays);
    setPayrollDays(nextProject.payrollDays);
    setFilter("all");
    setQuery("");
    setActiveIndex(0);
    setStep("reimport-settings");
    setMessage("正式考勤汇总表已导入，请确认续改参数。");
  }

  async function goBack() {
    if (step === "settings") {
      if (!await props.onClose()) return;
      setStep("home");
      setMonth(null);
      setSourcePath("");
      setMessage("请选择企业微信月报开始生成考勤表。");
    } else if (step === "reimport-settings") {
      if (!await props.onClose()) return;
      setStep("home");
      setProject(null);
      setSourcePath("");
      setRecoveredParameters(EMPTY_RECOVERED_PARAMETERS);
      resetSettings();
      setMessage("请选择企业微信月报，或导入已导出的正式考勤汇总表。");
    } else if (step === "department-settings") {
      if (!await props.onClose()) return;
      setStep("home");
      setMessage("请选择企业微信月报开始生成考勤表。");
    } else if (step === "review") {
      if (project?.sourceKind === "exported-report") {
        setStep("reimport-settings");
        setMessage("已返回续改参数确认页。");
      } else {
        setStep("settings");
        setMessage("已返回月度参数确认页。");
      }
    }
  }

  function resetSettings() {
    setRestDays([]);
    setPaidRestDays([]);
    setRestDayKind("paid");
    setShouldAttendanceDays(null);
    setPayrollDays(null);
  }

  function enterReview() {
    if (!month) return;
    if (scheduleIssues.length > 0) {
      setMessage(`部门时段配置有误：${scheduleIssues[0].message}`);
      return;
    }
    const rebuiltProject = buildAttendanceProject(month, { restDays, paidRestDays, shouldAttendanceDays, payrollDays }, personnelProfiles, departmentSchedules);
    const hadConfirmedEmployees = project ? project.employees.some((employee) => employee.confirmed) : false;
    const parametersChanged = project ? projectMonthlyParametersChanged(rebuiltProject, project) : false;
    const nextProject = mergeRebuiltProjectWithReviewState(rebuiltProject, project, departmentSchedules);
    setProject(nextProject);
    setFilter("all");
    setQuery("");
    setActiveIndex(0);
    setStep("review");
    setMessage(
      parametersChanged && hadConfirmedEmployees
        ? "已按新参数重新计算并保留人工修改。已确认员工已改为待复核，请重新确认后导出。"
        : "员工卡片已生成。保存当前卡片后会自动跳到下一个未确认员工。",
    );
  }

  function enterReimportReview() {
    if (!project || project.sourceKind !== "exported-report") return;
    setProject(applyReimportMonthlyParameters(project, {
      restDays,
      paidRestDays,
      shouldAttendanceDays,
      payrollDays,
    }));
    setFilter("all");
    setQuery("");
    setActiveIndex(0);
    setStep("review");
    setMessage("续改参数已确认：每位员工的应出勤日和计薪日已按本次参数更新，其余导入汇总保持原值，请逐人确认后再导出。");
  }

  function applyFilter(nextFilter: EmployeeFilter) {
    setFilter(nextFilter);
    setActiveIndex(0);
  }

  function updateQuery(nextQuery: string) {
    setQuery(nextQuery);
    setActiveIndex(0);
  }

  function toggleRestDay(day: number) {
    if (restDayKind === "paid") {
      setPaidRestDays((current) => toggleDay(current, day));
      setRestDays((current) => current.filter((item) => item !== day));
      return;
    }

    setRestDays((current) => toggleDay(current, day));
    setPaidRestDays((current) => current.filter((item) => item !== day));
  }

  function applyRecoveredRestCandidates(kind: RestDayKind) {
    const days =
      kind === "paid"
        ? recoveredParameters.paidRestDayCandidates.map((candidate) => candidate.day)
        : recoveredParameters.restDayCandidates.map((candidate) => candidate.day);
    if (kind === "paid") {
      setPaidRestDays((current) => mergeDays(current, days));
      setRestDays((current) => current.filter((day) => !days.includes(day)));
    } else {
      setRestDays((current) => mergeDays(current, days));
      setPaidRestDays((current) => current.filter((day) => !days.includes(day)));
    }
  }

  function restDayType(day: number): RestDayKind | "" {
    if (paidRestDays.includes(day)) return "paid";
    if (restDays.includes(day)) return "rest";
    return "";
  }

  function updateEmployee(key: string, updater: (employee: EmployeeReview) => EmployeeReview) {
    setProject((current) => {
      if (!current) return current;
      return {
        ...current,
        updatedAt: new Date().toISOString(),
        employees: current.employees.map((employee) => (employee.key === key ? updater(employee) : employee)),
      };
    });
  }

  function resetCurrentEmployee() {
    if (!project || !currentEmployee) return;
    if (project.sourceKind === "exported-report") {
      resetCurrentExportedEmployee(currentEmployee);
      return;
    }
    if (!month) return;
    const sourceEmployee = month.employees[currentEmployee.sourceIndex - 1];
    if (!sourceEmployee) {
      setMessage("未找到该员工的源月报数据，无法重置。");
      return;
    }

    const resetEmployee = buildEmployeeReview(
      month,
      sourceEmployee,
      currentEmployee.sourceIndex,
      {
        restDays: project.restDays,
        paidRestDays: project.paidRestDays,
        shouldAttendanceDays: project.shouldAttendanceDays,
        payrollDays: project.payrollDays,
      },
      personnelProfiles,
      departmentSchedules,
    );

    setProject((current) => {
      if (!current) return current;
      return {
        ...current,
        updatedAt: new Date().toISOString(),
        employees: current.employees.map((employee) => (employee.key === currentEmployee.key ? resetEmployee : employee)),
      };
    });
    setMessage(`${resetEmployee.displayName} 已重新读取出勤状态和汇总数据，请重新确认。`);
  }

  function resetCurrentExportedEmployee(employee: EmployeeReview) {
    const importedSummary = summaryFromSourceSummaries(employee.sourceSummaries, employee.summary);
    setProject((current) => {
      if (!current) return current;
      return {
        ...current,
        updatedAt: new Date().toISOString(),
        employees: current.employees.map((item) =>
          item.key === employee.key
            ? {
                ...item,
                confirmed: false,
                days: item.days.map((day) => ({
                  ...day,
                  finalMorning: day.systemMorning,
                  finalAfternoon: day.systemAfternoon,
                  morningNote: "",
                  afternoonNote: "",
                  issues: [],
                  modified: false,
                })),
                summary: importedSummary,
                manualSummaryOverrides: {},
                notes: "",
              }
            : item,
        ),
      };
    });
    setMessage(`${employee.displayName} 已恢复为导入时的状态和汇总。`);
  }

  async function saveSchedules(nextSchedules = departmentSchedules, successMessage = "部门时段设置已保存。") {
    const issues = validateDepartmentScheduleSettings(nextSchedules);
    if (issues.length > 0) {
      setMessage(`部门时段配置有误：${issues[0].message}`);
      return;
    }
    setBusy(true);
    try {
      await props.saveSchedules(nextSchedules);
      scheduleBaseline.current = JSON.stringify(nextSchedules);
      setDepartmentSchedules(nextSchedules);
      setProject((current) => (current ? recalculateProjectForSchedules(current, nextSchedules) : current));
      setMessage(successMessage);
    } catch (error) {
      setMessage(`部门时段设置保存失败：${formatError(error)}`);
    } finally { setBusy(false); }
  }

  function updateDepartmentSchedules(updater: (settings: DepartmentScheduleSettings) => DepartmentScheduleSettings) {
    setDepartmentSchedules((current) => {
      const next = updater(current);
      setProject((projectValue) => (projectValue ? recalculateProjectForSchedules(projectValue, next) : projectValue));
      return next;
    });
  }

  function addDepartment() {
    const department = newDepartmentName.trim();
    if (!department) return;
    updateDepartmentSchedules((current) => ensureDepartments(current, [department]));
    setNewDepartmentName("");
    setMessage(`已新增部门：${department}`);
  }

  async function saveCurrentEmployee() {
    if (!project || !currentEmployee) return;
    setBusy(true);
    const savedEmployee = { ...currentEmployee, confirmed: true };

    const refreshed = {
      ...project,
      updatedAt: new Date().toISOString(),
      employees: project.employees.map((employee) => (employee.key === currentEmployee.key ? savedEmployee : employee)),
    };
    try {
      await props.onSave({ ...snapshot, project: refreshed });
      setProject(refreshed);
    } catch (error) {
      setMessage(`保存失败，当前修改已保留：${formatError(error)}`);
      setBusy(false);
      return;
    }
    let profileError: unknown = null;
    try {
      await zsjkqtApi.profile({ key: employeeProfileKey(savedEmployee), name: savedEmployee.displayName, department: savedEmployee.displayDepartment, entryDate: savedEmployee.summary.entryDate || savedEmployee.entryDate, enabled: savedEmployee.enabled });
      setPersonnelProfiles((current) => ({
        ...current,
        [employeeProfileKey(savedEmployee)]: {
          name: savedEmployee.displayName,
          department: savedEmployee.displayDepartment,
          entry_date: savedEmployee.summary.entryDate || savedEmployee.entryDate,
          enabled: savedEmployee.enabled,
        },
      }));
    } catch (error) {
      profileError = error;
    }

    setActiveIndex(nextReviewIndex(filteredEmployees, filterReviewEmployees(refreshed.employees, query, filter), savedEmployee.key));
    const successMessage = employeeReviewIssueCount(savedEmployee)
      ? "已保存并更新人员档案。该员工仍有异常，导出时会进入异常信息表。"
      : "已保存、更新人员档案并切换到下一位。";
    setMessage(profileError ? `已保存，但人员档案写入失败：${formatError(profileError)}` : successMessage);
    setBusy(false);
  }

  async function handleExportAttendanceSummary() {
    if (!project) return;
    const attendanceName = `${project.year}年${project.month}月中世健学校考勤汇总表.xlsx`;

    setBusy(true);
    try {
      await props.onExport(snapshot, "report", attendanceName);
      setMessage(`考勤汇总表已导出：${attendanceName}`);
    } catch (error) {
      setMessage(`导出考勤汇总表失败：${formatError(error)}`);
    } finally { setBusy(false); }
  }

  async function handleExportIssueList() {
    if (!project) return;
    const issueName = `${project.year}年${project.month}月异常清单.xlsx`;

    setBusy(true);
    try {
      await props.onExport(snapshot, "issues", issueName);
      setMessage(`异常清单已导出：${issueName}`);
    } catch (error) {
      setMessage(`导出异常清单失败：${formatError(error)}`);
    } finally { setBusy(false); }
  }

  return (
    <main className={`app-shell${step === "review" ? " zsjkqt-review-shell" : ""}`}>
      <header className="zsjkqt-editor-toolbar">
        {props.toolbar}
        {project && (
          <div className="zsjkqt-review-counts" aria-label="考勤统计">
            <span>已确认 <strong>{confirmedCount}/{project.employees.length}</strong></span>
            <span>异常 <strong>{totalIssues}</strong></span>
          </div>
        )}
      </header>
      {message && message !== "请选择企业微信月报开始生成考勤表。" && message !== "员工卡片已生成。保存当前卡片后会自动跳到下一个未确认员工。" && (
        <p className="zsjkqt-feedback" role="status">{message}</p>
      )}
      <fieldset className="zsjkqt-controls" disabled={busy}>
      {step === "home" && (
        <section className="home-grid">
          <div className="glass action-panel">
            <FileSpreadsheet size={34} />
            <h2>选择企业微信月报</h2>
            <p>选择企业微信导出的上下班打卡月报，系统会自动识别员工和每日明细。</p>
            <button className="primary-button" onClick={handlePickFile}>选择 .xlsx 文件</button>
            <input ref={fileInputRef} className="hidden-file-input" type="file" accept=".xlsx" onChange={handleBrowserFileChange} />
            <button className="ghost-button" onClick={handlePickExportedReport}>导入已导出的正式汇总表</button>
            <input ref={exportedReportInputRef} className="hidden-file-input" type="file" accept=".xlsx" onChange={handleBrowserExportedReportChange} />
          </div>
          <div className="glass recent-panel">
            <h2>常用设置</h2>
            <p className="muted">维护各部门上午和下午时段，导入月报后会自动套用。</p>
            <button className="ghost-button" onClick={() => setStep("department-settings")}>
              <Clock3 size={17} />
              部门时段设置
            </button>
          </div>
        </section>
      )}

      {step === "department-settings" && (
        <section className="glass settings-panel">
          <DepartmentScheduleEditor
            title="部门时段设置"
            description="先维护默认班次，再给特殊部门单独设置上午和下午时段。"
            settings={departmentSchedules}
            departments={knownDepartments}
            newDepartmentName={newDepartmentName}
            onNewDepartmentNameChange={setNewDepartmentName}
            onAddDepartment={addDepartment}
            onChange={updateDepartmentSchedules}
          />
        </section>
      )}

      {step === "settings" && month && (
        <section className="glass settings-panel monthly-parameters-panel">
          <div className="section-title">
            <CalendarDays size={22} />
            <div>
              <h2>{month.year}年{month.month}月参数确认</h2>
              <p>{sourcePath || month.sourceFileName}</p>
            </div>
          </div>
          <div className="monthly-parameters-layout">
          <div className="monthly-parameters-controls">
          <div className="settings-grid">
            <label>
              应出勤日
              <input type="number" value={shouldAttendanceDays ?? ""} onChange={(event) => setShouldAttendanceDays(toNumber(event.target.value))} />
            </label>
            <label>
              计薪日
              <input type="number" value={payrollDays ?? ""} onChange={(event) => setPayrollDays(toNumber(event.target.value))} />
            </label>
          </div>
          <HomeRestDayImport year={(month || project)!.year} month={(month || project)!.month}
            allowed={!!props.canReadHomeRestDays} onImport={(days, kind) => {
              const next = mergeHomeRestDays(restDays, paidRestDays, days, kind);
              setRestDays(next.restDays); setPaidRestDays(next.paidRestDays);
            }} />
          </div>
          <div className="rest-calendar">
            <div className="rest-kind-controls" aria-label="休息日类型">
              <button className={restDayKind === "paid" ? "rest-kind active paid" : "rest-kind"} onClick={() => setRestDayKind("paid")}>
                带薪休息日
              </button>
              <button className={restDayKind === "rest" ? "rest-kind active rest" : "rest-kind"} onClick={() => setRestDayKind("rest")}>
                普通休息日
              </button>
            </div>
            <div className="calendar-weekdays">
              {WEEKDAY_LABELS.map((label) => (
                <span key={label}>{label}</span>
              ))}
            </div>
            <div className="calendar-grid">
              {Array.from({ length: monthStartOffset(month.year, month.month) }, (_, index) => (
                <span key={`blank-${index}`} className="calendar-blank" />
              ))}
              {Array.from({ length: new Date(month.year, month.month, 0).getDate() }, (_, index) => index + 1).map((day) => {
                const type = restDayType(day);
                return (
                  <button
                    key={day}
                    className={type ? `day-toggle selected ${type}` : "day-toggle"}
                    onClick={() => toggleRestDay(day)}
                  >
                    <span>{day}</span>
                    <small>{restDayLabel(type)}</small>
                  </button>
                );
              })}
            </div>
          </div>
          </div>
          <div className="department-schedule-section">
            <DepartmentScheduleQuickView
              departments={currentMonthDepartments}
              settings={departmentSchedules}
              onChange={updateDepartmentSchedules}
            />
          </div>
        </section>
      )}

      {step === "reimport-settings" && project?.sourceKind === "exported-report" && (
        <section className="glass settings-panel monthly-parameters-panel">
          <div className="section-title">
            <CalendarDays size={22} />
            <div>
              <h2>{project.year}年{project.month}月续改参数确认</h2>
              <p>{sourcePath || project.sourceFileName}</p>
            </div>
          </div>
          <div className="recovered-parameter-banner">
            <strong>{recoveredSourceLabel(recoveredParameters.source)}</strong>
            <span>确认后会更新项目参数，并将每位员工的应出勤日、计薪日改为本次填写值；其余右侧汇总和人工修订保持原值。</span>
          </div>
          {recoveredParameters.warnings.length > 0 && (
            <div className="warning-list">
              {recoveredParameters.warnings.map((warning) => (
                <span key={warning}>{warning}</span>
              ))}
            </div>
          )}
          <div className="monthly-parameters-layout">
          <div className="monthly-parameters-controls">
          <div className="settings-grid">
            <label>
              应出勤日
              <input type="number" value={shouldAttendanceDays ?? ""} onChange={(event) => setShouldAttendanceDays(toNumber(event.target.value))} />
            </label>
            <label>
              计薪日
              <input type="number" value={payrollDays ?? ""} onChange={(event) => setPayrollDays(toNumber(event.target.value))} />
            </label>
          </div>
          {(recoveredParameters.restDayCandidates.length > 0 || recoveredParameters.paidRestDayCandidates.length > 0) && (
            <div className="candidate-actions">
              {recoveredParameters.paidRestDayCandidates.length > 0 && (
                <button className="ghost-button" onClick={() => applyRecoveredRestCandidates("paid")}>
                  采纳带薪候选：{candidateDaysText(recoveredParameters.paidRestDayCandidates)}
                </button>
              )}
              {recoveredParameters.restDayCandidates.length > 0 && (
                <button className="ghost-button" onClick={() => applyRecoveredRestCandidates("rest")}>
                  采纳休息候选：{candidateDaysText(recoveredParameters.restDayCandidates)}
                </button>
              )}
            </div>
          )}
          <HomeRestDayImport year={(month || project)!.year} month={(month || project)!.month}
            allowed={!!props.canReadHomeRestDays} onImport={(days, kind) => {
              const next = mergeHomeRestDays(restDays, paidRestDays, days, kind);
              setRestDays(next.restDays); setPaidRestDays(next.paidRestDays);
            }} />
          </div>
          <div className="rest-calendar">
            <div className="rest-kind-controls" aria-label="休息日类型">
              <button className={restDayKind === "paid" ? "rest-kind active paid" : "rest-kind"} onClick={() => setRestDayKind("paid")}>
                带薪休息日
              </button>
              <button className={restDayKind === "rest" ? "rest-kind active rest" : "rest-kind"} onClick={() => setRestDayKind("rest")}>
                普通休息日
              </button>
            </div>
            <div className="calendar-weekdays">
              {WEEKDAY_LABELS.map((label) => (
                <span key={label}>{label}</span>
              ))}
            </div>
            <div className="calendar-grid">
              {Array.from({ length: monthStartOffset(project.year, project.month) }, (_, index) => (
                <span key={`reimport-blank-${index}`} className="calendar-blank" />
              ))}
              {Array.from({ length: new Date(project.year, project.month, 0).getDate() }, (_, index) => index + 1).map((day) => {
                const type = restDayType(day);
                return (
                  <button
                    key={day}
                    className={type ? `day-toggle selected ${type}` : "day-toggle"}
                    onClick={() => toggleRestDay(day)}
                  >
                    <span>{day}</span>
                    <small>{restDayLabel(type)}</small>
                  </button>
                );
              })}
            </div>
          </div>
          </div>
        </section>
      )}

      {step === "review" && project && (
        <section className="workspace">
          <aside className="glass employee-list">
            <div className="search-box">
              <Search size={17} />
              <input placeholder="搜索姓名或部门" value={query} onChange={(event) => updateQuery(event.target.value)} />
            </div>
            <div className="filter-row">
              {(["all", "issues", "unconfirmed", "disabled"] as const).map((item) => (
                <button key={item} className={filter === item ? "chip active" : "chip"} onClick={() => applyFilter(item)}>
                  {filterLabel(item)}
                </button>
              ))}
            </div>
            <div className="employee-scroll">
              {filteredEmployees.map((employee, index) => (
                <button
                  key={employee.key}
                  className={employeeItemClassName(employee, employee.key === currentEmployee?.key)}
                  onClick={() => {
                    setActiveIndex(index);
                  }}
                >
                  <span>{employee.displayName}</span>
                  <small>{employee.displayDepartment || "未分组"} · {employee.confirmed ? "已确认" : "待确认"}</small>
                </button>
              ))}
            </div>
          </aside>
          {currentEmployee ? (
            <EmployeeCard
              employee={currentEmployee}
              year={project.year}
              month={project.month}
              sourceKind={project.sourceKind}
              departmentSchedules={departmentSchedules}
              onChange={updateEmployee}
            />
          ) : (
            <section className="glass employee-card empty-review-panel">
              <div>
                <h2>{emptyReviewTitle(filter, query)}</h2>
                <p>{emptyReviewDescription(filter, query)}</p>
                <button className="ghost-button" onClick={() => applyFilter("all")}>查看全部员工</button>
              </div>
            </section>
          )}
          <aside className="glass summary-panel">
            {currentEmployee ? (
              <>
                <div className="section-title compact">
                  <Settings2 size={20} />
                  <h2>汇总</h2>
                </div>
                <SummaryList employee={currentEmployee} onChange={updateEmployee} />
              </>
            ) : (
              <div className="empty-summary-panel">
                <h2>暂无汇总</h2>
                <p>当前筛选条件下没有员工。</p>
              </div>
            )}
          </aside>
        </section>
      )}

      {step !== "home" && (
        <section className="glass page-action-bar">
          <button className="ghost-button back-action" onClick={goBack}>
            <ChevronLeft size={17} />
            返回上一页
          </button>
          <div className="page-action-group">
            {step === "settings" && (
              <>
                <button className="ghost-button" onClick={() => saveSchedules(departmentSchedules, "本次部门时段已保存。")}>
                  <Save size={16} />
                  保存部门时段
                </button>
                <button className="primary-button" onClick={enterReview}>进入员工确认</button>
              </>
            )}
            {step === "reimport-settings" && (
              <button className="primary-button" onClick={enterReimportReview}>进入员工确认</button>
            )}
            {step === "department-settings" && (
              <button className="primary-button" onClick={() => saveSchedules()}>
                <Save size={16} />
                保存设置
              </button>
            )}
            {step === "review" && project && (
              <>
                {currentEmployee && (
                  <>
                    <button className="ghost-button" onClick={() => setActiveIndex(Math.max(0, activeIndex - 1))}><ChevronLeft size={16} />上一位</button>
                    <button className="ghost-button" onClick={resetCurrentEmployee}><RotateCcw size={16} />重置当前员工</button>
                    <button className="primary-button" onClick={saveCurrentEmployee}><CheckCircle2 size={17} />保存并下一个</button>
                    <button className="ghost-button" onClick={() => setActiveIndex(Math.min(filteredEmployees.length - 1, activeIndex + 1))}><ChevronRight size={16} />下一位</button>
                  </>
                )}
                <button className="export-button" onClick={handleExportAttendanceSummary}><Download size={17} />导出考勤汇总表</button>
                <button className="export-button" onClick={handleExportIssueList}><Download size={17} />导出异常清单</button>
              </>
            )}
          </div>
        </section>
      )}
      </fieldset>
    </main>
  );
}

function DepartmentScheduleEditor({
  title,
  description,
  settings,
  departments,
  newDepartmentName,
  onNewDepartmentNameChange,
  onAddDepartment,
  onChange,
}: {
  title: string;
  description: string;
  settings: DepartmentScheduleSettings;
  departments: string[];
  newDepartmentName: string;
  onNewDepartmentNameChange: (value: string) => void;
  onAddDepartment: () => void;
  onChange: (updater: (settings: DepartmentScheduleSettings) => DepartmentScheduleSettings) => void;
}) {
  const issues = validateDepartmentScheduleSettings(settings);

  return (
    <div className="department-schedule-editor">
      <div className="section-title">
        <Clock3 size={22} />
        <div>
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
      </div>

      <div className="schedule-default-row">
        <strong>默认班次</strong>
        <ScheduleTimeInputs
          schedule={settings.default}
          onChange={(half, field, value) =>
            onChange((current) => {
              const next = cloneDepartmentScheduleSettings(current);
              next.default[half][field] = value;
              return next;
            })
          }
        />
        <ScheduleHours schedule={settings.default} />
      </div>

      <div className="department-add-row">
        <input
          placeholder="新增部门名称"
          value={newDepartmentName}
          onChange={(event) => onNewDepartmentNameChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") onAddDepartment();
          }}
        />
        <button className="ghost-button" onClick={onAddDepartment}>
          <Plus size={16} />
          新增部门
        </button>
      </div>

      {issues.length > 0 && <div className="inline-error">{issues[0].message}</div>}

      <div className="schedule-table-wrap">
        <table className="schedule-table">
          <thead>
            <tr>
              <th>部门</th>
              <th>模式</th>
              <th>上午</th>
              <th>下午</th>
              <th>时长</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {departments.map((department) => {
              const override = settings.departments[department] || { mode: "default" as const };
              const schedule = scheduleForOverride(settings.default, override);
              return (
                <tr key={department}>
                  <td>{department}</td>
                  <td>
                    <select
                      value={override.mode}
                      onChange={(event) =>
                        onChange((current) => {
                          const next = cloneDepartmentScheduleSettings(current);
                          next.departments[department] =
                            event.target.value === "custom"
                              ? { mode: "custom", morning: { ...current.default.morning }, afternoon: { ...current.default.afternoon } }
                              : { mode: "default" };
                          return next;
                        })
                      }
                    >
                      <option value="default">使用默认班次</option>
                      <option value="custom">单独设置</option>
                    </select>
                  </td>
                  <td>
                    {override.mode === "custom" ? (
                      <ScheduleHalfInputs
                        half="morning"
                        schedule={schedule}
                        onChange={(field, value) =>
                          onChange((current) => updateDepartmentScheduleHalf(current, department, "morning", field, value))
                        }
                      />
                    ) : (
                      <span className="muted-text">{schedule.morning.start}-{schedule.morning.end}</span>
                    )}
                  </td>
                  <td>
                    {override.mode === "custom" ? (
                      <ScheduleHalfInputs
                        half="afternoon"
                        schedule={schedule}
                        onChange={(field, value) =>
                          onChange((current) => updateDepartmentScheduleHalf(current, department, "afternoon", field, value))
                        }
                      />
                    ) : (
                      <span className="muted-text">{schedule.afternoon.start}-{schedule.afternoon.end}</span>
                    )}
                  </td>
                  <td><ScheduleHours schedule={schedule} /></td>
                  <td>
                    <div className="schedule-actions">
                      <button
                        className="icon-button"
                        title="改回默认"
                        onClick={() =>
                          onChange((current) => {
                            const next = cloneDepartmentScheduleSettings(current);
                            next.departments[department] = { mode: "default" };
                            return next;
                          })
                        }
                      >
                        <RotateCcw size={15} />
                      </button>
                      <button
                        className="icon-button"
                        title="删除部门"
                        onClick={() =>
                          onChange((current) => {
                            const next = cloneDepartmentScheduleSettings(current);
                            delete next.departments[department];
                            return next;
                          })
                        }
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function DepartmentScheduleQuickView({
  departments,
  settings,
  onChange,
}: {
  departments: string[];
  settings: DepartmentScheduleSettings;
  onChange: (updater: (settings: DepartmentScheduleSettings) => DepartmentScheduleSettings) => void;
}) {
  return (
    <div className="department-schedule-quick">
      <div className="section-title compact">
        <Clock3 size={20} />
        <h2>本次部门时段</h2>
      </div>
      <div className="schedule-table-wrap compact">
        <table className="schedule-table">
          <thead>
            <tr>
              <th>部门</th>
              <th>模式</th>
              <th>上午</th>
              <th>下午</th>
              <th>时长</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {departments.map((department) => {
              const override = settings.departments[department] || { mode: "default" as const };
              const schedule = scheduleForOverride(settings.default, override);
              return (
                <tr key={department}>
                  <td>{department}</td>
                  <td>
                    <select
                      value={override.mode}
                      onChange={(event) =>
                        onChange((current) => {
                          const next = cloneDepartmentScheduleSettings(current);
                          next.departments[department] =
                            event.target.value === "custom"
                              ? { mode: "custom", morning: { ...current.default.morning }, afternoon: { ...current.default.afternoon } }
                              : { mode: "default" };
                          return next;
                        })
                      }
                    >
                      <option value="default">使用默认班次</option>
                      <option value="custom">单独设置</option>
                    </select>
                  </td>
                  <td>
                    {override.mode === "custom" ? (
                      <ScheduleHalfInputs
                        half="morning"
                        schedule={schedule}
                        onChange={(field, value) =>
                          onChange((current) => updateDepartmentScheduleHalf(current, department, "morning", field, value))
                        }
                      />
                    ) : (
                      <span className="muted-text">{schedule.morning.start}-{schedule.morning.end}</span>
                    )}
                  </td>
                  <td>
                    {override.mode === "custom" ? (
                      <ScheduleHalfInputs
                        half="afternoon"
                        schedule={schedule}
                        onChange={(field, value) =>
                          onChange((current) => updateDepartmentScheduleHalf(current, department, "afternoon", field, value))
                        }
                      />
                    ) : (
                      <span className="muted-text">{schedule.afternoon.start}-{schedule.afternoon.end}</span>
                    )}
                  </td>
                  <td><ScheduleHours schedule={schedule} /></td>
                  <td><span className="schedule-status-pill">{departmentScheduleStatus(department, settings)}</span></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ScheduleTimeInputs({
  schedule,
  onChange,
}: {
  schedule: WorkSchedule;
  onChange: (half: "morning" | "afternoon", field: "start" | "end", value: string) => void;
}) {
  return (
    <>
      <label>
        上午
        <ScheduleHalfInputs half="morning" schedule={schedule} onChange={(field, value) => onChange("morning", field, value)} />
      </label>
      <label>
        下午
        <ScheduleHalfInputs half="afternoon" schedule={schedule} onChange={(field, value) => onChange("afternoon", field, value)} />
      </label>
    </>
  );
}

function ScheduleHalfInputs({
  half,
  schedule,
  onChange,
}: {
  half: "morning" | "afternoon";
  schedule: WorkSchedule;
  onChange: (field: "start" | "end", value: string) => void;
}) {
  return (
    <span className="time-range-inputs">
      <input type="time" value={schedule[half].start} onChange={(event) => onChange("start", event.target.value)} />
      <span>-</span>
      <input type="time" value={schedule[half].end} onChange={(event) => onChange("end", event.target.value)} />
    </span>
  );
}

function ScheduleHours({ schedule }: { schedule: WorkSchedule }) {
  const workSchedule = attendanceWorkSchedule(schedule);
  return (
    <span className="schedule-hours">
      上午 {formatHours(workSchedule.workHalves.morning.hours)} / 下午 {formatHours(workSchedule.workHalves.afternoon.hours)}
    </span>
  );
}

function EmployeeCard({
  employee,
  year,
  month,
  sourceKind,
  departmentSchedules,
  onChange,
}: {
  employee: EmployeeReview;
  year: number;
  month: number;
  sourceKind: AttendanceProject["sourceKind"];
  departmentSchedules: DepartmentScheduleSettings;
  onChange: (key: string, updater: (employee: EmployeeReview) => EmployeeReview) => void;
}) {
  function updateDay(day: DayReview, patch: Partial<DayReview>) {
    onChange(employee.key, (current) => {
      const days = current.days.map((item) =>
        item.day === day.day
          ? reviewIssueForUpdatedDay({ ...item, ...patch, modified: true }, current.displayName, sourceKind)
          : item,
      );
      if (sourceKind === "exported-report") {
        return {
          ...current,
          days,
        };
      }
      return {
        ...current,
        days,
        summary: mergeSummaryWithManualOverrides(
          current.summary,
          recalculateSummary({
            sourceSummaries: current.sourceSummaries || {},
            days,
            shouldAttendanceDays: current.summary.shouldAttendanceDays,
            payrollDays: current.summary.payrollDays,
            entryDate: current.entryDate,
            workSchedule: current.departmentSchedule.schedule,
          }),
          current.manualSummaryOverrides,
        ),
      };
    });
  }

  function updateDepartment(displayDepartment: string) {
    onChange(employee.key, (current) => {
      const departmentSchedule = resolveDepartmentSchedule(displayDepartment, current.sourceDepartment, departmentSchedules);
      if (sourceKind === "exported-report") {
        return { ...current, displayDepartment, departmentSchedule };
      }
      const days = reclassifyDaysForSchedule(current.days, departmentSchedule.schedule, current.displayName);
      const summary = mergeSummaryWithManualOverrides(
        current.summary,
        recalculateSummary({
          sourceSummaries: current.sourceSummaries || {},
          days,
          shouldAttendanceDays: current.summary.shouldAttendanceDays,
          payrollDays: current.summary.payrollDays,
          entryDate: current.entryDate,
          workSchedule: departmentSchedule.schedule,
        }),
        current.manualSummaryOverrides,
      );
      return { ...current, displayDepartment, departmentSchedule, days, summary };
    });
  }

  const startOffset = monthStartOffset(year, month);
  const totalGridRows = Math.ceil((startOffset + employee.days.length) / 7);

  return (
    <section className="glass employee-card">
      <div className="person-header">
        <div className="avatar">{employee.displayName.slice(0, 1)}</div>
        <div className="person-fields">
          <input value={employee.displayName} onChange={(event) => onChange(employee.key, (current) => ({ ...current, displayName: event.target.value }))} />
          <input value={employee.displayDepartment} onChange={(event) => updateDepartment(event.target.value)} />
        </div>
        <label className="switch-line">
          <input type="checkbox" checked={employee.enabled} onChange={(event) => onChange(employee.key, (current) => ({ ...current, enabled: event.target.checked }))} />
          导出
        </label>
      </div>
      <div className="schedule-tip">
        使用 {employee.departmentSchedule.department} 时段：{formatScheduleText(employee.departmentSchedule.schedule)}
      </div>
      {sourceKind === "exported-report" && (
        <div className="schedule-tip">
          修改每日状态不会自动改汇总，请在右侧汇总中手工核对。
        </div>
      )}
      <div className="review-calendar">
        <div className="calendar-weekdays">
          {WEEKDAY_LABELS.map((label) => (
            <span key={label}>{label}</span>
          ))}
        </div>
        <div className="day-grid">
          {Array.from({ length: startOffset }, (_, index) => (
            <span key={`review-blank-${index}`} className="review-calendar-blank" />
          ))}
          {employee.days.map((day) => {
            const gridIndex = startOffset + day.day - 1;

            return (
              <div key={day.day} className={dayCardClassName(day)}>
                <div className="day-card-header">
                  <div className="day-number">{day.day}</div>
                  <DayDetailHover day={day} placement={detailPopoverPlacement(gridIndex, totalGridRows)} />
                </div>
                <select className={`status-select ${statusStyleClass(day.finalMorning)}`} value={day.finalMorning} onChange={(event) => updateDay(day, { finalMorning: event.target.value as AttendanceStatus })}>
                  {ATTENDANCE_STATUSES.map((status) => <option key={`m-${status}`} value={status}>{displayStatus(status)}</option>)}
                </select>
                <select className={`status-select ${statusStyleClass(day.finalAfternoon)}`} value={day.finalAfternoon} onChange={(event) => updateDay(day, { finalAfternoon: event.target.value as AttendanceStatus })}>
                  {ATTENDANCE_STATUSES.map((status) => <option key={`a-${status}`} value={status}>{displayStatus(status)}</option>)}
                </select>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function DayDetailHover({ day, placement }: { day: DayReview; placement: string }) {
  const popoverId = `zsjkqt-day-detail-${day.day}`;

  const popupPlacement = placement.includes("place-top")
    ? placement.includes("place-left") ? "topLeft" : placement.includes("place-right") ? "topRight" : "top"
    : placement.includes("place-left") ? "bottomLeft" : placement.includes("place-right") ? "bottomRight" : "bottom";
  return (
    <Popover rootClassName="zsjkqt-root zsjkqt-detail-overlay" placement={popupPlacement}
      trigger={["hover", "focus"]} autoAdjustOverflow getPopupContainer={() => document.body}
      content={<div id={popoverId} className="detail-popover" role="tooltip">
        <div className="detail-popover-title">
          <span>{day.day}日详情</span>
          <strong>{statusPair(day.systemMorning, day.systemAfternoon)}</strong>
        </div>
        <div className="detail-popover-row">
          <span>当前结果</span>
          <strong>{statusPair(day.finalMorning, day.finalAfternoon)}</strong>
        </div>
        <div className="detail-popover-section">
          <span>处理提醒</span>
          {day.issues.length > 0 ? (
            day.issues.map((issue, index) => (
              <p key={`${issue.message}-${index}`}>{issue.message}</p>
            ))
          ) : (
            <p>无系统异常提示。</p>
          )}
        </div>
        <div className="detail-popover-section">
          <span>原始数据</span>
          <pre>{day.rawDetail || "源表当天没有原始打卡明细。"}</pre>
        </div>
      </div>}>
      <button type="button" className="detail-button" aria-describedby={popoverId}>详细</button>
    </Popover>
  );
}

function SummaryList({
  employee,
  onChange,
}: {
  employee: EmployeeReview;
  onChange: (key: string, updater: (employee: EmployeeReview) => EmployeeReview) => void;
}) {
  function updateSummaryField(field: SummaryFieldDefinition, rawValue: string) {
    const value = field.valueType === "number" ? toNumber(rawValue) : rawValue;
    onChange(employee.key, (current) => ({
      ...current,
      entryDate: field.key === "entryDate" ? summaryInputValue(value) : current.entryDate,
      manualSummaryOverrides: {
        ...current.manualSummaryOverrides,
        [field.key]: true,
      },
      summary: {
        ...current.summary,
        [field.key]: value,
      },
    }));
  }

  return (
    <div className="summary-list">
      {SUMMARY_FIELD_DEFINITIONS.map((field) => (
        <div key={field.key} className="summary-row editable">
          <label htmlFor={`summary-${field.key}`}>{field.label}</label>
          <input
            id={`summary-${field.key}`}
            type={field.valueType === "number" ? "number" : "text"}
            step={summaryInputStep(field)}
            value={summaryInputValue(employee.summary[field.key])}
            onChange={(event) => updateSummaryField(field, event.target.value)}
            aria-invalid={field.valueType === 'text' && summaryInputValue(employee.summary[field.key]).length > 32767}
          />
          {field.valueType === 'text' && summaryInputValue(employee.summary[field.key]).length > 32767 && <small role="alert">{field.label}不能超过 32767 个字符，请缩短后保存或导出。</small>}
        </div>
      ))}
    </div>
  );
}

function updateDepartmentScheduleHalf(
  settings: DepartmentScheduleSettings,
  department: string,
  half: "morning" | "afternoon",
  field: "start" | "end",
  value: string,
): DepartmentScheduleSettings {
  const next = cloneDepartmentScheduleSettings(settings);
  const override = next.departments[department];
  if (!override || override.mode !== "custom") {
    next.departments[department] = {
      mode: "custom",
      morning: { ...next.default.morning },
      afternoon: { ...next.default.afternoon },
    };
  }
  const custom = next.departments[department];
  if (custom.mode === "custom") {
    custom[half] = { ...(custom[half] || next.default[half]), [field]: value };
  }
  return next;
}

function recalculateProjectForSchedules(project: AttendanceProject, settings: DepartmentScheduleSettings): AttendanceProject {
  return {
    ...project,
    updatedAt: new Date().toISOString(),
    employees: project.employees.map((employee) =>
      project.sourceKind === "exported-report" ? updateEmployeeScheduleOnly(employee, settings) : recalculateEmployeeForSchedules(employee, settings),
    ),
  };
}

function recalculateEmployeeForSchedules(employee: EmployeeReview, settings: DepartmentScheduleSettings): EmployeeReview {
  const departmentSchedule = resolveDepartmentSchedule(employee.displayDepartment, employee.sourceDepartment, settings);
  const days = reclassifyDaysForSchedule(employee.days, departmentSchedule.schedule, employee.displayName);
  const recalculated = recalculateSummary({
    sourceSummaries: employee.sourceSummaries || {},
    days,
    shouldAttendanceDays: employee.summary.shouldAttendanceDays,
    payrollDays: employee.summary.payrollDays,
    entryDate: employee.entryDate,
    workSchedule: departmentSchedule.schedule,
  });
  return {
    ...employee,
    departmentSchedule,
    days,
    summary: mergeSummaryWithManualOverrides(employee.summary, recalculated, employee.manualSummaryOverrides),
  };
}

function updateEmployeeScheduleOnly(employee: EmployeeReview, settings: DepartmentScheduleSettings): EmployeeReview {
  return {
    ...employee,
    departmentSchedule: resolveDepartmentSchedule(employee.displayDepartment, employee.sourceDepartment, settings),
  };
}

function reviewIssueForUpdatedDay(
  day: DayReview,
  employee: string,
  sourceKind: AttendanceProject["sourceKind"],
): DayReview {
  if (sourceKind === "exported-report") return appendSourceSummaryReviewIssue(day, employee);
  return refreshSourceSummaryReviewIssue(day, employee);
}

function departmentsFromExportedReport(report: ExportedAttendanceReport): string[] {
  return [...new Set(report.employees.map((employee) => employee.department).filter(Boolean))];
}

function summaryFromSourceSummaries(sourceSummaries: Record<string, unknown> | undefined, fallback: SummaryReview): SummaryReview {
  if (!sourceSummaries) return fallback;
  const summary = { ...fallback };
  for (const field of SUMMARY_FIELD_DEFINITIONS) {
    if (Object.prototype.hasOwnProperty.call(sourceSummaries, field.key)) {
      summary[field.key] = sourceSummaries[field.key] as never;
    }
  }
  return summary;
}

function mergeSummaryWithManualOverrides(
  current: SummaryReview,
  recalculated: SummaryReview,
  manualOverrides: Partial<Record<keyof SummaryReview, boolean>>,
): SummaryReview {
  const next = { ...recalculated };
  for (const key of Object.keys(manualOverrides) as Array<keyof SummaryReview>) {
    if (manualOverrides[key]) {
      next[key] = current[key] as never;
    }
  }
  return next;
}

function summaryInputStep(field: SummaryFieldDefinition): string | undefined {
  if (field.valueType !== "number") return undefined;
  return field.key === "actualAttendanceDays" ? "0.001" : "0.5";
}

function summaryInputValue(value: unknown): string {
  if (value == null) return "";
  return String(value);
}

function toNumber(value: string): number | null {
  if (!value.trim()) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function toggleDay(days: number[], day: number): number[] {
  return days.includes(day) ? days.filter((item) => item !== day) : [...days, day].sort((a, b) => a - b);
}

function mergeDays(current: number[], next: number[]): number[] {
  return [...new Set([...current, ...next])].sort((a, b) => a - b);
}

function restDayLabel(type: RestDayKind | ""): string {
  if (type === "paid") return "带薪";
  if (type === "rest") return "休息";
  return "未选";
}

function recoveredSourceLabel(source: RecoveredMonthlyParameters["source"]): string {
  if (source === "metadata") return "从隐藏元数据精确恢复";
  if (source === "inferred") return "从汇总表保守推断";
  return "未恢复到月度参数";
}

function candidateDaysText(candidates: RecoveredDayCandidate[]): string {
  return candidates.map((candidate) => `${candidate.day}日`).join("、");
}

function employeeItemClassName(employee: EmployeeReview, active: boolean): string {
  return ["employee-item", active ? "active" : "", employee.confirmed ? "confirmed" : ""].filter(Boolean).join(" ");
}

function statusPair(morning: AttendanceStatus, afternoon: AttendanceStatus): string {
  return `${displayStatus(morning)} / ${displayStatus(afternoon)}`;
}

function detailPopoverPlacement(gridIndex: number, totalGridRows: number): string {
  const column = gridIndex % 7;
  const row = Math.floor(gridIndex / 7);
  const vertical = row >= Math.max(totalGridRows - 2, 0) ? "place-top" : "place-bottom";
  const horizontal = column <= 1 ? "place-left" : column >= 5 ? "place-right" : "place-center";
  return `${vertical} ${horizontal}`;
}

function displayStatus(status: AttendanceStatus | string): string {
  return status || "--";
}

function dayCardClassName(day: DayReview): string {
  if (dayHasManualReviewReminder(day)) return "day-card manual-review";
  if (dayHasReviewIssue(day)) return "day-card issue";
  return "day-card";
}

function monthStartOffset(year: number, month: number): number {
  const jsDay = new Date(year, month - 1, 1).getDay();
  return (jsDay + 6) % 7;
}

function filterLabel(value: EmployeeFilter): string {
  return {
    all: "全部",
    issues: "异常",
    unconfirmed: "未确认",
    disabled: "不导出",
  }[value];
}

function emptyReviewTitle(filter: EmployeeFilter, query: string): string {
  if (query.trim()) return "没有匹配的员工";
  if (filter === "disabled") return "暂无不导出人员";
  if (filter === "issues") return "暂无异常人员";
  if (filter === "unconfirmed") return "暂无未确认人员";
  return "暂无员工";
}

function emptyReviewDescription(filter: EmployeeFilter, query: string): string {
  if (query.trim()) return "可以清空搜索，或切换到全部员工继续查看。";
  if (filter === "disabled") return "当前没有被标记为不导出的员工，可以切换回全部员工继续处理。";
  if (filter === "issues") return "当前没有需要处理的异常员工，可以切换回全部员工查看。";
  if (filter === "unconfirmed") return "当前没有待确认员工，可以切换回全部员工查看。";
  return "当前月报没有可显示的员工。";
}

function formatError(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  if (typeof error === "string" && error.trim()) return error;
  try {
    return JSON.stringify(error);
  } catch {
    return "请确认文件是企业微信导出的 .xlsx 月报，并刷新页面后重试。";
  }
}
