package com.example.qltv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationRepository {

    private final DatabaseOpenHelper dbHelper;

    public NotificationRepository(Context context) {
        this.dbHelper = new DatabaseOpenHelper(context);
        this.dbHelper.initializeDatabase();
    }

    /**
     * Tạo thông báo mới cho người dùng
     */
    public long createNotification(int ma_nd, String tieu_de, String noi_dung, String loai_thong_bao, String link_lien_quan) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ma_nd", ma_nd);
        values.put("tieu_de", tieu_de);
        values.put("noi_dung", noi_dung);
        values.put("loai_thong_bao", loai_thong_bao);
        values.put("da_doc", 0);
        values.put("ngay_tao", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        if (link_lien_quan != null) {
            values.put("link_lien_quan", link_lien_quan);
        }
        return db.insert("THONG_BAO", null, values);
    }

    /**
     * Lấy danh sách thông báo của người dùng
     */
    public List<Map<String, Object>> getNotifications(int ma_nd, int limit, boolean unreadOnly) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT * FROM THONG_BAO WHERE ma_nd = ?";
        List<String> params = new ArrayList<>();
        params.add(String.valueOf(ma_nd));

        if (unreadOnly) {
            sql += " AND da_doc = 0";
        }

        sql += " ORDER BY ngay_tao DESC LIMIT ?";
        params.add(String.valueOf(limit));

        try (Cursor c = db.rawQuery(sql, params.toArray(new String[0]))) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_thong_bao", c.getInt(c.getColumnIndexOrThrow("ma_thong_bao")));
                map.put("ma_nd", c.getInt(c.getColumnIndexOrThrow("ma_nd")));
                map.put("tieu_de", c.getString(c.getColumnIndexOrThrow("tieu_de")));
                map.put("noi_dung", c.getString(c.getColumnIndexOrThrow("noi_dung")));
                map.put("loai_thong_bao", c.getString(c.getColumnIndexOrThrow("loai_thong_bao")));
                map.put("da_doc", c.getInt(c.getColumnIndexOrThrow("da_doc")));
                map.put("ngay_tao", c.getString(c.getColumnIndexOrThrow("ngay_tao")));
                map.put("link_lien_quan", c.getString(c.getColumnIndexOrThrow("link_lien_quan")));
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Đếm số lượng thông báo chưa đọc
     */
    public int getUnreadNotificationCount(int ma_nd) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM THONG_BAO WHERE ma_nd = ? AND da_doc = 0";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(ma_nd)})) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        }
        return 0;
    }

    /**
     * Đánh dấu một thông báo đã đọc
     */
    public boolean markNotificationAsRead(int ma_thong_bao) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("da_doc", 1);
        int rows = db.update("THONG_BAO", values, "ma_thong_bao = ?", new String[]{String.valueOf(ma_thong_bao)});
        return rows > 0;
    }

    /**
     * Đánh dấu toàn bộ thông báo đã đọc
     */
    public void markAllNotificationsAsRead(int ma_nd) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("da_doc", 1);
        db.update("THONG_BAO", values, "ma_nd = ?", new String[]{String.valueOf(ma_nd)});
    }

    /**
     * Xóa một thông báo
     */
    public boolean deleteNotification(int ma_thong_bao) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("THONG_BAO", "ma_thong_bao = ?", new String[]{String.valueOf(ma_thong_bao)});
        return rows > 0;
    }

    /**
     * Xóa toàn bộ thông báo của người dùng
     */
    public void deleteAllNotifications(int ma_nd) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("THONG_BAO", "ma_nd = ?", new String[]{String.valueOf(ma_nd)});
    }

    /**
     * KIỂM TRA NGẦM VÀ THÔNG BÁO SÁCH TRỄ HẠN/QUÁ HẠN (Dùng cho NotificationWorker)
     */
    public void checkAndNotifyDueBooks() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String todayStr = sdfDate.format(new Date());
        String nowStr = sdfTime.format(new Date());

        db.beginTransaction();
        try {
            // === 1. TỰ ĐỘNG HỦY CÁC YÊU CẦU MƯỢN QUÁ HẠN 3 NGÀY LẤY SÁCH ===
            String expiredReqsSql = "SELECT yc.*, s.tieu_de " +
                                    "FROM YEU_CAU_MUON yc " +
                                    "JOIN SACH s ON yc.ma_sach = s.ma_sach " +
                                    "WHERE yc.trang_thai = 'CHO_LAY_SACH' " +
                                    "AND yc.han_lay_sach IS NOT NULL " +
                                    "AND datetime(yc.han_lay_sach) < datetime(?)";

            List<Map<String, Object>> expiredReqs = new ArrayList<>();
            try (Cursor c = db.rawQuery(expiredReqsSql, new String[]{nowStr})) {
                while (c.moveToNext()) {
                    Map<String, Object> req = new HashMap<>();
                    req.put("ma_yeu_cau", c.getInt(c.getColumnIndexOrThrow("ma_yeu_cau")));
                    req.put("ma_nd_doc_gia", c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia")));
                    req.put("ma_quyen", c.isNull(c.getColumnIndexOrThrow("ma_quyen")) ? null : c.getInt(c.getColumnIndexOrThrow("ma_quyen")));
                    req.put("tieu_de", c.getString(c.getColumnIndexOrThrow("tieu_de")));
                    expiredReqs.add(req);
                }
            }

            for (Map<String, Object> req : expiredReqs) {
                int maYeuCau = (int) req.get("ma_yeu_cau");
                int maNdDocGia = (int) req.get("ma_nd_doc_gia");
                Integer maQuyen = (Integer) req.get("ma_quyen");
                String tieuDe = (String) req.get("tieu_de");

                // Hủy yêu cầu
                db.execSQL("UPDATE YEU_CAU_MUON SET trang_thai = 'DA_HUY' WHERE ma_yeu_cau = ?", new Object[]{maYeuCau});

                // Giải phóng quyển sách đặt trước
                if (maQuyen != null) {
                    db.execSQL("UPDATE QUYEN_SACH SET trang_thai = 'CO_SAN' WHERE ma_quyen = ?", new Object[]{maQuyen});
                }

                // Gửi thông báo cho Độc giả
                ContentValues notif = new ContentValues();
                notif.put("ma_nd", maNdDocGia);
                notif.put("tieu_de", "⏰ Yêu cầu mượn sách đã hết hạn lấy");
                notif.put("noi_dung", "Yêu cầu mượn sách \"" + tieuDe + "\" đã bị hủy tự động do quá hạn 3 ngày lấy sách. Bạn có thể gửi yêu cầu mới.");
                notif.put("loai_thong_bao", "HE_THONG");
                notif.put("da_doc", 0);
                notif.put("ngay_tao", nowStr);
                notif.put("link_lien_quan", "yeu_cau:" + maYeuCau);
                db.insert("THONG_BAO", null, notif);
            }

            // === 2. GỬI CẢNH BÁO SẮP HẾT HẠN VÀ QUÁ HẠN CHO ĐỌC GIẢ ===
            String activeBorrowsSql = "SELECT pm.*, s.tieu_de " +
                                      "FROM PHIEU_MUON_TRA pm " +
                                      "JOIN SACH s ON pm.ma_sach = s.ma_sach " +
                                      "WHERE pm.trang_thai_phieu = 'DANG_MUON'";

            List<Map<String, Object>> activeBorrows = new ArrayList<>();
            try (Cursor c = db.rawQuery(activeBorrowsSql, null)) {
                while (c.moveToNext()) {
                    Map<String, Object> borrow = new HashMap<>();
                    borrow.put("ma_phieu", c.getInt(c.getColumnIndexOrThrow("ma_phieu")));
                    borrow.put("ma_nd_doc_gia", c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia")));
                    borrow.put("tieu_de", c.getString(c.getColumnIndexOrThrow("tieu_de")));
                    borrow.put("ngay_hen_tra", c.getString(c.getColumnIndexOrThrow("ngay_hen_tra")));
                    activeBorrows.add(borrow);
                }
            }

            for (Map<String, Object> b : activeBorrows) {
                int maPhieu = (int) b.get("ma_phieu");
                int maNdDocGia = (int) b.get("ma_nd_doc_gia");
                String tieuDe = (String) b.get("tieu_de");
                String ngayHenTraStr = (String) b.get("ngay_hen_tra");

                try {
                    Date ngayHenTra = sdfDate.parse(ngayHenTraStr);
                    Date today = sdfDate.parse(todayStr);

                    if (ngayHenTra != null && today != null) {
                        long diff = ngayHenTra.getTime() - today.getTime();
                        long daysUntilDue = diff / (24 * 60 * 60 * 1000);

                        // Tránh gửi nhiều thông báo nhắc nhở trùng trong ngày hôm nay
                        String checkNotifSql = "SELECT COUNT(*) FROM THONG_BAO " +
                                               "WHERE ma_nd = ? AND link_lien_quan = ? " +
                                               "AND date(ngay_tao) = date(?)";
                        boolean alreadyNotified = false;
                        try (Cursor c = db.rawQuery(checkNotifSql, new String[]{String.valueOf(maNdDocGia), "phieu_muon:" + maPhieu, nowStr})) {
                            if (c.moveToFirst() && c.getInt(0) > 0) {
                                alreadyNotified = true;
                            }
                        }

                        if (alreadyNotified) {
                            continue;
                        }

                        if (daysUntilDue < 0) {
                            // Sách đã quá hạn
                            ContentValues notif = new ContentValues();
                            notif.put("ma_nd", maNdDocGia);
                            notif.put("tieu_de", "⚠️ Sách đã quá hạn trả!");
                            notif.put("noi_dung", "Sách \"" + tieuDe + "\" đã quá hạn " + Math.abs(daysUntilDue) + " ngày. Vui lòng trả ngay để tránh tăng tiền phạt.");
                            notif.put("loai_thong_bao", "QUA_HAN");
                            notif.put("da_doc", 0);
                            notif.put("ngay_tao", nowStr);
                            notif.put("link_lien_quan", "phieu_muon:" + maPhieu);
                            db.insert("THONG_BAO", null, notif);
                        } else if (daysUntilDue <= 3) {
                            // Sách sắp đến hạn (1-3 ngày nữa)
                            SimpleDateFormat sdfVn = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                            String formattedHenTra = sdfVn.format(ngayHenTra);
                            
                            ContentValues notif = new ContentValues();
                            notif.put("ma_nd", maNdDocGia);
                            notif.put("tieu_de", "📅 Sách sắp đến hạn trả");
                            notif.put("noi_dung", "Sách \"" + tieuDe + "\" sẽ hết hạn mượn sau " + daysUntilDue + " ngày (" + formattedHenTra + "). Hãy trả sách đúng hạn!");
                            notif.put("loai_thong_bao", "SAP_HET_HAN");
                            notif.put("da_doc", 0);
                            notif.put("ngay_tao", nowStr);
                            notif.put("link_lien_quan", "phieu_muon:" + maPhieu);
                            db.insert("THONG_BAO", null, notif);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
