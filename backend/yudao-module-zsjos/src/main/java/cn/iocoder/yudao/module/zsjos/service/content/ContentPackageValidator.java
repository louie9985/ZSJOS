package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_PACKAGE_INCOMPLETE;

/** Validates the immutable content package required before content review. */
public final class ContentPackageValidator {

    private ContentPackageValidator() {
    }

    public static void validate(ContentDO content, ContentVersionDO version) {
        List<String> missing = new ArrayList<>();
        if (blank(firstText(version.getTitleSnapshot(), version.getTopicSnapshot(),
                content.getTitle(), content.getTopic()))) {
            missing.add("标题或选题");
        }
        if (blank(version.getScriptText())) missing.add("脚本或正文");
        if (!hasJsonContent(version.getCoverSnapshotJson())) missing.add("封面");
        if (blank(version.getDeliverableUrl()) && !hasJsonContent(version.getDeliverableSnapshotJson())) {
            missing.add("成品预览");
        }
        if (!validHttps(version.getLeadResourceUrl())) missing.add("HTTPS 引流资料链接");
        if (version.getPlannedPublishAt() == null) missing.add("预计发布时间");
        if (!missing.isEmpty()) {
            throw exception(CONTENT_REVIEW_PACKAGE_INCOMPLETE,
                    content.getContentNo() + " 缺少" + String.join("、", missing));
        }
    }

    private static boolean hasJsonContent(String json) {
        if (blank(json)) return false;
        try {
            var node = JsonUtils.parseTree(json);
            return node != null && (!node.isArray() || !node.isEmpty()) && (!node.isObject() || !node.isEmpty());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean validHttps(String value) {
        if (blank(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
