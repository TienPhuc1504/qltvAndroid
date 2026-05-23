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

public class BorrowRepository {

    private final DatabaseOpenHelper dbHelper;
    private static final double FINE_PER_DAY = 5000.0;

    public BorrowRepository(Context context) {
        this.dbHelper = new DatabaseOpenHelper(context);
        this.dbHelper.initializeDatabase();
    }

    /**
     * Lấy danh sách toàn bộ phiếu mượn trả
     */
    public List<Map<String, Object>> getAllBorrows(String searchTerm, String status) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> borrows = new ArrayList<>();

        String sql = "SELECT p.*, " +
                     "nd_dg.ho_ten as ten_doc_gia, dg.ma_doc_gia, " +
                     "nd_nv.ho_ten as ten_nhan_vien, nv.ma_nhan_vien, " +
                     "s.tieu_de, s.tac_gia, s.loai_sach, qs.ma_quyen_sach " +
                     "FROM PHIEU_MUON_TRA p " +
                     "JOIN DOC_GIA dg ON p.ma_nd_doc_gia = dg.ma_nd " +
                     "JOIN NGUOI_DUNG nd_dg ON dg.ma_nd = nd_dg.ma_nd " +
                     "JOIN NHAN_VIEN nv ON p.ma_nd_nhan_vien = nv.ma_nd " +
                     "JOIN NGUOI_DUNG nd_nv ON nv.ma_nd = nd_nv.ma_nd " +
                     "JOIN SACH s ON p.ma_sach = s.ma_sach " +
                     "LEFT JOIN QUYEN_SACH qs ON p.ma_quyen = qs.ma_quyen " +
                     "WHERE 1=1";

