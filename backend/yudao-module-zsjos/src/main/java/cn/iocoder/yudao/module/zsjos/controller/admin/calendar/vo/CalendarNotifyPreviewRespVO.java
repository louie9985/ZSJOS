package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

public record CalendarNotifyPreviewRespVO(int calendarVersion, String title, String time, String remark,
                                          int recipientCount, long notifiedCount, int newRecipientCount,
                                          String previewToken, String contentHash, Integer expectedVersion,
                                          String eventType, int invalidRecipientCount) {
    public CalendarNotifyPreviewRespVO(int calendarVersion, String title, String time, String remark,
                                       int recipientCount, long notifiedCount, int newRecipientCount,
                                       String previewToken, String contentHash) {
        this(calendarVersion, title, time, remark, recipientCount, notifiedCount, newRecipientCount,
                previewToken, contentHash, calendarVersion, "MANUAL", 0);
    }
}
