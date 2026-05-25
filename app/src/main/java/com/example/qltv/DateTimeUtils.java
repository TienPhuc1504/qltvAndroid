package com.example.qltv;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {

    private static final SimpleDateFormat SQL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat SQL_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DISPLAY_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat DISPLAY_DATETIME_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    /**
     * Chuyển đổi chuỗi ngày từ SQLite (yyyy-MM-dd hoặc yyyy-MM-dd HH:mm:ss) sang định dạng hiển thị dd/MM/yyyy.
     * Nếu chuỗi gốc chứa giờ phút giây, sẽ hiển thị dd/MM/yyyy HH:mm:ss.
     * Nếu không parse được hoặc rỗng, trả về chuỗi gốc.
     */
    public static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return "";
        }
        dateStr = dateStr.trim();
        try {
            if (dateStr.length() > 10) {
                Date date = SQL_DATETIME_FORMAT.parse(dateStr);
                if (date != null) {
                    return DISPLAY_DATETIME_FORMAT.format(date);
                }
            } else {
                Date date = SQL_DATE_FORMAT.parse(dateStr);
                if (date != null) {
                    return DISPLAY_DATE_FORMAT.format(date);
                }
            }
        } catch (ParseException e) {
            // Thử các giải pháp phục hồi
            try {
                if (dateStr.length() > 10) {
                    Date date = SQL_DATE_FORMAT.parse(dateStr.substring(0, 10));
                    if (date != null) {
                        return DISPLAY_DATE_FORMAT.format(date);
                    }
                }
            } catch (Exception ignored) {}
        }
        return dateStr;
    }
}