        List<String> paramsList = new ArrayList<>();
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql += " AND (nd_dg.ho_ten LIKE ? OR dg.ma_doc_gia LIKE ? OR s.tieu_de LIKE ?)";
            String searchPattern = "%" + searchTerm.trim() + "%";
            paramsList.add(searchPattern);
            paramsList.add(searchPattern);
            paramsList.add(searchPattern);
        }

        if (status != null && !status.isEmpty()) {
            sql += " AND p.trang_thai_phieu = ?";
            paramsList.add(status);
        }

        sql += " ORDER BY p.ma_phieu DESC";

        String[] params = paramsList.isEmpty() ? null : paramsList.toArray(new String[0]);

        try (Cursor cursor = db.rawQuery(sql, params)) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                int maPhieu = cursor.getInt(cursor.getColumnIndexOrThrow("ma_phieu"));
                String trangThai = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_phieu"));
                String ngayHenTra = cursor.getString(cursor.getColumnIndexOrThrow("ngay_hen_tra"));
                double fine = cursor.getDouble(cursor.getColumnIndexOrThrow("tien_phat"));

                if ("DANG_MUON".equals(trangThai)) {
                    fine = calculateFine(ngayHenTra);
                }

                map.put("ma_phieu", maPhieu);
                map.put("ma_nd_doc_gia", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd_doc_gia")));
                map.put("ten_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ten_doc_gia")));
                map.put("ma_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ma_doc_gia")));
                map.put("ma_nd_nhan_vien", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd_nhan_vien")));
                map.put("ten_nhan_vien", cursor.getString(cursor.getColumnIndexOrThrow("ten_nhan_vien")));
                map.put("ma_nhan_vien", cursor.getString(cursor.getColumnIndexOrThrow("ma_nhan_vien")));
                map.put("ma_sach", cursor.getInt(cursor.getColumnIndexOrThrow("ma_sach")));
                map.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                map.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                
                int maQuyenIdx = cursor.getColumnIndexOrThrow("ma_quyen");
                if (!cursor.isNull(maQuyenIdx)) {
                    map.put("ma_quyen", cursor.getInt(maQuyenIdx));
                    map.put("ma_quyen_sach", cursor.getString(cursor.getColumnIndexOrThrow("ma_quyen_sach")));
                }
                
                map.put("ngay_muon", cursor.getString(cursor.getColumnIndexOrThrow("ngay_muon")));
                map.put("ngay_hen_tra", ngayHenTra);
                map.put("ngay_tra_thuc", cursor.getString(cursor.getColumnIndexOrThrow("ngay_tra_thuc")));
                map.put("trang_thai_phieu", trangThai);
                map.put("tien_phat", fine);
                borrows.add(map);
            }
        }
        return borrows;
    }

    /**
     * Lấy thông tin chi tiết một phiếu mượn
     */
    public Map<String, Object> getBorrowById(int ma_phieu) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT p.*, " +
                     "nd_dg.ho_ten as ten_doc_gia, nd_dg.dia_chi, nd_dg.so_dt, nd_dg.email, dg.ma_doc_gia, " +
                     "nd_nv.ho_ten as ten_nhan_vien, nv.ma_nhan_vien, " +
                     "s.tieu_de, s.tac_gia, s.loai_sach, tl.ten_the_loai, qs.ma_quyen_sach " +
                     "FROM PHIEU_MUON_TRA p " +
                     "JOIN DOC_GIA dg ON p.ma_nd_doc_gia = dg.ma_nd " +
                     "JOIN NGUOI_DUNG nd_dg ON dg.ma_nd = nd_dg.ma_nd " +
                     "JOIN NHAN_VIEN nv ON p.ma_nd_nhan_vien = nv.ma_nd " +
                     "JOIN NGUOI_DUNG nd_nv ON nv.ma_nd = nd_nv.ma_nd " +
                     "JOIN SACH s ON p.ma_sach = s.ma_sach " +
                     "LEFT JOIN THE_LOAI_SACH tl ON s.ma_the_loai = tl.ma_the_loai " +
                     "LEFT JOIN QUYEN_SACH qs ON p.ma_quyen = qs.ma_quyen " +
                     "WHERE p.ma_phieu = ?";

        Map<String, Object> map = null;
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_phieu)})) {
            if (cursor.moveToFirst()) {
                map = new HashMap<>();
                String trangThai = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_phieu"));
                String ngayHenTra = cursor.getString(cursor.getColumnIndexOrThrow("ngay_hen_tra"));
                double fine = cursor.getDouble(cursor.getColumnIndexOrThrow("tien_phat"));

                if ("DANG_MUON".equals(trangThai)) {
                    fine = calculateFine(ngayHenTra);
                }

                map.put("ma_phieu", cursor.getInt(cursor.getColumnIndexOrThrow("ma_phieu")));
                map.put("ma_nd_doc_gia", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd_doc_gia")));
                map.put("ten_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ten_doc_gia")));
                map.put("dia_chi", cursor.getString(cursor.getColumnIndexOrThrow("dia_chi")));
                map.put("so_dt", cursor.getString(cursor.getColumnIndexOrThrow("so_dt")));
                map.put("email", cursor.getString(cursor.getColumnIndexOrThrow("email")));
                map.put("ma_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ma_doc_gia")));
                map.put("ma_nd_nhan_vien", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd_nhan_vien")));
                map.put("ten_nhan_vien", cursor.getString(cursor.getColumnIndexOrThrow("ten_nhan_vien")));
                map.put("ma_nhan_vien", cursor.getString(cursor.getColumnIndexOrThrow("ma_nhan_vien")));
                map.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                map.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                map.put("ten_the_loai", cursor.getString(cursor.getColumnIndexOrThrow("ten_the_loai")));
                
                int maQuyenIdx = cursor.getColumnIndexOrThrow("ma_quyen");
                if (!cursor.isNull(maQuyenIdx)) {
                    map.put("ma_quyen", cursor.getInt(maQuyenIdx));
                    map.put("ma_quyen_sach", cursor.getString(cursor.getColumnIndexOrThrow("ma_quyen_sach")));
                }

                map.put("ngay_muon", cursor.getString(cursor.getColumnIndexOrThrow("ngay_muon")));
                map.put("ngay_hen_tra", ngayHenTra);
                map.put("ngay_tra_thuc", cursor.getString(cursor.getColumnIndexOrThrow("ngay_tra_thuc")));
                map.put("trang_thai_phieu", trangThai);
                map.put("tien_phat", fine);
            }
        }
        return map;
    }

    /**
     * Tạo phiếu mượn trực tiếp tại quầy (Nhân viên làm)
     */
    public long createBorrow(int ma_nd_doc_gia, int ma_nd_nhan_vien, int ma_sach, int so_ngay_muon) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 1. Kiểm tra độc giả đã mượn sách này chưa mà chưa trả
        String checkBorrow = "SELECT COUNT(*) FROM PHIEU_MUON_TRA " +
                             "WHERE ma_nd_doc_gia = ? AND ma_sach = ? AND trang_thai_phieu = 'DANG_MUON'";
        try (Cursor cursor = db.rawQuery(checkBorrow, new String[]{String.valueOf(ma_nd_doc_gia), String.valueOf(ma_sach)})) {
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                throw new Exception("Độc giả đã mượn sách này rồi! Vui lòng trả sách trước khi mượn lại.");
            }
        }

        // 2. Kiểm tra loại sách
        String checkType = "SELECT loai_sach FROM SACH WHERE ma_sach = ?";
        String loai_sach = "";
        try (Cursor cursor = db.rawQuery(checkType, new String[]{String.valueOf(ma_sach)})) {
            if (cursor.moveToFirst()) {
                loai_sach = cursor.getString(cursor.getColumnIndexOrThrow("loai_sach"));
            } else {
                throw new Exception("Không tìm thấy sách!");
            }
        }

        Integer ma_quyen = null;
        if ("SACH_GIAY".equals(loai_sach)) {
            // Tìm bản sao quyển sách có sẵn
            String findCopy = "SELECT ma_quyen FROM QUYEN_SACH WHERE ma_sach = ? AND trang_thai = 'CO_SAN' ORDER BY ma_quyen LIMIT 1";
            try (Cursor cursor = db.rawQuery(findCopy, new String[]{String.valueOf(ma_sach)})) {
                if (cursor.moveToFirst()) {
                    ma_quyen = cursor.getInt(0);
                } else {
                    throw new Exception("Sách đã hết bản sao có sẵn!");
                }
            }
        }

        db.beginTransaction();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            String ngay_muon = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, so_ngay_muon);
            String ngay_hen_tra = sdf.format(cal.getTime());

            // Nhân viên chưa tồn tại trong NHAN_VIEN? Auto-insert để bảo toàn khóa ngoại
            db.execSQL("INSERT OR IGNORE INTO NHAN_VIEN (ma_nd, ma_nhan_vien) VALUES (?, ?)",
                    new Object[]{ma_nd_nhan_vien, String.format(Locale.getDefault(), "NV%04d", ma_nd_nhan_vien)});

            ContentValues values = new ContentValues();
            values.put("ma_nd_doc_gia", ma_nd_doc_gia);
            values.put("ma_nd_nhan_vien", ma_nd_nhan_vien);
            values.put("ma_sach", ma_sach);
            if (ma_quyen != null) {
                values.put("ma_quyen", ma_quyen);
            }
            values.put("ngay_muon", ngay_muon);
            values.put("ngay_hen_tra", ngay_hen_tra);
            values.put("trang_thai_phieu", "DANG_MUON");
            values.put("tien_phat", 0.0);
            long ma_phieu = db.insertOrThrow("PHIEU_MUON_TRA", null, values);

            // Cập nhật trạng thái quyển sách
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
     * Xử lý trả sách và ghi nhận phạt trễ hạn
     */
    public boolean returnBook(int ma_phieu, double tien_phat) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Lấy thông tin quyển sách
        String sql = "SELECT ma_quyen FROM PHIEU_MUON_TRA WHERE ma_phieu = ?";
        Integer ma_quyen = null;
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_phieu)})) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                ma_quyen = cursor.getInt(0);
            }
        }

        db.beginTransaction();
        try {
            String ngay_tra = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("ngay_tra_thuc", ngay_tra);
            values.put("trang_thai_phieu", "DA_TRA");
            values.put("tien_phat", tien_phat);
            db.update("PHIEU_MUON_TRA", values, "ma_phieu = ?", new String[]{String.valueOf(ma_phieu)});

            // Trả trạng thái quyển sách về CO_SAN
            if (ma_quyen != null) {
                db.execSQL("UPDATE QUYEN_SACH SET trang_thai = 'CO_SAN' WHERE ma_quyen = ?", new Object[]{ma_quyen});
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Tính toán tiền phạt trễ hạn dựa vào ngay_hen_tra
     */
    public double calculateFine(String ngayHenTraStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date ngayHenTra = sdf.parse(ngayHenTraStr);
            Date ngayHienTai = sdf.parse(sdf.format(new Date()));

            if (ngayHienTai != null && ngayHenTra != null && ngayHienTai.after(ngayHenTra)) {
                long diff = ngayHienTai.getTime() - ngayHenTra.getTime();
                long days = diff / (24 * 60 * 60 * 1000);
                return days * FINE_PER_DAY;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    /**
     * Lấy lịch sử mượn trả của một độc giả
     */
    public List<Map<String, Object>> getReaderBorrowHistory(int ma_nd_doc_gia) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> history = new ArrayList<>();

        String sql = "SELECT p.*, s.tieu_de, s.tac_gia, s.loai_sach, qs.ma_quyen_sach, " +
                     "nd_nv.ho_ten as ten_nhan_vien, so.url_tai_lieu, so.dinh_dang " +
                     "FROM PHIEU_MUON_TRA p " +
                     "JOIN SACH s ON p.ma_sach = s.ma_sach " +
                     "LEFT JOIN SACH_ONLINE so ON s.ma_sach = so.ma_sach " +
                     "JOIN NHAN_VIEN nv ON p.ma_nd_nhan_vien = nv.ma_nd " +
                     "JOIN NGUOI_DUNG nd_nv ON nv.ma_nd = nd_nv.ma_nd " +
                     "LEFT JOIN QUYEN_SACH qs ON p.ma_quyen = qs.ma_quyen " +
                     "WHERE p.ma_nd_doc_gia = ? " +
                     "ORDER BY p.ngay_muon DESC";

        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_nd_doc_gia)})) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                String trangThai = cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_phieu"));
                String ngayHenTra = cursor.getString(cursor.getColumnIndexOrThrow("ngay_hen_tra"));
                double fine = cursor.getDouble(cursor.getColumnIndexOrThrow("tien_phat"));

                if ("DANG_MUON".equals(trangThai)) {
                    fine = calculateFine(ngayHenTra);
                }

                map.put("ma_phieu", cursor.getInt(cursor.getColumnIndexOrThrow("ma_phieu")));
                map.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                map.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                map.put("ma_quyen_sach", cursor.getString(cursor.getColumnIndexOrThrow("ma_quyen_sach")));
                map.put("ten_nhan_vien", cursor.getString(cursor.getColumnIndexOrThrow("ten_nhan_vien")));
                map.put("url_tai_lieu", cursor.getString(cursor.getColumnIndexOrThrow("url_tai_lieu")));
                map.put("dinh_dang", cursor.getString(cursor.getColumnIndexOrThrow("dinh_dang")));
                map.put("ngay_muon", cursor.getString(cursor.getColumnIndexOrThrow("ngay_muon")));
                map.put("ngay_hen_tra", ngayHenTra);
                map.put("ngay_tra_thuc", cursor.getString(cursor.getColumnIndexOrThrow("ngay_tra_thuc")));
                map.put("trang_thai_phieu", trangThai);
                map.put("tien_phat", fine);
                history.add(map);
            }
        }
        return history;
    }

    /**
     * Lấy các số liệu thống kê (Thống kê sách & Thống kê mượn trả)
     */
    public Map<String, Object> getLibraryStatistics() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Map<String, Object> stats = new HashMap<>();

        // 1. Tổng số đầu sách
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM SACH", null)) {
            if (c.moveToFirst()) stats.put("total_books", c.getInt(0));
        }

        // 2. Sách theo trạng thái đầu sách
        Map<String, Integer> byStatus = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT trang_thai_sach, COUNT(*) FROM SACH GROUP BY trang_thai_sach", null)) {
            while (c.moveToNext()) {
                byStatus.put(c.getString(0), c.getInt(1));
            }
        }
        stats.put("by_status", byStatus);

        // 3. Sách theo thể loại
        List<Map<String, Object>> byCategory = new ArrayList<>();
        String catSql = "SELECT tl.ten_the_loai, COUNT(s.ma_sach) as count " +
                        "FROM THE_LOAI_SACH tl " +
                        "LEFT JOIN SACH s ON tl.ma_the_loai = s.ma_the_loai " +
                        "GROUP BY tl.ma_the_loai ORDER BY count DESC";
        try (Cursor c = db.rawQuery(catSql, null)) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("category_name", c.getString(0));
                map.put("count", c.getInt(1));
                byCategory.add(map);
            }
        }
        stats.put("by_category", byCategory);

        // 4. Tổng số phiếu mượn
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM PHIEU_MUON_TRA", null)) {
            if (c.moveToFirst()) stats.put("total_borrows", c.getInt(0));
        }

        // 5. Phiếu mượn theo trạng thái
        Map<String, Integer> borrowByStatus = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT trang_thai_phieu, COUNT(*) FROM PHIEU_MUON_TRA GROUP BY trang_thai_phieu", null)) {
            while (c.moveToNext()) {
                borrowByStatus.put(c.getString(0), c.getInt(1));
            }
        }
        stats.put("borrow_by_status", borrowByStatus);

        // 6. Số phiếu quá hạn hiện tại
        String overdueSql = "SELECT COUNT(*) FROM PHIEU_MUON_TRA WHERE trang_thai_phieu = 'DANG_MUON' AND ngay_hen_tra < date('now')";
        try (Cursor c = db.rawQuery(overdueSql, null)) {
            if (c.moveToFirst()) stats.put("overdue_count", c.getInt(0));
        }

        // 7. Tổng số tiền phạt thu được
        try (Cursor c = db.rawQuery("SELECT SUM(tien_phat) FROM PHIEU_MUON_TRA", null)) {
            if (c.moveToFirst() && !c.isNull(0)) {
                stats.put("total_fines", c.getDouble(0));
            } else {
                stats.put("total_fines", 0.0);
            }
        }

        // 8. Top 10 sách mượn nhiều nhất
        List<Map<String, Object>> mostBorrowed = new ArrayList<>();
        String topSql = "SELECT s.tieu_de, s.tac_gia, COUNT(p.ma_phieu) as borrow_count " +
                        "FROM SACH s " +
                        "LEFT JOIN PHIEU_MUON_TRA p ON s.ma_sach = p.ma_sach " +
                        "GROUP BY s.ma_sach ORDER BY borrow_count DESC LIMIT 10";
        try (Cursor c = db.rawQuery(topSql, null)) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("tieu_de", c.getString(0));
                map.put("tac_gia", c.getString(1));
                map.put("borrow_count", c.getInt(2));
                mostBorrowed.add(map);
            }
        }
        stats.put("most_borrowed", mostBorrowed);

        return stats;
    }
}
