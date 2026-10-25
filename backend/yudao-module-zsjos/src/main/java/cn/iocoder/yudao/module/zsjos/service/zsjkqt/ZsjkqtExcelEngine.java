package cn.iocoder.yudao.module.zsjos.service.zsjkqt;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.io.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
/** Excel-only port of the desktop engine. Reviewed/manual values are never recalculated here. */
@Component
public class ZsjkqtExcelEngine {
    static final String METADATA = "_attendance_metadata";
    static final String[] TITLES = {
        "应出勤日", "实出勤日", "计薪日", "入职日期", "缺卡", "迟到", "迟到是否惩罚", "早退", "旷工", "事假/小时", "病假/天", "工伤/天", "丧假/天", "婚假/天", "产假/天", "调休/小时", "年假/小时", "加班/小时", "加班未调休换算成工资", "备注"
    };
    static final String[] FIELDS = {
        "shouldAttendanceDays", "actualAttendanceDays", "payrollDays", "entryDate", "missingCardCount", "lateText", "latePunishment", "earlyText", "absenceDays", "personalLeaveHours", "sickLeaveDays", "injuryLeaveDays", "funeralLeaveDays", "marriageLeaveDays", "maternityLeaveDays", "compLeaveHours", "annualLeaveHours", "overtimeHours", "overtimeToSalary", "remark"
    };
    static final Set<String> STATUSES = Set.of("", "出勤", "休息", "带薪", "迟到", "早退", "缺卡", "异常", "加班", "旷工", "事假", "病假", "年假", "调休", "婚假", "产假", "陪产假", "产检假", "丧假", "工伤");
    static final int MAX_CELL_TEXT_LENGTH = 32767;
    static final String[] SOURCE_FIELDS = {
        "应出勤天数(天)", "实际出勤天数(天)", "缺卡次数(次)", "迟到次数(次)", "迟到时长(分钟)", "早退次数(次)", "早退时长(分钟)", "旷工次数(次)", "旷工时长(分钟)", "事假(小时)", "病假(小时)", "年假(小时)", "调休假(小时)", "调休(小时)", "婚假(天)", "产假(天)", "丧假(天)", "加班时长(小时)"
    };
    static String text(JsonNode n, String key) {
        return n.path(key).isNull() || n.path(key).isMissingNode() ? "" : n.path(key).asText();
    }
    static String display(String s) {
        return switch(s) {
            case "√" -> "出勤";
            case "△", "休" -> "休息";
            case "带薪假" -> "带薪";
            default -> s;
        };
    }
    static String blank(String s) {
        return s.isEmpty() ? "--" : s;
    }
    static String combined(String a, String b) {
        return "上午："+blank(display(a))+"\n下午："+blank(display(b));
    }
    static String dateHeader(int y, int m, int d) {
        return m+"月"+d+"日"+LocalDate.of(y, m, d).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
    }
    static int summaryStart(int y, int m) {
        return 4+YearMonth.of(y, m).lengthOfMonth();
    }
    static int[] sheetMonth(String s) {
        var matcher=Pattern.compile("^(20\\d{2})年(\\d{1,2})月考勤$").matcher(s);
        if(!matcher.matches()) throw formatError();
        int y=Integer.parseInt(matcher.group(1)), m=Integer.parseInt(matcher.group(2));
        if(m<1||m>12) throw formatError();
        return new int[] {
            y, m
        };
    }
    static IllegalArgumentException formatError() {
        return new IllegalArgumentException("请选择由中世健考勤通导出的正式考勤汇总表。");
    }
    private static Cell cell(Sheet s, int r, int c) {
        if(c<0)return null;
        Row row=s.getRow(r);
        return row==null ? null : row.getCell(c);
    }
    static Object value(Sheet s, int r, int c) {
        Cell cell=cell(s, r, c);
        if(cell==null) return null;
        CellType type=cell.getCellType()==CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch(type) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> cell.getBooleanCellValue();
            case ERROR -> FormulaError.forInt(cell.getErrorCellValue()).getString();
            default -> null;
        };
    }
    static String cellText(Sheet s, int r, int c) {
        Object v=value(s, r, c);
        return v==null ? "" : v instanceof Double d && d==Math.rint(d) ? Long.toString(d.longValue()) : v.toString().trim();
    }
    public Map<String, Object> parseSource(byte[] bytes, String filename) throws IOException {
        try(Workbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if(book.getNumberOfSheets()==0) throw new IllegalArgumentException("无法读取企业微信月报工作表");
            Sheet s=book.getSheetAt(0);
            String start, end;
            int year, month;
            var dates=Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})-(20\\d{2})(\\d{2})(\\d{2})").matcher(filename);
            if(dates.find()) {
                year=Integer.parseInt(dates.group(1));
                month=Integer.parseInt(dates.group(2));
                start=dates.group(1)+"-"+dates.group(2)+"-"+dates.group(3);
                end=dates.group(4)+"-"+dates.group(5)+"-"+dates.group(6);
            }
            else {
                var stat=Pattern.compile("统计时间:(\\d{2})-(\\d{2})\\s*[～~-]\\s*(\\d{2})-(\\d{2})").matcher(cellText(s, 1, 0));
                if(!stat.find()) throw new IllegalArgumentException("无法从文件名或统计时间识别月份");
                // Compatibility with the original desktop fallback; never infer a new year silently.
                year=2026;
                month=Integer.parseInt(stat.group(1));
                start=year+"-"+stat.group(1)+"-"+stat.group(2);
                end=year+"-"+stat.group(3)+"-"+stat.group(4);
            }
            int count=YearMonth.of(year, month).lengthOfMonth();
            Map<String, Integer> cols=new LinkedHashMap<>();
            Map<Integer, Integer> days=new TreeMap<>();
            int width=Math.max(s.getRow(2)==null?0:s.getRow(2).getLastCellNum(), s.getRow(3)==null?0:s.getRow(3).getLastCellNum());
            for(int c=0; c<width; c++) for(int r=2; r<=3; r++) {
                String key=cellText(s, r, c);
                if(!key.isEmpty()) cols.put(key, c);
            }
            Row dayrow=s.getRow(3);
            if(dayrow!=null) for(int c=0; c<dayrow.getLastCellNum(); c++) {
                var day=Pattern.compile("^\\s*(\\d{1,2})\\s*(?:\\n|$)").matcher(cellText(s, 3, c));
                if(day.find()) days.put(Integer.parseInt(day.group(1)), c);
            }
            List<Map<String, Object>> employees=new ArrayList<>();
            List<Map<String, Object>> issues=new ArrayList<>();
            Set<String> names=new HashSet<>();
            for(int r=4; r<=s.getLastRowNum(); r++) {
                String name=cellText(s, r, cols.getOrDefault("姓名", -1));
                if(name.isEmpty()) continue;
                if(!names.add(name)) {
                    Map<String, Object> issue=new LinkedHashMap<>();
                    issue.put("level", "warning");
                    issue.put("employee", name);
                    issue.put("day", null);
                    issue.put("message", "同名人员出现多条记录");
                    issue.put("rawValue", "");
                    issues.add(issue);
                }
                Map<String, Object> e=new LinkedHashMap<>(), summaries=new LinkedHashMap<>(), details=new LinkedHashMap<>();
                String[] keys= {
                    "name", "account", "rule", "department", "position", "jobNo"
                }, labels= {
                    "姓名", "账号", "所属规则", "部门", "职务", "工号"
                };
                for(int i=0; i<keys.length; i++) e.put(keys[i], cellText(s, r, cols.getOrDefault(labels[i], -1)));
                for(String f:SOURCE_FIELDS) if(cols.containsKey(f)) summaries.put(f, value(s, r, cols.get(f)));
                for(var d:days.entrySet()) if(d.getKey()<=count) details.put(d.getKey().toString(), cellText(s, r, d.getValue()));
                e.put("summaries", summaries);
                e.put("dayDetails", details);
                employees.add(e);
            }
            Map<String, Object> attendance=new LinkedHashMap<>();
            attendance.put("year", year);
            attendance.put("month", month);
            attendance.put("startDate", start);
            attendance.put("endDate", end);
            attendance.put("sourceFileName", filename);
            attendance.put("sourceFileHash", sha256(bytes));
            attendance.put("employees", employees);
            return Map.of("attendance", attendance, "issues", issues);
        }
    }
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch(java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    public Map<String, Object> parseReport(byte[] bytes, String filename) throws IOException {
        try(Workbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if(book.getNumberOfSheets()==0) throw formatError();
            Sheet s=book.getSheetAt(0);
            int[] ym=sheetMonth(s.getSheetName());
            int year=ym[0], month=ym[1], count=YearMonth.of(year, month).lengthOfMonth(), first=4+count;
            String[] headers= {
                "序号", "部门", "姓名", "考勤"
            };
            for(int c=0; c<4; c++) if(!headers[c].equals(cellText(s, 0, c))) throw formatError();
            for(int d=1; d<=count; d++) if(!dateHeader(year, month, d).equals(cellText(s, 0, 3+d))) throw formatError();
            for(int i=0; i<TITLES.length; i++) if(!TITLES[i].equals(cellText(s, 0, first+i))) throw formatError();
            List<Map<String, Object>> employees=new ArrayList<>();
            for(int r=1; r<=s.getLastRowNum(); r++) {
                String department=cellText(s, r, 1), name=cellText(s, r, 2);
                if(name.isEmpty()&&department.isEmpty()) continue;
                if(name.isEmpty()||department.isEmpty()) throw new IllegalArgumentException("正式考勤汇总表第 "+(r+1)+" 行缺少姓名或部门。");
                List<Map<String, Object>> days=new ArrayList<>();
                for(int d=1; d<=count; d++) {
                    String[] pair=parsePair(cellText(s, r, 3+d), r+1, d);
                    days.add(Map.of("day", d, "morning", pair[0], "afternoon", pair[1]));
                }
                Map<String, Object> summary=new LinkedHashMap<>();
                for(int i=0; i<FIELDS.length; i++) {
                    Object v=value(s, r, first+i);
                    summary.put(FIELDS[i], "--".equals(v)||"".equals(v)?null:v);
                }
                employees.add(Map.of("name", name, "department", department, "sourceIndex", employees.size()+1, "days", days, "summary", summary));
            }
            if(employees.isEmpty()) throw formatError();
            Map<String, Object> result=new LinkedHashMap<>();
            result.put("year", year);
            result.put("month", month);
            result.put("employees", employees);
            result.put("sourceFileName", filename);
            result.put("sourceFileHash", sha256(bytes));
            result.put("recoveredParameters", recover(book, employees, year, month));
            return result;
        }
    }
    static String[] parsePair(String text, int row, int day) {
        var pair=Pattern.compile("上午[:：]\\s*([^\\r\\n]+)\\s*[\\r\\n]+\\s*下午[:：]\\s*([^\\r\\n]+)").matcher(text);
        if(!pair.find()) throw new IllegalArgumentException("正式考勤汇总表第 "+row+" 行 "+day+" 日状态格式不正确。");
        String[] result= {
            pair.group(1).trim(), pair.group(2).trim()
        };
        for(int i=0; i<2; i++) {
            String raw=result[i];
            result[i]="--".equals(raw)?"":"带薪假".equals(raw)?"带薪":raw;
            if(!STATUSES.contains(result[i])) throw new IllegalArgumentException("正式考勤汇总表第 "+row+" 行 "+day+" 日"+(i==0?"上午":"下午")+"状态不支持："+raw);
        }
        return result;
    }
    private Map<String, Object> recover(Workbook book, List<Map<String, Object>> employees, int year, int month) {
        Map<String, Object> out=new LinkedHashMap<>();
        List<String>warnings=new ArrayList<>();
        Sheet meta=book.getSheet(METADATA);
        Map<String, String> values=new HashMap<>();
        if(meta!=null) for(int r=0; r<=meta.getLastRowNum(); r++) values.put(cellText(meta, r, 0), cellText(meta, r, 1));
        boolean valid="zhongshijian-kaoqintong".equals(values.get("appName"))&&"1".equals(values.get("schemaVersion"));
        boolean matching=Integer.toString(year).equals(values.get("year"))&&Integer.toString(month).equals(values.get("month"));
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        boolean validDays = metadataDaysValid(values.get("restDays"), daysInMonth)
                && metadataDaysValid(values.get("paidRestDays"), daysInMonth);
        if(meta!=null&&!valid) warnings.add("隐藏元数据格式不正确，已改用汇总表推断。");
        else if(meta!=null&&!matching) warnings.add("隐藏元数据年月与工作表不一致，已改用汇总表推断。");
        else if(meta!=null&&!validDays) warnings.add("隐藏元数据包含本月不存在或无效的休息日期，已改用汇总表推断。");
        boolean metadata=valid&&matching&&validDays;
        out.put("source", metadata?"metadata":"inferred");
        for(String f:List.of("shouldAttendanceDays", "payrollDays")) {
            if(metadata) out.put(f, number(values.get(f)));
            else {
                Set<Double> nums=new LinkedHashSet<>();
                for(var e:employees) {
                    Object s=e.get("summary");
                    Double n=number(((Map<?, ?>)s).get(f));
                    if(n!=null)nums.add(n);
                }
                out.put(f, nums.size()==1?nums.iterator().next():null);
                if(nums.size()>1) warnings.add("不同员工的"+(f.equals("payrollDays")?"计薪日":"应出勤日")+"不一致，请手工确认。");
            }
        }
        out.put("restDays", metadata?dayList(values.get("restDays")):List.of());
        out.put("paidRestDays", metadata?dayList(values.get("paidRestDays")):List.of());
        List<Map<String, Object>> rest=metadata?List.of():candidates(employees, year, month, "休息"), paid=metadata?List.of():candidates(employees, year, month, "带薪");
        for(var r:rest) if(paid.stream().anyMatch(p->Objects.equals(p.get("day"), r.get("day")))) warnings.add(r.get("day")+" 日同时符合休息日和带薪休息日候选，请手工确认。");
        out.put("restDayCandidates", rest);
        out.put("paidRestDayCandidates", paid);
        out.put("warnings", warnings);
        return out;
    }
    static Double number(Object v) {
        try {
            if(v==null||v.toString().isBlank())return null;
            double d=Double.parseDouble(v.toString());
            return Double.isFinite(d)?d:null;
        }
        catch(NumberFormatException e) {
            return null;
        }
    }
    static List<Integer> dayList(String s) {
        if(s==null||s.isBlank())return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x->x.matches("\\d+")).map(java.math.BigInteger::new).filter(x->x.signum()>0&&x.compareTo(java.math.BigInteger.valueOf(31))<=0).map(java.math.BigInteger::intValue).toList();
    }
    private static boolean metadataDaysValid(String value, int daysInMonth) {
        if (value == null || value.isBlank()) return true;
        for (String item : value.split(",", -1)) {
            String day = item.trim();
            if (!day.matches("\\d+")) return false;
            var number = new java.math.BigInteger(day);
            if (number.signum() <= 0 || number.compareTo(java.math.BigInteger.valueOf(daysInMonth)) > 0) return false;
        }
        return true;
    }
    private List<Map<String, Object>> candidates(List<Map<String, Object>> employees, int year, int month, String status) {
        List<Map<String, Object>> result=new ArrayList<>();
        var nodes=JsonUtils.getObjectMapper().valueToTree(employees);
        for(int d=1; d<=YearMonth.of(year, month).lengthOfMonth(); d++) {
            int matches=0;
            for(JsonNode e:nodes) for(JsonNode day:e.path("days")) if(day.path("day").asInt()==d&&status.equals(text(day, "morning"))&&status.equals(text(day, "afternoon"))) matches++;
            if(!employees.isEmpty()&&(double)matches/employees.size()>=.8) result.add(Map.of("day", d, "confidence", matches==employees.size()?"high":"medium", "matchedEmployees", matches, "totalEmployees", employees.size()));
        }
        return result;
    }
    static List<JsonNode> sortedEmployees(JsonNode project) {
        List<JsonNode> rows=new ArrayList<>();
        Map<String, Integer> order=new LinkedHashMap<>();
        for(JsonNode e:project.path("employees")) if(e.path("enabled").asBoolean(true)) {
            rows.add(e);
            order.computeIfAbsent(text(e, "displayDepartment"), k->order.size());
        }
        rows.sort(Comparator.comparingInt((JsonNode e)->order.get(text(e, "displayDepartment"))).thenComparingLong(e->e.path("sourceIndex").asLong(Long.MAX_VALUE)));
        return rows;
    }
    static String styleKey(String status) {
        return switch(status) {
            case "迟到"->"late";
            case "缺卡"->"missing";
            case "事假"->"personal";
            case "病假", "工伤", "丧假", "婚假", "产假", "年假"->"benefit";
            case "早退"->"early";
            case "异常"->"abnormal";
            case "旷工"->"absence";
            default->"normal";
        };
    }
    static String pairStyle(String a, String b) {
        String x=styleKey(a), y=styleKey(b);
        for(String p:List.of("abnormal", "absence", "missing", "personal", "late", "early", "benefit")) if(p.equals(x)||p.equals(y))return p;
        return "normal";
    }
    static String background(String key) {
        return switch(key) {
            case "late"->"FFEB9C";
            case "missing"->"FFC7CE";
            case "personal"->"DDEBF7";
            case "benefit"->"D9EAD3";
            case "early"->"FCE4D6";
            case "abnormal"->"EADCF8";
            case "absence"->"7F1D1D";
            default->null;
        };
    }
    private CellStyle style(XSSFWorkbook book, boolean bold, boolean center, String key) {
        XSSFCellStyle s=book.createCellStyle();
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        if(center)s.setAlignment(HorizontalAlignment.CENTER);
        s.setWrapText(true);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        XSSFFont font=book.createFont();
        font.setBold(bold);
        s.setFont(font);
        String bg=background(key);
        if(bg!=null) {
            s.setFillForegroundColor(color(bg));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            String fg=switch(key) {
                case "late"->"9C6500";
                case "missing"->"9C0006";
                case "personal"->"1F4E79";
                case "benefit"->"0F5132";
                case "early"->"FF6600";
                case "abnormal"->"C000FF";
                case "absence"->"FFFFFF";
                default->"000000";
            };
            font.setColor(color(fg));
            font.setBold(true);
        }
        return s;
    }
    private XSSFColor color(String hex) {
        return new XSSFColor(HexFormat.of().parseHex(hex), new DefaultIndexedColorMap());
    }
    private static void put(Row row, int col, Object value, CellStyle style) {
        Cell c=row.createCell(col);
        if(value instanceof Number n)c.setCellValue(n.doubleValue());
        else {
            String text = value == null ? "" : value.toString();
            requireCellText(text, "导出表第" + (row.getRowNum() + 1) + "行第" + (col + 1) + "列");
            c.setCellValue(text);
        }
        if(style!=null)c.setCellStyle(style);
    }
    static void requireCellText(String value, String label) {
        if (value.length() > MAX_CELL_TEXT_LENGTH) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    ZsjkqtErrors.INVALID, label + "不能超过 " + MAX_CELL_TEXT_LENGTH + " 个字符，请缩短后保存或导出");
        }
    }
    private static Object jsonValue(JsonNode n) {
        if(n.isNull()||n.isMissingNode())return "";
        return n.isNumber()?n.asDouble():n.asText();
    }
    public byte[] exportReport(JsonNode project) throws IOException {
        int y=project.path("year").asInt(), m=project.path("month").asInt(), count=YearMonth.of(y, m).lengthOfMonth(), first=4+count;
        try(XSSFWorkbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Sheet s=book.createSheet(y+"年"+m+"月考勤");
            CellStyle header=style(book, true, true, "normal"), normal=style(book, false, true, "normal");
            Map<String, CellStyle> styles=new HashMap<>();
            for(String key:List.of("normal", "late", "missing", "personal", "benefit", "early", "abnormal", "absence"))styles.put(key, style(book, false, true, key));
            Row h=s.createRow(0);
            h.setHeightInPoints(34);
            String[] leading= {
                "序号", "部门", "姓名", "考勤"
            };
            for(int c=0; c<4; c++)put(h, c, leading[c], header);
            double[] leadingWidths= {
                6, 16, 12, 8
            };
            for(int c=0; c<4; c++)s.setColumnWidth(c, (int)(leadingWidths[c]*256));
            for(int d=1; d<=count; d++) {
                put(h, 3+d, dateHeader(y, m, d), header);
                s.setColumnWidth(3+d, 18*256);
            }
            double[] widths= {
                10, 10, 9, 12, 8, 10, 14, 10, 10, 12, 10, 10, 10, 10, 10, 12, 12, 12, 26, 18
            };
            for(int i=0; i<TITLES.length; i++) {
                put(h, first+i, TITLES[i], header);
                s.setColumnWidth(first+i, (int)(widths[i]*256));
            }
            int index=1;
            for(JsonNode e:sortedEmployees(project)) {
                Row row=s.createRow(index);
                row.setHeightInPoints(38);
                put(row, 0, Integer.toString(index), normal);
                put(row, 1, blank(text(e, "displayDepartment")), normal);
                put(row, 2, blank(text(e, "displayName")), normal);
                put(row, 3, "考勤", normal);
                for(int day=1; day<=count; day++)put(row, 3+day, combined("", ""), normal);
                for(JsonNode d:e.path("days")) {
                    int day=d.path("day").asInt();
                    if(day<1||day>count)continue;
                    String a=text(d, "finalMorning"), b=text(d, "finalAfternoon");
                    put(row, 3+day, combined(a, b), styles.get(pairStyle(a, b)));
                }
                for(int i=0; i<FIELDS.length; i++) {
                    JsonNode value=e.path("summary").path(FIELDS[i]);
                    if(value.isNumber() || value.isTextual() && !value.asText().isEmpty())put(row, first+i, jsonValue(value), normal);
                }
                index++;
            }
            Sheet meta=book.createSheet(METADATA);
            Map<String, String> values=new LinkedHashMap<>();
            values.put("appName", "zhongshijian-kaoqintong");
            values.put("schemaVersion", "1");
            values.put("year", Integer.toString(y));
            values.put("month", Integer.toString(m));
            for(String f:List.of("shouldAttendanceDays", "payrollDays"))values.put(f, text(project, f));
            for(String f:List.of("restDays", "paidRestDays")) {
                List<String> ds=new ArrayList<>();
                for(JsonNode n:project.path(f))ds.add(n.asText());
                values.put(f, String.join(",", ds));
            }
            int r=0;
            for(var entry:values.entrySet()) {
                Row row=meta.createRow(r++);
                put(row, 0, entry.getKey(), null);
                put(row, 1, entry.getValue(), null);
            }
            book.setSheetVisibility(1, SheetVisibility.VERY_HIDDEN);
            book.write(out);
            return out.toByteArray();
        }
    }
    static boolean shouldExportIssue(JsonNode day, int issueCount) {
        boolean abnormal=Stream.of("systemMorning", "systemAfternoon", "finalMorning", "finalAfternoon").map(k->text(day, k)).anyMatch("异常"::equals);
        boolean changed=!text(day, "systemMorning").equals(text(day, "finalMorning"))||!text(day, "systemAfternoon").equals(text(day, "finalAfternoon"))||!text(day, "morningNote").isEmpty()||!text(day, "afternoonNote").isEmpty();
        return abnormal&&(issueCount>0||changed);
    }
    public byte[] exportIssues(JsonNode project) throws IOException {
        try(XSSFWorkbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Sheet s=book.createSheet("异常信息");
            CellStyle header=style(book, true, true, "normal"), body=style(book, false, false, "normal");
            String[] titles= {
                "序号", "员工", "部门", "日期", "异常类型", "原始明细", "系统判断", "修改前", "修改后", "备注", "确认时间"
            };
            int[] widths= {
                6, 12, 16, 10, 14, 28, 16, 16, 16, 26, 24
            };
            Row h=s.createRow(0);
            h.setHeightInPoints(28);
            for(int i=0; i<titles.length; i++) {
                put(h, i, titles[i], header);
                s.setColumnWidth(i, widths[i]*256);
            }
            int row=1;
            String at=Long.toString(Instant.now().getEpochSecond());
            for(JsonNode e:project.path("employees")) {
                if(!e.path("enabled").asBoolean(true)) {
                    Object[] data= {
                        row, text(e, "displayName"), text(e, "displayDepartment"), "", "未导出人员", "", "源表存在该员工", "导出", "不导出", text(e, "notes"), at
                    };
                    Row r=s.createRow(row++);
                    for(int i=0; i<data.length; i++)put(r, i, data[i], body);
                }
                for(JsonNode d:e.path("days")) {
                    List<String> issues=new ArrayList<>();
                    for(JsonNode issue:d.path("issues"))if(!"人工修改，汇总数据待核对".equals(text(issue, "message")))issues.add(text(issue, "message"));
                    if(!shouldExportIssue(d, issues.size()))continue;
                    String before=blank(text(d, "systemMorning"))+" / "+blank(text(d, "systemAfternoon")), after=blank(text(d, "finalMorning"))+" / "+blank(text(d, "finalAfternoon"));
                    String note=Stream.of(text(d, "morningNote"), text(d, "afternoonNote")).filter(x->!x.isEmpty()).collect(Collectors.joining("；"));
                    Object[] data= {
                        row, text(e, "displayName"), text(e, "displayDepartment"), Integer.toString(d.path("day").asInt()), issues.isEmpty()?"人工修改":String.join("；", issues), text(d, "rawDetail"), before, before, after, note, at
                    };
                    Row r=s.createRow(row++);
                    for(int i=0; i<data.length; i++)put(r, i, data[i], body);
                }
            }
            book.write(out);
            return out.toByteArray();
        }
    }
}
