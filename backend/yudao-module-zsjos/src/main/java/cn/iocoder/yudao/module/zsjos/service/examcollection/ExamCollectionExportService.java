package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionExportVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.VERSION_CONFLICT;

@Service
public class ExamCollectionExportService {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final int MAX_FILES = 2000;
    @Resource private ExamCollectionPermissionProvider permissions;
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionMemberMapper members;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionQueryService queries;
    @Resource private StudentServiceArchiveStore archives;
    @Resource private StudentServiceArchiveFacade archiveFiles;

    record Item(String path, Long relationId, Long submissionId, Long attachmentId, String source, String fieldKey, long size, int revision) {}
    record Plan(List<Item> items, List<Field> fields, List<List<String>> rows, String title, Preview preview) {}

    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public Settings settings(Long taskId) {
        check(taskId);
        return stored(tasks.selectById(taskId));
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    public String downloadName(Long taskId) {
        check(taskId);
        String timestamp = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return ExamCollectionExportNaming.safe(tasks.selectById(taskId).getTitle()) + "(" + timestamp + ").zip";
    }

    private Settings stored(ExamCollectionTaskDO task) {
        return task.getExportSettingsJson() == null ? new Settings() : JsonUtils.parseObject(task.getExportSettingsJson(), Settings.class);
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    @Transactional(rollbackFor=Exception.class)
    public Settings save(Long taskId, Settings settings) {
        check(taskId);
        var task = tasks.lock(taskId);
        ExamCollectionExportNaming.validate(settings, labels(task).keySet());
        if (stored(task).getRevision() != settings.getRevision()) throw exception(VERSION_CONFLICT);
        settings.setRevision(settings.getRevision() + 1);
        // Export preferences have their own revision and never change collection lifecycle state.
        var update = new ExamCollectionTaskDO();
        update.setId(taskId); update.setExportSettingsJson(JsonUtils.toJsonString(settings));
        tasks.updateById(update);
        return settings;
    }
    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Preview preview(Long taskId, Request request) { return plan(taskId, request).preview(); }

    @ZsjosPermission(bizType="exam-collection", bizId="#taskId", action="query")
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Path download(Long taskId, Request request) throws IOException {
        var plan = plan(taskId, request);
        if (request.getFingerprint() == null || !request.getFingerprint().equals(plan.preview().fingerprint())) throw exception(VERSION_CONFLICT);
        if (plan.rows().isEmpty()) throw exception(FIELD_INVALID, "没有可导出的人员资料");
        Path output = Files.createTempFile("exam-material-export-", ".zip");
        boolean completed = false;
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
                long bytes = 0;
                var pictures = new HashMap<String, byte[]>();
                for (var item : plan.items()) {
                    byte[] content = item.submissionId() == null
                            ? archiveFiles.read(item.relationId(), item.attachmentId(), item.source())
                            : queries.attachment(taskId, item.submissionId(), item.attachmentId());
                    bytes += content.length;
                    if (bytes > MAX_BYTES || content.length != item.size()) throw exception(FIELD_INVALID, "材料大小发生变化，请重新预览");
                    if (item.path().matches("(?i).*\\.(jpg|png|webp)$")) pictures.put(item.path(), thumbnail(content));
                    zip.putNextEntry(new ZipEntry(item.path())); zip.write(content); zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("(" + ExamCollectionExportNaming.safe(plan.title()) + ")表格视图.xlsx"));
                zip.write(workbook(plan, pictures));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("导出说明.txt"));
                zip.write(("中世健考试资料导出\n人员数 " + plan.preview().people() + "\n附件数 " + plan.preview().files()
                        + "\n" + String.join("\n", plan.preview().warnings())).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            check(taskId);
            completed = true;
            return output;
        } finally { if (!completed) Files.deleteIfExists(output); }
    }

    private Plan plan(Long taskId, Request request) {
        check(taskId);
        var task = tasks.selectById(taskId);
        var labels = labels(task);
        var settings = request.getSettings();
        ExamCollectionExportNaming.validate(settings, labels.keySet());
        if (!labels.keySet().containsAll(request.getFieldKeys())) throw exception(FIELD_INVALID, "材料范围");
        var roster = members.selectList(new LambdaQueryWrapperX<ExamCollectionMemberDO>()
                .eq(ExamCollectionMemberDO::getTaskId, taskId).eq(ExamCollectionMemberDO::getActive, true)
                .orderByAsc(ExamCollectionMemberDO::getId).last("LIMIT 10001"));
        var byService = new LinkedHashMap<Long, ExamCollectionMemberDO>();
        roster.forEach(row -> byService.put(row.getServiceRelationId(), row));
        var submitted = submissions.selectList(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getTaskId, taskId).isNotNull(ExamCollectionSubmissionDO::getSubmittedAt)
                .orderByAsc(ExamCollectionSubmissionDO::getSequenceNo).last("LIMIT 10001"));
        if (submitted.size() > 10000 || roster.size() > 10000) throw exception(FIELD_INVALID, "任务记录过多，请分任务导出");
        var candidates = new LinkedHashSet<Long>(byService.keySet());
        if (request.isIncludeAdditional()) submitted.stream().filter(s -> ARCHIVED.equals(s.getStatus()))
                .map(ExamCollectionSubmissionDO::getServiceRelationId).filter(Objects::nonNull).forEach(candidates::add);
        if (!candidates.containsAll(request.getServiceIds())) throw exception(FIELD_INVALID, "所选人员已不在本任务导出范围，请刷新");
        var fields = ExamCollectionFields.liveFields(JsonUtils.parseArray(task.getFieldsSnapshot(), Field.class)).stream()
                .filter(f -> !"images".equals(f.getType()) || request.getFieldKeys().isEmpty() || request.getFieldKeys().contains(f.getKey())).toList();
        var rows = new ArrayList<List<String>>();
        var items = new ArrayList<Item>(); var paths = new HashSet<String>(); var warnings = new ArrayList<String>();
        var people = new HashSet<String>(); var directories = new HashMap<String, String>(); var usedDirectories = new HashSet<String>();
        if (request.getMode() == Mode.ARCHIVE) {
            for (Long relationId : candidates) {
                var member = byService.get(relationId);
                if (!selected(request, relationId, member)) continue;
                var archive = archives.current(relationId);
                var values = ExamCollectionQueryService.display(archive.answers());
                var latest = submitted.stream().filter(s -> Objects.equals(relationId, s.getServiceRelationId()))
                        .max(Comparator.comparing(ExamCollectionSubmissionDO::getSequenceNo)).orElse(null);
                // Attendance is kept on the submission rather than the archive; show the latest answer for this task.
                if (latest != null) ExamCollectionQueryService.display(JsonUtils.parseArray(latest.getAnswersJson(),
                        cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO.class)).forEach((key, value) -> {
                    if (ExamCollectionFields.ATTENDANCE_KEY.equals(key) || ExamCollectionFields.ABSENCE_REASON_KEY.equals(key)) values.put(key, value);
                });
                if (!matches(values, request.getKeyword())) continue;
                String person = "service:" + relationId;
                people.add(person);
                var selected = archive.attachments().stream().filter(a -> labels.containsKey(a.fieldKey())
                        && (request.getFieldKeys().isEmpty() || request.getFieldKeys().contains(a.fieldKey()))).toList();
                missing(warnings, values, labels, request, selected.stream().map(StudentServiceArchiveStore.Attachment::fieldKey).toList());
                int start = items.size();
                for (var file : selected) add(items, paths, directories, usedDirectories, request, labels, values, member, person,
                        relationId, null, latest == null ? null : latest.getSubmittedAt(), "", file.id(), file.source(), file.fieldKey(), file.side(), file.name(), file.contentType(), file.size(), archive.revision());
                rows.add(tableRow(fields, values, items.subList(start, items.size())));
            }
            warnings.add("当前有效资料包含所选材料对应的已有报名附件和后续档案修订，缺交状态不代表档案中没有资料。");
        } else {
            for (var submission : submitted) {
                Long relationId = submission.getServiceRelationId(); var member = byService.get(relationId);
                if (member == null && !request.isIncludeAdditional()) continue;
                if (relationId != null && !request.getServiceIds().isEmpty() && !request.getServiceIds().contains(relationId)) continue;
                if (relationId == null && !request.getServiceIds().isEmpty()) continue;
                if (!selected(request, relationId, member)) continue;
                var detail = queries.submission(taskId, submission.getId());
                if (!matches(detail.getValues(), request.getKeyword())) continue;
                String person = "submission:" + submission.getId(); people.add(person);
                var selected = detail.getAttachments().stream().filter(a -> labels.containsKey(a.getFieldKey())
                        && (request.getFieldKeys().isEmpty() || request.getFieldKeys().contains(a.getFieldKey()))).toList();
                missing(warnings, detail.getValues(), labels, request, selected.stream().map(a -> a.getFieldKey()).toList());
                int start = items.size();
                for (var file : selected) add(items, paths, directories, usedDirectories, request, labels, detail.getValues(), member, person,
                        relationId, submission.getId(), submission.getSubmittedAt(), "第" + submission.getSequenceNo() + "次提交_" + statusLabel(submission.getStatus()), file.getId(), "exam", file.getFieldKey(), file.getSide(), file.getName(), file.getContentType(), file.getSize(), submission.getRevision());
                rows.add(tableRow(fields, detail.getValues(), items.subList(start, items.size())));
            }
            warnings.add("原始提交按提交记录分别计数和分组，包含历史、异常及已标无效记录，状态见目录前缀。");
        }
        long bytes = items.stream().mapToLong(Item::size).sum();
        if (items.size() > MAX_FILES || bytes > MAX_BYTES) throw exception(FIELD_INVALID, "单次最多 2000 个附件、512 MB，请缩小范围");
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(task.getRevision(), task.getTitle(), items, fields, rows, warnings, settings, request.getMode())));
        return new Plan(items, fields, rows, task.getTitle(), new Preview(people.size(), items.size(), bytes, items.stream().limit(80).map(Item::path).toList(), warnings, fingerprint));
    }
    private List<String> tableRow(List<Field> fields, Map<String, String> values, List<Item> files) {
        var row = new ArrayList<String>();
        for (var field : fields) {
            row.add("images".equals(field.getType())
                    ? String.join("\n", files.stream().filter(f -> field.getKey().equals(f.fieldKey())).map(Item::path).toList())
                    : Objects.toString(values.get(field.getKey()), ""));
        }
        return row;
    }

    private byte[] thumbnail(byte[] content) throws IOException {
        try (var input = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw exception(FIELD_INVALID, "图片无法生成 Excel 预览");
            var reader = readers.next();
            try {
                reader.setInput(input);
                var param = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(reader.getWidth(0), reader.getHeight(0)) / 240);
                param.setSourceSubsampling(sample, sample, 0, 0);
                var image = reader.read(0, param);
                double scale = Math.min(1, 160.0 / Math.max(image.getWidth(), image.getHeight()));
                var preview = new java.awt.image.BufferedImage(Math.max(1, (int) (image.getWidth() * scale)),
                        Math.max(1, (int) (image.getHeight() * scale)), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = preview.createGraphics();
                try {
                    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(image, 0, 0, preview.getWidth(), preview.getHeight(), null);
                } finally { graphics.dispose(); }
                try (var output = new ByteArrayOutputStream()) {
                    javax.imageio.ImageIO.write(preview, "png", output);
                    return output.toByteArray();
                }
            } finally { reader.dispose(); }
        }
    }

    private byte[] workbook(Plan plan, Map<String, byte[]> pictures) throws IOException {
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("表格视图");
            sheet.createFreezePane(0, 1);
            var header = sheet.createRow(0);
            header.setHeightInPoints(42);
            var textStyle = book.createCellStyle();
            textStyle.setWrapText(true);
            textStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            var linkStyle = book.createCellStyle();
            linkStyle.cloneStyleFrom(textStyle);
            var font = book.createFont();
            font.setColor(org.apache.poi.ss.usermodel.IndexedColors.BLUE.getIndex());
            font.setUnderline(org.apache.poi.ss.usermodel.Font.U_SINGLE);
            linkStyle.setFont(font);
            var drawing = sheet.createDrawingPatriarch();
            int column = 0;
            for (int fieldIndex = 0; fieldIndex < plan.fields().size(); fieldIndex++) {
                var field = plan.fields().get(fieldIndex);
                boolean attachments = "images".equals(field.getType());
                final int index = fieldIndex;
                int count = attachments ? plan.rows().stream().mapToInt(r -> r.get(index).split("\n").length).max().orElse(1) : 1;
                for (int part = 0; part < count; part++, column++) {
                    var label = header.createCell(column);
                    label.setCellValue(field.getLabel() + (count > 1 ? "（" + (part + 1) + "）" : ""));
                    label.setCellStyle(textStyle);
                    sheet.setColumnWidth(column, (attachments ? 28 : 24) * 256);
                    for (int rowIndex = 0; rowIndex < plan.rows().size(); rowIndex++) {
                        var row = sheet.getRow(rowIndex + 1);
                        if (row == null) { row = sheet.createRow(rowIndex + 1); row.setHeightInPoints(84); }
                        var cell = row.createCell(column);
                        cell.setCellStyle(textStyle);
                        String value = plan.rows().get(rowIndex).get(fieldIndex);
                        if (!attachments) { cell.setCellValue(value); continue; }
                        var paths = value.split("\n");
                        if (part >= paths.length || paths[part].isBlank()) continue;
                        String path = paths[part];
                        byte[] picture = pictures.get(path);
                        if (picture != null) {
                            int pictureId = book.addPicture(picture, org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG);
                            var anchor = book.getCreationHelper().createClientAnchor();
                            anchor.setCol1(column); anchor.setRow1(rowIndex + 1);
                            var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(picture));
                            double scale = Math.min(180.0 / image.getWidth(), 100.0 / image.getHeight());
                            anchor.setCol2(column); anchor.setRow2(rowIndex + 1);
                            anchor.setDx1(org.apache.poi.util.Units.pixelToEMU(4));
                            anchor.setDy1(org.apache.poi.util.Units.pixelToEMU(4));
                            anchor.setDx2(org.apache.poi.util.Units.pixelToEMU(4 + (int) (image.getWidth() * scale)));
                            anchor.setDy2(org.apache.poi.util.Units.pixelToEMU(4 + (int) (image.getHeight() * scale)));
                            anchor.setAnchorType(org.apache.poi.ss.usermodel.ClientAnchor.AnchorType.MOVE_AND_RESIZE);
                            drawing.createPicture(anchor, pictureId);
                        } else {
                            cell.setCellValue(path.substring(path.lastIndexOf('/') + 1));
                            var link = book.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.FILE);
                            link.setAddress(org.springframework.web.util.UriUtils.encodePath(path, StandardCharsets.UTF_8));
                            cell.setHyperlink(link);
                            cell.setCellStyle(linkStyle);
                        }
                    }
                }
            }
            if (column > 0) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, plan.rows().size(), 0, column - 1));
            book.write(output);
            return output.toByteArray();
        }
    }

    private boolean selected(Request request, Long relationId, ExamCollectionMemberDO member) {
        return (request.getServiceIds().isEmpty() || request.getServiceIds().contains(relationId))
                && (request.getClassIds().isEmpty() || member != null && request.getClassIds().contains(member.getClassId()))
                && (request.getFilled() == null || request.getFilled().equals(member != null && member.getLastSubmissionId() != null));
    }
    private boolean matches(Map<String,String> values, String keyword) {
        return keyword == null || keyword.isBlank() || values.values().stream().filter(Objects::nonNull).anyMatch(v -> v.contains(keyword.trim()));
    }
    private void missing(List<String> warnings, Map<String,String> values, Map<String,String> labels, Request request, List<String> present) {
        var absent = labels.keySet().stream().filter(k -> request.getFieldKeys().isEmpty() || request.getFieldKeys().contains(k))
                .filter(k -> !present.contains(k)).map(labels::get).toList();
        if (!absent.isEmpty()) warnings.add(values.getOrDefault("name", "未填写姓名") + " 缺少材料 " + String.join("、", absent));
    }
    private void add(List<Item> items, Set<String> paths, Map<String,String> directories, Set<String> usedDirectories,
                     Request request, Map<String,String> labels, Map<String,String> values, ExamCollectionMemberDO member, String person,
                     Long relationId, Long submissionId, java.time.LocalDateTime submittedAt, String submissionLabel, Long id, String source, String field, String side, String original, String type, Long size, int revision) {
        if (size == null || size < 0) throw exception(FIELD_INVALID, "材料大小缺失");
        if (size > MAX_BYTES || items.size() >= MAX_FILES) throw exception(FIELD_INVALID, "单次最多 2000 个附件、512 MB，请缩小范围");
        var naming = new HashMap<>(values);
        naming.put("idCard", values.getOrDefault("id_card", ""));
        naming.put("material", labels.get(field));
        naming.put("submittedAt", submittedAt == null ? "未记录"
                : submittedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd HH_mm_ss")));
        naming.put("side", "front".equalsIgnoreCase(side) ? "正面" : "back".equalsIgnoreCase(side) ? "反面" : "");
        long sequence = items.stream().filter(item -> Objects.equals(item.relationId(), relationId)
                && Objects.equals(item.submissionId(), submissionId) && field.equals(item.fieldKey())).count() + 1;
        naming.put("sequence", String.format("%02d", sequence));
        String originalName = Objects.requireNonNullElse(original, "");
        int dot = originalName.lastIndexOf('.'); naming.put("original", dot > 0 ? originalName.substring(0, dot) : originalName);
        String directory = "";
        if (request.getSettings().isGroupByClass()) {
            String classKey = "class:" + (member == null ? "additional" : member.getClassId());
            directory = directories.computeIfAbsent(classKey, ignored -> ExamCollectionExportNaming.unique("",
                    ExamCollectionExportNaming.safe(member == null ? "额外关联人员" : member.getClassNameSnapshot()), "/", usedDirectories));
        }
        if (request.getMode() == Mode.SUBMISSIONS || request.getSettings().getLayout() == Layout.STUDENT) {
            String key = "student:" + directory + person;
            String display = values.getOrDefault("name", "未填写姓名");
            if (submissionId != null) display += "_" + submissionLabel;
            final String parent = directory; final String title = ExamCollectionExportNaming.safe(display);
            directory = directories.computeIfAbsent(key, ignored -> ExamCollectionExportNaming.unique(parent, title, "/", usedDirectories));
        }
        if (request.getSettings().getLayout() == Layout.MATERIAL) {
            final String parent = directory;
            directory = directories.computeIfAbsent("material:" + parent + field, ignored -> ExamCollectionExportNaming.unique(parent,
                    ExamCollectionExportNaming.safe(labels.get(field)), "/", usedDirectories));
        }
        String name = ExamCollectionExportNaming.name(request.getSettings().getOverrides().getOrDefault(field, request.getSettings().getNaming()), naming);
        String path = ExamCollectionExportNaming.unique(directory, name, ExamCollectionExportNaming.extension(type), paths);
        items.add(new Item(path, relationId, submissionId, id, source, field, size, revision));
    }
    private String statusLabel(String status) {
        return switch (status) { case "ARCHIVED" -> "已归档"; case "EXCEPTION" -> "待处理异常"; case "INVALID" -> "已标记无效"; default -> "未知状态"; };
    }
    private Map<String,String> labels(ExamCollectionTaskDO task) {
        var labels = new LinkedHashMap<String,String>();
        for (var field : JsonUtils.parseArray(task.getFieldsSnapshot(), Field.class))
            if ("images".equals(field.getType())) labels.put(field.getKey(), field.getLabel());
        return labels;
    }
    private void check(Long taskId) { permissions.check(taskId, "query", getLoginUserId()); }
}
