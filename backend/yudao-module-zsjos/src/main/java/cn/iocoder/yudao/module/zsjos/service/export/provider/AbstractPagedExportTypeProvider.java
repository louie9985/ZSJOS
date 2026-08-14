package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.idev.excel.FastExcelFactory;
import cn.idev.excel.converters.longconverter.LongStringConverter;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.service.export.ExportTypeProvider;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

abstract class AbstractPagedExportTypeProvider<Q extends PageParam, R> implements ExportTypeProvider {

    private static final int PAGE_SIZE = 200;
    private static final long MAX_ROWS = 100_000L;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private Validator validator;

    @Override
    public final void validateFilter(String filterJson) {
        parseAndValidate(filterJson);
    }

    @Override
    public final ExportResult generate(ExportTaskDO task) {
        Q request = parseAndValidate(task.getFilterJson());
        request.setPageNo(1);
        request.setPageSize(PAGE_SIZE);

        List<List<Object>> rows = new ArrayList<>();
        String exportTime = DISPLAY_TIME.format(LocalDateTime.now());
        long total = 0L;
        while (true) {
            PageResult<R> page = getPage(request, task.getCreatorUserId());
            total = page.getTotal();
            if (total > MAX_ROWS) {
                return new ExportResult(new byte[0], fileName(task), total);
            }
            for (R item : page.getList()) {
                List<Object> row = new ArrayList<>(columns().size() + 3);
                row.add(task.getTaskNo());
                row.add(task.getCreatorNameSnapshot());
                row.add(exportTime);
                row.addAll(toRow(item));
                rows.add(row);
            }
            if (rows.size() >= total || page.getList().isEmpty()) {
                break;
            }
            request.setPageNo(request.getPageNo() + 1);
        }

        List<List<String>> headers = new ArrayList<>(columns().size() + 3);
        headers.add(List.of("任务编号"));
        headers.add(List.of("导出人"));
        headers.add(List.of("导出时间"));
        columns().forEach(column -> headers.add(List.of(column)));
        int rowCount = rows.size();
        if (rows.isEmpty()) {
            List<Object> metadataRow = new ArrayList<>(columns().size() + 3);
            metadataRow.add(task.getTaskNo());
            metadataRow.add(task.getCreatorNameSnapshot());
            metadataRow.add(exportTime);
            columns().forEach(column -> metadataRow.add(""));
            rows.add(metadataRow);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FastExcelFactory.write(output).head(headers).registerConverter(new LongStringConverter())
                .sheet(sheetName()).doWrite(rows);
        return new ExportResult(output.toByteArray(), fileName(task), rowCount);
    }

    protected abstract Class<Q> requestType();

    protected abstract PageResult<R> getPage(Q request, Long creatorUserId);

    protected abstract List<String> columns();

    protected abstract List<Object> toRow(R item);

    protected abstract String sheetName();

    private Q parseAndValidate(String filterJson) {
        Q request;
        try {
            request = JsonUtils.parseObject(filterJson, requestType());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("导出筛选条件无法解析", exception);
        }
        if (request == null) {
            throw new IllegalArgumentException("导出筛选条件不能为空");
        }
        Set<ConstraintViolation<Q>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return request;
    }

    private String fileName(ExportTaskDO task) {
        return getType() + "-" + task.getTaskNo() + "-" + FILE_TIME.format(LocalDateTime.now()) + ".xlsx";
    }
}
