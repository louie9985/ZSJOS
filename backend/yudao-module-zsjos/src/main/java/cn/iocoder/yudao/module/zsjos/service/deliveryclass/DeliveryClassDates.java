package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Class dates remain frozen independently of later calendar edits. */
record DeliveryClassDates(LocalDate start, LocalDate end) {
    private static final Pattern DATES = Pattern.compile("(?:^| · )(\\d{4}-\\d{2}-\\d{2})(?:~(\\d{4}-\\d{2}-\\d{2}))?$");

    String snapshot() {
        return start.equals(end) ? start.toString() : start + "~" + end;
    }

    static DeliveryClassDates fromSnapshot(String snapshot) {
        if (snapshot == null) return null;
        var match = DATES.matcher(snapshot);
        if (!match.find()) return null;
        try {
            LocalDate start = LocalDate.parse(match.group(1));
            LocalDate end = match.group(2) == null ? start : LocalDate.parse(match.group(2));
            return end.isBefore(start) ? null : new DeliveryClassDates(start, end);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
