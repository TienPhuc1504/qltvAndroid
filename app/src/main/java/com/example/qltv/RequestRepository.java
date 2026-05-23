package com.example.qltv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RequestRepository {

    private final DatabaseOpenHelper dbHelper;
    private static final int MAX_ACTIVE_BORROWS = 5;

    public RequestRepository(Context context) {
        this.dbHelper = new DatabaseOpenHelper(context);
        this.dbHelper.initializeDatabase();
    }

    /**
     * Đọc giả gửi yêu cầu mượn sách mới
     */
    public long createBorrowRequest(int ma_nd_doc_gia, int ma_sach, int so_ngay_muon_de_xuat, String ghi_chu) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (so_ngay_muon_de_xuat <= 0 || so_ngay_muon_de_xuat > 30) {
            throw new Exception("Số ngày mượn phải từ 1-30 ngày!");
        }

        // 1. Kiểm tra số sách đang mượn
        int activeBorrows = 0;
        String countBorrowSql = "SELECT COUNT(*) FROM PHIEU_MUON_TRA WHERE ma_nd_doc_gia = ? AND trang_thai_phieu = 'DANG_MUON'";
        try (Cursor c = db.rawQuery(countBorrowSql, new String[]{String.valueOf(ma_nd_doc_gia)})) {
            if (c.moveToFirst()) activeBorrows = c.getInt(0);
        }

        // 2. Kiểm tra số yêu cầu đang chờ xử lý
        int pendingRequests = 0;
        String countReqSql = "SELECT COUNT(*) FROM YEU_CAU_MUON WHERE ma_nd_doc_gia = ? AND trang_thai IN ('CHO_DUYET', 'CHO_LAY_SACH')";
        try (Cursor c = db.rawQuery(countReqSql, new String[]{String.valueOf(ma_nd_doc_gia)})) {
            if (c.moveToFirst()) pendingRequests = c.getInt(0);
        }

        if (activeBorrows + pendingRequests >= MAX_ACTIVE_BORROWS) {
            throw new Exception("Bạn chỉ có thể mượn tối đa " + MAX_ACTIVE_BORROWS + " sách cùng lúc!\n" +
                    "Hiện tại: " + activeBorrows + " đang mượn, " + pendingRequests + " đang chờ xử lý.");
        }

        // 3. Kiểm tra độc giả đang mượn sách này
        String checkBorrow = "SELECT COUNT(*) FROM PHIEU_MUON_TRA WHERE ma_nd_doc_gia = ? AND ma_sach = ? AND trang_thai_phieu = 'DANG_MUON'";
        try (Cursor c = db.rawQuery(checkBorrow, new String[]{String.valueOf(ma_nd_doc_gia), String.valueOf(ma_sach)})) {
            if (c.moveToFirst() && c.getInt(0) > 0) {
                throw new Exception("Bạn đang mượn sách này! Vui lòng trả sách trước khi mượn lại.");
            }
        }

        // 4. Kiểm tra đã gửi yêu cầu cho cuốn này chưa
        String checkReq = "SELECT COUNT(*) FROM YEU_CAU_MUON WHERE ma_nd_doc_gia = ? AND ma_sach = ? AND trang_thai IN ('CHO_DUYET', 'CHO_LAY_SACH')";
        try (Cursor c = db.rawQuery(checkReq, new String[]{String.valueOf(ma_nd_doc_gia), String.valueOf(ma_sach)})) {
            if (c.moveToFirst() && c.getInt(0) > 0) {
                throw new Exception("Bạn đã có yêu cầu mượn sách này đang chờ xử lý!");
            }
        }

        // 5. Kiểm tra sách khả dụng
        String checkBook = "SELECT tieu_de, loai_sach, trang_thai_sach FROM SACH WHERE ma_sach = ?";
        String tieu_de = "";
        String loai_sach = "";
        String trang_thai_sach = "";
        try (Cursor c = db.rawQuery(checkBook, new String[]{String.valueOf(ma_sach)})) {
            if (c.moveToFirst()) {
                tieu_de = c.getString(c.getColumnIndexOrThrow("tieu_de"));
                loai_sach = c.getString(c.getColumnIndexOrThrow("loai_sach"));
                trang_thai_sach = c.getString(c.getColumnIndexOrThrow("trang_thai_sach"));
            } else {
                throw new Exception("Không tìm thấy sách!");
            }
        }

        if ("HONG".equals(trang_thai_sach) || "MAT".equals(trang_thai_sach)) {
            throw new Exception("Sách không khả dụng (hỏng hoặc mất)!");
        }

        Integer ma_quyen = null;
        if ("SACH_GIAY".equals(loai_sach)) {
            // Lấy quyển sách có sẵn
            String findCopy = "SELECT ma_quyen FROM QUYEN_SACH WHERE ma_sach = ? AND trang_thai = 'CO_SAN' ORDER BY ma_quyen LIMIT 1";
            try (Cursor c = db.rawQuery(findCopy, new String[]{String.valueOf(ma_sach)})) {
                if (c.moveToFirst()) {
                    ma_quyen = c.getInt(0);
                } else {
                    throw new Exception("Sách đã hết! Vui lòng chọn sách khác.");
                }
            }
        }

        db.beginTransaction();
        try {
            String ngay_yeu_cau = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("ma_nd_doc_gia", ma_nd_doc_gia);
            values.put("ma_sach", ma_sach);
            values.put("ngay_yeu_cau", ngay_yeu_cau);
            values.put("so_ngay_muon_de_xuat", so_ngay_muon_de_xuat);
            values.put("ghi_chu", ghi_chu);
            values.put("trang_thai", "CHO_DUYET");
            if (ma_quyen != null) {
                values.put("ma_quyen", ma_quyen);
            }
            long ma_yeu_cau = db.insertOrThrow("YEU_CAU_MUON", null, values);

            // Đặt trước quyển sách bằng cách đổi sang KHONG_CO_SAN
            if (ma_quyen != null) {
                db.execSQL("UPDATE QUYEN_SACH SET trang_thai = 'KHONG_CO_SAN' WHERE ma_quyen = ?", new Object[]{ma_quyen});
            }

            // Gửi thông báo đến toàn bộ nhân viên/admin
            String getStaff = "SELECT ma_nd FROM NGUOI_DUNG WHERE loai_nguoi_dung IN ('NHAN_VIEN', 'ADMIN')";
            String ho_ten_doc_gia = "";
            try (Cursor c = db.rawQuery("SELECT ho_ten FROM NGUOI_DUNG WHERE ma_nd = ?", new String[]{String.valueOf(ma_nd_doc_gia)})) {
                if (c.moveToFirst()) ho_ten_doc_gia = c.getString(0);
            }

            String ngay_tao = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            try (Cursor c = db.rawQuery(getStaff, null)) {
                while (c.moveToNext()) {
                    int ma_nd_staff = c.getInt(0);
                    ContentValues notif = new ContentValues();
                    notif.put("ma_nd", ma_nd_staff);
                    notif.put("tieu_de", "📬 Yêu cầu mượn sách mới");
                    notif.put("noi_dung", "Độc giả " + ho_ten_doc_gia + " yêu cầu mượn sách \"" + tieu_de + "\" trong " + so_ngay_muon_de_xuat + " ngày.");
                    notif.put("loai_thong_bao", "YEU_CAU_MOI");
                    notif.put("da_doc", 0);
                    notif.put("ngay_tao", ngay_tao);
                    notif.put("link_lien_quan", "yeu_cau:" + ma_yeu_cau);
                    db.insert("THONG_BAO", null, notif);
                }
            }

            db.setTransactionSuccessful();
            return ma_yeu_cau;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Lấy toàn bộ yêu cầu mượn sách (Nhân viên quản lý)
     */
    public List<Map<String, Object>> getAllBorrowRequests(String status, String searchTerm) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT yc.*, " +
                     "nd.ho_ten as ten_doc_gia, nd.so_dt, nd.email, dg.ma_doc_gia, " +
                     "s.tieu_de, s.tac_gia, s.loai_sach, tl.ten_the_loai, " +
                     "nd2.ho_ten as nguoi_xu_ly " +
                     "FROM YEU_CAU_MUON yc " +
                     "JOIN NGUOI_DUNG nd ON yc.ma_nd_doc_gia = nd.ma_nd " +
                     "JOIN DOC_GIA dg ON nd.ma_nd = dg.ma_nd " +
                     "JOIN SACH s ON yc.ma_sach = s.ma_sach " +
                     "LEFT JOIN THE_LOAI_SACH tl ON s.ma_the_loai = tl.ma_the_loai " +
                     "LEFT JOIN NGUOI_DUNG nd2 ON yc.ma_nd_xu_ly = nd2.ma_nd " +
                     "WHERE 1=1";

        List<String> paramsList = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            sql += " AND yc.trang_thai = ?";
            paramsList.add(status);
        }

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql += " AND (nd.ho_ten LIKE ? OR s.tieu_de LIKE ? OR dg.ma_doc_gia LIKE ?)";
            String searchPattern = "%" + searchTerm.trim() + "%";
            paramsList.add(searchPattern);
            paramsList.add(searchPattern);
            paramsList.add(searchPattern);
        }

        sql += " ORDER BY yc.ngay_yeu_cau DESC, yc.ma_yeu_cau DESC";

        String[] params = paramsList.isEmpty() ? null : paramsList.toArray(new String[0]);

        try (Cursor cursor = db.rawQuery(sql, params)) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_yeu_cau", cursor.getInt(cursor.getColumnIndexOrThrow("ma_yeu_cau")));
                map.put("ma_nd_doc_gia", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd_doc_gia")));
                map.put("ten_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ten_doc_gia")));
                map.put("so_dt", cursor.getString(cursor.getColumnIndexOrThrow("so_dt")));
                map.put("email", cursor.getString(cursor.getColumnIndexOrThrow("email")));
                map.put("ma_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ma_doc_gia")));
                map.put("ma_sach", cursor.getInt(cursor.getColumnIndexOrThrow("ma_sach")));
                map.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                map.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                map.put("ten_the_loai", cursor.getString(cursor.getColumnIndexOrThrow("ten_the_loai")));
                map.put("ngay_yeu_cau", cursor.getString(cursor.getColumnIndexOrThrow("ngay_yeu_cau")));
                map.put("so_ngay_muon_de_xuat", cursor.getInt(cursor.getColumnIndexOrThrow("so_ngay_muon_de_xuat")));
                
                int offIdx = cursor.getColumnIndexOrThrow("so_ngay_muon_chinh_thuc");
                if (!cursor.isNull(offIdx)) {
                    map.put("so_ngay_muon_chinh_thuc", cursor.getInt(offIdx));
                }
                
                map.put("ghi_chu", cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu")));
                map.put("trang_thai", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai")));
                map.put("ngay_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("ngay_xu_ly")));
                map.put("ly_do_tu_choi", cursor.getString(cursor.getColumnIndexOrThrow("ly_do_tu_choi")));
                map.put("nguoi_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("nguoi_xu_ly")));
                
                int mqIdx = cursor.getColumnIndexOrThrow("ma_quyen");
                if (!cursor.isNull(mqIdx)) {
                    map.put("ma_quyen", cursor.getInt(mqIdx));
                }

                int hanIdx = cursor.getColumnIndexOrThrow("han_lay_sach");
                if (!cursor.isNull(hanIdx)) {
                    map.put("han_lay_sach", cursor.getString(hanIdx));
                }
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Lấy các yêu cầu mượn của một độc giả
     */
    public List<Map<String, Object>> getReaderBorrowRequests(int ma_nd_doc_gia) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT yc.*, s.tieu_de, s.tac_gia, s.loai_sach, tl.ten_the_loai, nd.ho_ten as nguoi_xu_ly " +
                     "FROM YEU_CAU_MUON yc " +
                     "JOIN SACH s ON yc.ma_sach = s.ma_sach " +
                     "LEFT JOIN THE_LOAI_SACH tl ON s.ma_the_loai = tl.ma_the_loai " +
                     "LEFT JOIN NGUOI_DUNG nd ON yc.ma_nd_xu_ly = nd.ma_nd " +
                     "WHERE yc.ma_nd_doc_gia = ? " +
                     "ORDER BY yc.ngay_yeu_cau DESC";

        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_nd_doc_gia)})) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_yeu_cau", cursor.getInt(cursor.getColumnIndexOrThrow("ma_yeu_cau")));
                map.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                map.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                map.put("ten_the_loai", cursor.getString(cursor.getColumnIndexOrThrow("ten_the_loai")));
                map.put("ngay_yeu_cau", cursor.getString(cursor.getColumnIndexOrThrow("ngay_yeu_cau")));
                map.put("so_ngay_muon_de_xuat", cursor.getInt(cursor.getColumnIndexOrThrow("so_ngay_muon_de_xuat")));
                
                int offIdx = cursor.getColumnIndexOrThrow("so_ngay_muon_chinh_thuc");
                if (!cursor.isNull(offIdx)) {
                    map.put("so_ngay_muon_chinh_thuc", cursor.getInt(offIdx));
                }
                
                map.put("ghi_chu", cursor.getString(cursor.getColumnIndexOrThrow("ghi_chu")));
                map.put("trang_thai", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai")));
                map.put("ngay_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("ngay_xu_ly")));
                map.put("ly_do_tu_choi", cursor.getString(cursor.getColumnIndexOrThrow("ly_do_tu_choi")));
                map.put("nguoi_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("nguoi_xu_ly")));
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Phê duyệt yêu cầu mượn (Nhân viên làm) -> Chuyển sang chờ lấy sách (Hạn 3 ngày)
     */
    public boolean approveBorrowRequest(int ma_yeu_cau, int ma_nd_xu_ly, int so_ngay_muon) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (so_ngay_muon <= 0 || so_ngay_muon > 30) {
            throw new Exception("Số ngày mượn phải từ 1-30 ngày!");
        }

        // 1. Lấy thông tin yêu cầu
        String getReq = "SELECT * FROM YEU_CAU_MUON WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        int ma_sach = 0;
        String trang_thai = "";
        Integer ma_quyen = null;
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                ma_sach = c.getInt(c.getColumnIndexOrThrow("ma_sach"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
                int mqIdx = c.getColumnIndexOrThrow("ma_quyen");
                if (!c.isNull(mqIdx)) ma_quyen = c.getInt(mqIdx);
            } else {
                throw new Exception("Không tìm thấy yêu cầu!");
            }
        }

        if (!"CHO_DUYET".equals(trang_thai)) {
            throw new Exception("Yêu cầu này đã được xử lý!");
        }

        db.beginTransaction();
        try {
            String ngay_xu_ly = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            
            // Hạn lấy sách = 3 ngày sau duyệt
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 3);
            String han_lay_sach = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.getTime());

            // Cập nhật yêu cầu
            ContentValues values = new ContentValues();
            values.put("trang_thai", "CHO_LAY_SACH");
            values.put("ma_nd_xu_ly", ma_nd_xu_ly);
            values.put("ngay_xu_ly", ngay_xu_ly);
            values.put("so_ngay_muon_chinh_thuc", so_ngay_muon);
            values.put("han_lay_sach", han_lay_sach);
            db.update("YEU_CAU_MUON", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Xóa thông báo mượn mới của nhân viên liên quan yêu cầu này
            db.delete("THONG_BAO", "link_lien_quan = ? AND loai_thong_bao = 'YEU_CAU_MOI'", new String[]{"yeu_cau:" + ma_yeu_cau});

            // Lấy tên sách
            String tieu_de = "";
            try (Cursor c = db.rawQuery("SELECT tieu_de FROM SACH WHERE ma_sach = ?", new String[]{String.valueOf(ma_sach)})) {
                if (c.moveToFirst()) tieu_de = c.getString(0);
            }

            // Gửi thông báo cho Độc giả
            String ngay_tao = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String formattedDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime());
            
            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "✅ Yêu cầu mượn sách đã được duyệt!");
            notif.put("noi_dung", "Yêu cầu mượn sách \"" + tieu_de + "\" đã được duyệt với thời hạn " + so_ngay_muon + " ngày.\n⏰ Vui lòng đến thư viện lấy sách trước ngày " + formattedDate + ".");
            notif.put("loai_thong_bao", "YEU_CAU_DUYET");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_tao);
            notif.put("link_lien_quan", "yeu_cau:" + ma_yeu_cau);
            db.insert("THONG_BAO", null, notif);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Từ chối yêu cầu mượn (Nhân viên làm) -> Trả lại quyển sách về CO_SAN
     */
    public boolean rejectBorrowRequest(int ma_yeu_cau, int ma_nd_xu_ly, String ly_do) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String getReq = "SELECT * FROM YEU_CAU_MUON WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        int ma_sach = 0;
        String trang_thai = "";
        Integer ma_quyen = null;
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                ma_sach = c.getInt(c.getColumnIndexOrThrow("ma_sach"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
                int mqIdx = c.getColumnIndexOrThrow("ma_quyen");
                if (!c.isNull(mqIdx)) ma_quyen = c.getInt(mqIdx);
            } else {
                throw new Exception("Không tìm thấy yêu cầu!");
            }
        }

        if (!"CHO_DUYET".equals(trang_thai)) {
            throw new Exception("Yêu cầu này đã được xử lý!");
        }

        db.beginTransaction();
        try {
            String ngay_xu_ly = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("trang_thai", "TU_CHOI");
            values.put("ma_nd_xu_ly", ma_nd_xu_ly);
            values.put("ngay_xu_ly", ngay_xu_ly);
            values.put("ly_do_tu_choi", ly_do);
            db.update("YEU_CAU_MUON", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Trả lại quyển sách vật lý về CO_SAN
            if (ma_quyen != null) {
                db.execSQL("UPDATE QUYEN_SACH SET trang_thai = 'CO_SAN' WHERE ma_quyen = ?", new Object[]{ma_quyen});
            }

            // Xóa thông báo nhân viên
            db.delete("THONG_BAO", "link_lien_quan = ? AND loai_thong_bao = 'YEU_CAU_MOI'", new String[]{"yeu_cau:" + ma_yeu_cau});

            // Lấy tên sách
            String tieu_de = "";
            try (Cursor c = db.rawQuery("SELECT tieu_de FROM SACH WHERE ma_sach = ?", new String[]{String.valueOf(ma_sach)})) {
                if (c.moveToFirst()) tieu_de = c.getString(0);
            }

            // Gửi thông báo cho Đọc giả
            String ngay_tao = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String text = "Yêu cầu mượn sách \"" + tieu_de + "\" của bạn đã bị từ chối.";
            if (ly_do != null && !ly_do.isEmpty()) {
                text += " Lý do: " + ly_do;
            }

            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "❌ Yêu cầu mượn sách bị từ chối");
            notif.put("noi_dung", text);
            notif.put("loai_thong_bao", "YEU_CAU_TU_CHOI");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_tao);
            notif.put("link_lien_quan", "yeu_cau:" + ma_yeu_cau);
            db.insert("THONG_BAO", null, notif);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Hủy yêu cầu mượn (Độc giả tự hủy khi đang Chờ duyệt hoặc Chờ lấy)
     */
    public boolean cancelBorrowRequest(int ma_yeu_cau, int ma_nd_doc_gia) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String getReq = "SELECT * FROM YEU_CAU_MUON WHERE ma_yeu_cau = ?";
        int doc_gia_id = 0;
        String trang_thai = "";
        Integer ma_quyen = null;
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                doc_gia_id = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
                int mqIdx = c.getColumnIndexOrThrow("ma_quyen");
                if (!c.isNull(mqIdx)) ma_quyen = c.getInt(mqIdx);
            } else {
                throw new Exception("Không tìm thấy yêu cầu!");
            }
        }

        if (doc_gia_id != ma_nd_doc_gia) {
            throw new Exception("Bạn không có quyền hủy yêu cầu này!");
        }

        if (!"CHO_DUYET".equals(trang_thai) && !"CHO_LAY_SACH".equals(trang_thai)) {
            throw new Exception("Chỉ có thể hủy yêu cầu đang chờ duyệt hoặc chờ lấy sách!");
        }

        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("trang_thai", "DA_HUY");
            db.update("YEU_CAU_MUON", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Trả quyển sách về trạng thái CO_SAN
            if (ma_quyen != null) {
                db.execSQL("UPDATE QUYEN_SACH SET trang_thai = 'CO_SAN' WHERE ma_quyen = ?", new Object[]{ma_quyen});
            }

            // Xóa thông báo liên quan
            db.delete("THONG_BAO", "link_lien_quan = ?", new String[]{"yeu_cau:" + ma_yeu_cau});

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Xác nhận đọc giả đã đến thư viện lấy sách -> chuyển yêu cầu mượn sang DA_LAY và tạo PHIEU_MUON_TRA chính thức
     */
    public long confirmBookPickup(int ma_yeu_cau, int ma_nd_nhan_vien) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 1. Lấy thông tin yêu cầu
        String getReq = "SELECT * FROM YEU_CAU_MUON WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        int ma_sach = 0;
        String trang_thai = "";
        int so_ngay_muon = 14;
        Integer ma_quyen = null;
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                ma_sach = c.getInt(c.getColumnIndexOrThrow("ma_sach"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
                
                int offIdx = c.getColumnIndexOrThrow("so_ngay_muon_chinh_thuc");
                if (!c.isNull(offIdx)) so_ngay_muon = c.getInt(offIdx);
                else so_ngay_muon = c.getInt(c.getColumnIndexOrThrow("so_ngay_muon_de_xuat"));
                
                int mqIdx = c.getColumnIndexOrThrow("ma_quyen");
                if (!c.isNull(mqIdx)) ma_quyen = c.getInt(mqIdx);
            } else {
                throw new Exception("Không tìm thấy yêu cầu!");
            }
        }

        if (!"CHO_LAY_SACH".equals(trang_thai)) {
            throw new Exception("Yêu cầu này chưa được duyệt hoặc đã lấy sách!");
        }

        db.beginTransaction();
        try {
            // Cập nhật yêu cầu sang DA_LAY
            ContentValues reqVals = new ContentValues();
            reqVals.put("trang_thai", "DA_LAY");
            db.update("YEU_CAU_MUON", reqVals, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Xóa thông báo chờ lấy sách
            db.delete("THONG_BAO", "link_lien_quan = ? AND loai_thong_bao IN ('YEU_CAU_DUYET', 'CHO_LAY_SACH')", new String[]{"yeu_cau:" + ma_yeu_cau});

            // Lấy tên sách
            String tieu_de = "";
            try (Cursor c = db.rawQuery("SELECT tieu_de FROM SACH WHERE ma_sach = ?", new String[]{String.valueOf(ma_sach)})) {
                if (c.moveToFirst()) tieu_de = c.getString(0);
            }

            // Tạo thông báo thành công cho độc giả
            String ngay_tao = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            Calendar cal = Calendar.getInstance();
            String ngay_muon = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, so_ngay_muon);
            String ngay_hen_tra = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
            String formattedDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.getTime());

            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "📚 Bạn đã lấy sách thành công!");
            notif.put("noi_dung", "Bạn đã mượn sách \"" + tieu_de + "\". Hạn trả: " + formattedDate + ". Vui lòng trả sách đúng hạn!");
            notif.put("loai_thong_bao", "HE_THONG");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_tao);
            notif.put("link_lien_quan", "phieu_muon:new");
            db.insert("THONG_BAO", null, notif);

            // Nhân viên chưa tồn tại trong NHAN_VIEN? Auto-insert
            db.execSQL("INSERT OR IGNORE INTO NHAN_VIEN (ma_nd, ma_nhan_vien) VALUES (?, ?)",
                    new Object[]{ma_nd_nhan_vien, String.format(Locale.getDefault(), "NV%04d", ma_nd_nhan_vien)});

            // Tạo phiếu mượn chính thức
            ContentValues borrowVals = new ContentValues();
            borrowVals.put("ma_nd_doc_gia", ma_nd_doc_gia);
            borrowVals.put("ma_nd_nhan_vien", ma_nd_nhan_vien);
            borrowVals.put("ma_sach", ma_sach);
            if (ma_quyen != null) {
                borrowVals.put("ma_quyen", ma_quyen);
            }
            borrowVals.put("ngay_muon", ngay_muon);
            borrowVals.put("ngay_hen_tra", ngay_hen_tra);
            borrowVals.put("trang_thai_phieu", "DANG_MUON");
            borrowVals.put("tien_phat", 0.0);
            long ma_phieu = db.insertOrThrow("PHIEU_MUON_TRA", null, borrowVals);

            // Cập nhật bản sao quyển sách sang DANG_MUON
            if (ma_quyen != null) {
                db.execSQL("UPDATE QUYEN_SACH SET trang_thai = 'DANG_MUON' WHERE ma_quyen = ?", new Object[]{ma_quyen});
            }

            db.setTransactionSuccessful();
            return ma_phieu;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Đọc giả gửi yêu cầu cấp/in thẻ độc giả
     */
    public long createCardRequest(int ma_nd_doc_gia) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 1. Kiểm tra yêu cầu in thẻ đang chờ duyệt hoặc xử lý
        String checkSql = "SELECT COUNT(*) FROM YEU_CAU_THE WHERE ma_nd_doc_gia = ? AND trang_thai IN ('CHO_DUYET', 'DANG_XU_LY')";
        try (Cursor c = db.rawQuery(checkSql, new String[]{String.valueOf(ma_nd_doc_gia)})) {
            if (c.moveToFirst() && c.getInt(0) > 0) {
                throw new Exception("Bạn đã có yêu cầu in thẻ đang chờ xử lý!");
            }
        }

        db.beginTransaction();
        try {
            String ngay_yeu_cau = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("ma_nd_doc_gia", ma_nd_doc_gia);
            values.put("ngay_yeu_cau", ngay_yeu_cau);
            values.put("trang_thai", "CHO_DUYET");
            values.put("da_nhan", 0);
            long ma_yeu_cau = db.insertOrThrow("YEU_CAU_THE", null, values);

            // Lấy tên đọc giả
            String ho_ten = "";
            try (Cursor c = db.rawQuery("SELECT ho_ten FROM NGUOI_DUNG WHERE ma_nd = ?", new String[]{String.valueOf(ma_nd_doc_gia)})) {
                if (c.moveToFirst()) ho_ten = c.getString(0);
            }

            // Gửi thông báo đến toàn bộ nhân viên/admin
            String getStaff = "SELECT ma_nd FROM NGUOI_DUNG WHERE loai_nguoi_dung IN ('NHAN_VIEN', 'ADMIN')";
            try (Cursor c = db.rawQuery(getStaff, null)) {
                while (c.moveToNext()) {
                    int ma_nd_staff = c.getInt(0);
                    ContentValues notif = new ContentValues();
                    notif.put("ma_nd", ma_nd_staff);
                    notif.put("tieu_de", "📬 Yêu cầu in thẻ mới");
                    notif.put("noi_dung", "Độc giả " + ho_ten + " yêu cầu in/cấp lại thẻ đọc giả.");
                    notif.put("loai_thong_bao", "YEU_CAU_MOI");
                    notif.put("da_doc", 0);
                    notif.put("ngay_tao", ngay_yeu_cau);
                    notif.put("link_lien_quan", "yeu_cau_the:" + ma_yeu_cau);
                    db.insert("THONG_BAO", null, notif);
                }
            }

            db.setTransactionSuccessful();
            return ma_yeu_cau;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Lấy danh sách toàn bộ yêu cầu in thẻ (Cho nhân viên/admin)
     */
    public List<Map<String, Object>> getAllCardRequests(String status, String searchTerm) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT yt.*, nd.ho_ten as ten_doc_gia, dg.ma_doc_gia, nd2.ho_ten as nguoi_xu_ly " +
                     "FROM YEU_CAU_THE yt " +
                     "JOIN NGUOI_DUNG nd ON yt.ma_nd_doc_gia = nd.ma_nd " +
                     "LEFT JOIN DOC_GIA dg ON nd.ma_nd = dg.ma_nd " +
                     "LEFT JOIN NGUOI_DUNG nd2 ON yt.ma_nd_xu_ly = nd2.ma_nd " +
                     "WHERE 1=1";

        List<String> paramsList = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            sql += " AND yt.trang_thai = ?";
            paramsList.add(status);
        }

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql += " AND (nd.ho_ten LIKE ? OR dg.ma_doc_gia LIKE ?)";
            String searchPattern = "%" + searchTerm.trim() + "%";
            paramsList.add(searchPattern);
            paramsList.add(searchPattern);
        }

        sql += " ORDER BY yt.ngay_yeu_cau DESC, yt.ma_yeu_cau DESC";

        String[] params = paramsList.isEmpty() ? null : paramsList.toArray(new String[0]);

        try (Cursor cursor = db.rawQuery(sql, params)) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_yeu_cau", cursor.getInt(cursor.getColumnIndexOrThrow("ma_yeu_cau")));
                map.put("ma_nd_doc_gia", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd_doc_gia")));
                map.put("ten_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ten_doc_gia")));
                map.put("ma_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ma_doc_gia")));
                map.put("ngay_yeu_cau", cursor.getString(cursor.getColumnIndexOrThrow("ngay_yeu_cau")));
                map.put("trang_thai", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai")));
                map.put("ngay_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("ngay_xu_ly")));
                map.put("ly_do_tu_choi", cursor.getString(cursor.getColumnIndexOrThrow("ly_do_tu_choi")));
                map.put("nguoi_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("nguoi_xu_ly")));
                map.put("da_nhan", cursor.getInt(cursor.getColumnIndexOrThrow("da_nhan")));
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Lấy danh sách các yêu cầu in thẻ của một độc giả
     */
    public List<Map<String, Object>> getReaderCardRequests(int ma_nd_doc_gia) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT yc.*, nd.ho_ten as nguoi_xu_ly " +
                     "FROM YEU_CAU_THE yc " +
                     "LEFT JOIN NGUOI_DUNG nd ON yc.ma_nd_xu_ly = nd.ma_nd " +
                     "WHERE yc.ma_nd_doc_gia = ? " +
                     "ORDER BY yc.ngay_yeu_cau DESC";

        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_nd_doc_gia)})) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_yeu_cau", cursor.getInt(cursor.getColumnIndexOrThrow("ma_yeu_cau")));
                map.put("ngay_yeu_cau", cursor.getString(cursor.getColumnIndexOrThrow("ngay_yeu_cau")));
                map.put("trang_thai", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai")));
                map.put("ngay_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("ngay_xu_ly")));
                map.put("ly_do_tu_choi", cursor.getString(cursor.getColumnIndexOrThrow("ly_do_tu_choi")));
                map.put("nguoi_xu_ly", cursor.getString(cursor.getColumnIndexOrThrow("nguoi_xu_ly")));
                map.put("da_nhan", cursor.getInt(cursor.getColumnIndexOrThrow("da_nhan")));
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Duyệt yêu cầu in thẻ (Nhân viên đánh dấu DANG_XU_LY)
     */
    public boolean approveCardRequest(int ma_yeu_cau, int ma_nd_xu_ly) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String getReq = "SELECT * FROM YEU_CAU_THE WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        String trang_thai = "";
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
            } else {
                throw new Exception("Không tìm thấy yêu cầu!");
            }
        }

        if (!"CHO_DUYET".equals(trang_thai)) {
            throw new Exception("Yêu cầu này đã được xử lý!");
        }

        db.beginTransaction();
        try {
            String ngay_xu_ly = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("trang_thai", "DANG_XU_LY");
            values.put("ma_nd_xu_ly", ma_nd_xu_ly);
            values.put("ngay_xu_ly", ngay_xu_ly);
            db.update("YEU_CAU_THE", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Xóa thông báo nhân viên
            db.delete("THONG_BAO", "link_lien_quan = ? AND loai_thong_bao = 'YEU_CAU_MOI'", new String[]{"yeu_cau_the:" + ma_yeu_cau});

            // Gửi thông báo cho Độc giả
            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "✅ Yêu cầu in thẻ đang được xử lý");
            notif.put("noi_dung", "Yêu cầu in thẻ của bạn đã được nhân viên tiếp nhận và đang xử lý.");
            notif.put("loai_thong_bao", "YEU_CAU_DUYET");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_xu_ly);
            notif.put("link_lien_quan", "yeu_cau_the:" + ma_yeu_cau);
            db.insert("THONG_BAO", null, notif);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Xác nhận đã in thẻ xong -> Tạo hoặc cập nhật THE_DOC_GIA và chuyển trạng thái sang DA_IN
     */
    public boolean markCardPrinted(int ma_yeu_cau, int ma_nd_xu_ly) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String getReq = "SELECT * FROM YEU_CAU_THE WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        String trang_thai = "";
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
            } else {
                throw new Exception("Không tìm thấy yêu cầu!");
            }
        }

        if (!"CHO_DUYET".equals(trang_thai) && !"DANG_XU_LY".equals(trang_thai)) {
            throw new Exception("Yêu cầu này không ở trạng thái có thể in!");
        }

        db.beginTransaction();
        try {
            String ngay_xu_ly = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            // 1. Cập nhật yêu cầu sang DA_IN
            ContentValues values = new ContentValues();
            values.put("trang_thai", "DA_IN");
            values.put("ma_nd_xu_ly", ma_nd_xu_ly);
            values.put("ngay_xu_ly", ngay_xu_ly);
            db.update("YEU_CAU_THE", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // 2. Tạo hoặc Cập nhật THE_DOC_GIA thời hạn 1 năm
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            String ngay_cap = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 365);
            String ngay_het_han = sdf.format(cal.getTime());

            String checkCard = "SELECT ma_the FROM THE_DOC_GIA WHERE ma_nd_doc_gia = ?";
            boolean existCard = false;
            try (Cursor c = db.rawQuery(checkCard, new String[]{String.valueOf(ma_nd_doc_gia)})) {
                if (c.getCount() > 0) existCard = true;
            }

            if (existCard) {
                db.execSQL("UPDATE THE_DOC_GIA SET ngay_cap = ?, ngay_het_han = ?, trang_thai_the = 'HOAT_DONG' WHERE ma_nd_doc_gia = ?",
                        new Object[]{ngay_cap, ngay_het_han, ma_nd_doc_gia});
            } else {
                db.execSQL("INSERT INTO THE_DOC_GIA (ma_nd_doc_gia, ngay_cap, ngay_het_han, trang_thai_the) VALUES (?, ?, ?, 'HOAT_DONG')",
                        new Object[]{ma_nd_doc_gia, ngay_cap, ngay_het_han});
            }

            // Xóa thông báo liên quan
            db.delete("THONG_BAO", "link_lien_quan = ?", new String[]{"yeu_cau_the:" + ma_yeu_cau});

            // Gửi thông báo cho Độc giả
            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "✅ Thẻ độc giả đã được in");
            notif.put("noi_dung", "Thẻ độc giả của bạn đã được in và kích hoạt. Vui lòng đến thư viện nhận thẻ.");
            notif.put("loai_thong_bao", "HE_THONG");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_xu_ly);
            notif.put("link_lien_quan", "yeu_cau_the:" + ma_yeu_cau);
            db.insert("THONG_BAO", null, notif);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Xác nhận đọc giả đã lấy thẻ vật lý
     */
    public boolean confirmCardPickup(int ma_yeu_cau, int ma_nd_xu_ly) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String getReq = "SELECT * FROM YEU_CAU_THE WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        String trang_thai = "";
        int da_nhan = 0;
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
                da_nhan = c.getInt(c.getColumnIndexOrThrow("da_nhan"));
            } else {
                throw new Exception("Không tìm thấy yêu cầu in thẻ!");
            }
        }

        if (!"DA_IN".equals(trang_thai)) {
            throw new Exception("Chỉ có thể xác nhận lấy thẻ khi thẻ đã được in!");
        }

        if (da_nhan == 1) {
            throw new Exception("Yêu cầu đã được xác nhận lấy trước đó.");
        }

        db.beginTransaction();
        try {
            String ngay_xu_ly = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("da_nhan", 1);
            values.put("ma_nd_xu_ly", ma_nd_xu_ly);
            values.put("ngay_xu_ly", ngay_xu_ly);
            db.update("YEU_CAU_THE", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Thông báo Độc giả
            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "🎉 Đã nhận thẻ");
            notif.put("noi_dung", "Bạn đã nhận thẻ độc giả tại thư viện. Cảm ơn!");
            notif.put("loai_thong_bao", "HE_THONG");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_xu_ly);
            notif.put("link_lien_quan", "yeu_cau_the:" + ma_yeu_cau);
            db.insert("THONG_BAO", null, notif);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Từ chối yêu cầu in thẻ
     */
    public boolean rejectCardRequest(int ma_yeu_cau, int ma_nd_xu_ly, String ly_do) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String getReq = "SELECT * FROM YEU_CAU_THE WHERE ma_yeu_cau = ?";
        int ma_nd_doc_gia = 0;
        String trang_thai = "";
        try (Cursor c = db.rawQuery(getReq, new String[]{String.valueOf(ma_yeu_cau)})) {
            if (c.moveToFirst()) {
                ma_nd_doc_gia = c.getInt(c.getColumnIndexOrThrow("ma_nd_doc_gia"));
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
            } else {
                throw new Exception("Không tìm thấy yêu cầu in thẻ!");
            }
        }

        if (!"CHO_DUYET".equals(trang_thai)) {
            throw new Exception("Chỉ có thể từ chối yêu cầu ở trạng thái Chờ duyệt!");
        }

        db.beginTransaction();
        try {
            String ngay_xu_ly = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("trang_thai", "TU_CHOI");
            values.put("ma_nd_xu_ly", ma_nd_xu_ly);
            values.put("ngay_xu_ly", ngay_xu_ly);
            values.put("ly_do_tu_choi", ly_do);
            db.update("YEU_CAU_THE", values, "ma_yeu_cau = ?", new String[]{String.valueOf(ma_yeu_cau)});

            // Xóa thông báo nhân viên
            db.delete("THONG_BAO", "link_lien_quan = ? AND loai_thong_bao = 'YEU_CAU_MOI'", new String[]{"yeu_cau_the:" + ma_yeu_cau});

            // Gửi thông báo cho Độc giả
            String text = "Yêu cầu in thẻ của bạn đã bị từ chối.";
            if (ly_do != null && !ly_do.isEmpty()) {
                text += " Lý do: " + ly_do;
            }

            ContentValues notif = new ContentValues();
            notif.put("ma_nd", ma_nd_doc_gia);
            notif.put("tieu_de", "❌ Yêu cầu in thẻ bị từ chối");
            notif.put("noi_dung", text);
            notif.put("loai_thong_bao", "YEU_CAU_TU_CHOI");
            notif.put("da_doc", 0);
            notif.put("ngay_tao", ngay_xu_ly);
            notif.put("link_lien_quan", "yeu_cau_the:" + ma_yeu_cau);
            db.insert("THONG_BAO", null, notif);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }
}
