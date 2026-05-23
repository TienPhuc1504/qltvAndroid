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

public class BookRepository {

    private final DatabaseOpenHelper dbHelper;

    public BookRepository(Context context) {
        this.dbHelper = new DatabaseOpenHelper(context);
        this.dbHelper.initializeDatabase();
    }

    /**
     * Lấy danh sách tất cả sách kèm bộ lọc tìm kiếm và thể loại
     */
    public List<Map<String, Object>> getAllBooks(String searchTerm, Integer categoryId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> books = new ArrayList<>();

        String sql = "SELECT s.ma_sach, s.tieu_de, s.tac_gia, s.trang_thai_sach, s.loai_sach, s.ma_the_loai, " +
                     "tl.ten_the_loai, COALESCE(sg.so_luong, 0) as so_luong, so.url_tai_lieu, so.dinh_dang, " +
                     "(SELECT COUNT(*) FROM QUYEN_SACH q WHERE q.ma_sach = s.ma_sach AND q.trang_thai = 'CO_SAN') as so_quyen_co_san, " +
                     "(SELECT COUNT(*) FROM QUYEN_SACH q WHERE q.ma_sach = s.ma_sach) as tong_so_quyen " +
                     "FROM SACH s " +
                     "LEFT JOIN THE_LOAI_SACH tl ON s.ma_the_loai = tl.ma_the_loai " +
                     "LEFT JOIN SACH_GIAY sg ON s.ma_sach = sg.ma_sach " +
                     "LEFT JOIN SACH_ONLINE so ON s.ma_sach = so.ma_sach " +
                     "WHERE 1=1";

        List<String> paramsList = new ArrayList<>();
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql += " AND (s.tieu_de LIKE ? OR s.tac_gia LIKE ?)";
            String searchPattern = "%" + searchTerm.trim() + "%";
            paramsList.add(searchPattern);
            paramsList.add(searchPattern);
        }

        if (categoryId != null && categoryId > 0) {
            sql += " AND s.ma_the_loai = ?";
            paramsList.add(String.valueOf(categoryId));
        }

        sql += " ORDER BY s.ma_sach DESC";

        String[] params = paramsList.isEmpty() ? null : paramsList.toArray(new String[0]);

        try (Cursor cursor = db.rawQuery(sql, params)) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_sach", cursor.getInt(cursor.getColumnIndexOrThrow("ma_sach")));
                map.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                map.put("trang_thai_sach", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_sach")));
                map.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                map.put("ma_the_loai", cursor.getInt(cursor.getColumnIndexOrThrow("ma_the_loai")));
                map.put("ten_the_loai", cursor.getString(cursor.getColumnIndexOrThrow("ten_the_loai")));
                map.put("so_luong", cursor.getInt(cursor.getColumnIndexOrThrow("so_luong")));
                map.put("url_tai_lieu", cursor.getString(cursor.getColumnIndexOrThrow("url_tai_lieu")));
                map.put("dinh_dang", cursor.getString(cursor.getColumnIndexOrThrow("dinh_dang")));
                map.put("so_quyen_co_san", cursor.getInt(cursor.getColumnIndexOrThrow("so_quyen_co_san")));
                map.put("tong_so_quyen", cursor.getInt(cursor.getColumnIndexOrThrow("tong_so_quyen")));
                books.add(map);
            }
        }
        return books;
    }

    /**
     * Lấy thông tin chi tiết một đầu sách
     */
    public Map<String, Object> getBookById(int ma_sach) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT s.*, tl.ten_the_loai, COALESCE(sg.so_luong, 0) as so_luong, " +
                     "so.url_tai_lieu, so.dinh_dang, " +
                     "(SELECT COUNT(*) FROM QUYEN_SACH q WHERE q.ma_sach = s.ma_sach AND q.trang_thai = 'CO_SAN') as so_quyen_co_san, " +
                     "(SELECT COUNT(*) FROM QUYEN_SACH q WHERE q.ma_sach = s.ma_sach) as tong_so_quyen " +
                     "FROM SACH s " +
                     "LEFT JOIN THE_LOAI_SACH tl ON s.ma_the_loai = tl.ma_the_loai " +
                     "LEFT JOIN SACH_GIAY sg ON s.ma_sach = sg.ma_sach " +
                     "LEFT JOIN SACH_ONLINE so ON s.ma_sach = so.ma_sach " +
                     "WHERE s.ma_sach = ?";

        Map<String, Object> book = null;
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_sach)})) {
            if (cursor.moveToFirst()) {
                book = new HashMap<>();
                book.put("ma_sach", cursor.getInt(cursor.getColumnIndexOrThrow("ma_sach")));
                book.put("tieu_de", cursor.getString(cursor.getColumnIndexOrThrow("tieu_de")));
                book.put("tac_gia", cursor.getString(cursor.getColumnIndexOrThrow("tac_gia")));
                book.put("trang_thai_sach", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_sach")));
                book.put("loai_sach", cursor.getString(cursor.getColumnIndexOrThrow("loai_sach")));
                book.put("ma_the_loai", cursor.getInt(cursor.getColumnIndexOrThrow("ma_the_loai")));
                book.put("ten_the_loai", cursor.getString(cursor.getColumnIndexOrThrow("ten_the_loai")));
                book.put("so_luong", cursor.getInt(cursor.getColumnIndexOrThrow("so_luong")));
                book.put("url_tai_lieu", cursor.getString(cursor.getColumnIndexOrThrow("url_tai_lieu")));
                book.put("dinh_dang", cursor.getString(cursor.getColumnIndexOrThrow("dinh_dang")));
                book.put("so_quyen_co_san", cursor.getInt(cursor.getColumnIndexOrThrow("so_quyen_co_san")));
                book.put("tong_so_quyen", cursor.getInt(cursor.getColumnIndexOrThrow("tong_so_quyen")));
            }
        }
        return book;
    }

    /**
     * Thêm sách mới (giấy hoặc online)
     */
    public long addBook(String tieu_de, String tac_gia, int ma_the_loai, String loai_sach,
                        int so_luong, String url_tai_lieu, String dinh_dang) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues bookValues = new ContentValues();
            bookValues.put("tieu_de", tieu_de);
            bookValues.put("tac_gia", tac_gia);
            bookValues.put("trang_thai_sach", "CO_SAN");
            bookValues.put("ma_the_loai", ma_the_loai);
            bookValues.put("loai_sach", loai_sach);
            long ma_sach = db.insertOrThrow("SACH", null, bookValues);

            if ("SACH_GIAY".equals(loai_sach)) {
                ContentValues paperValues = new ContentValues();
                paperValues.put("ma_sach", ma_sach);
                paperValues.put("so_luong", so_luong);
                db.insertOrThrow("SACH_GIAY", null, paperValues);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String ngay_nhap = sdf.format(new Date());

                for (int i = 1; i <= so_luong; i++) {
                    String ma_quyen_sach = String.format(Locale.getDefault(), "S%04d-Q%03d", ma_sach, i);
                    ContentValues copyValues = new ContentValues();
                    copyValues.put("ma_sach", ma_sach);
                    copyValues.put("ma_quyen_sach", ma_quyen_sach);
                    copyValues.put("trang_thai", "CO_SAN");
                    copyValues.put("ngay_nhap", ngay_nhap);
                    db.insertOrThrow("QUYEN_SACH", null, copyValues);
                }
            } else {
                ContentValues onlineValues = new ContentValues();
                onlineValues.put("ma_sach", ma_sach);
                onlineValues.put("url_tai_lieu", url_tai_lieu);
                onlineValues.put("dinh_dang", dinh_dang);
                db.insertOrThrow("SACH_ONLINE", null, onlineValues);
            }
            db.setTransactionSuccessful();
            return ma_sach;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Cập nhật thông tin đầu sách
     */
    public boolean updateBook(int ma_sach, String tieu_de, String tac_gia, int ma_the_loai,
                              String trang_thai, String url_tai_lieu, String dinh_dang) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues bookValues = new ContentValues();
            bookValues.put("tieu_de", tieu_de);
            bookValues.put("tac_gia", tac_gia);
            bookValues.put("ma_the_loai", ma_the_loai);
            db.update("SACH", bookValues, "ma_sach = ?", new String[]{String.valueOf(ma_sach)});

            // Kiểm tra loại sách
            String typeQuery = "SELECT loai_sach FROM SACH WHERE ma_sach = ?";
            String loai_sach = "";
            try (Cursor c = db.rawQuery(typeQuery, new String[]{String.valueOf(ma_sach)})) {
                if (c.moveToFirst()) {
                    loai_sach = c.getString(c.getColumnIndexOrThrow("loai_sach"));
                }
            }

            if ("SACH_GIAY".equals(loai_sach)) {
                if ("KHONG_CO_SAN".equals(trang_thai)) {
                    ContentValues copyVal = new ContentValues();
                    copyVal.put("trang_thai", "KHONG_CO_SAN");
                    db.update("QUYEN_SACH", copyVal, "ma_sach = ? AND trang_thai = 'CO_SAN'", new String[]{String.valueOf(ma_sach)});
                } else if ("CO_SAN".equals(trang_thai)) {
                    ContentValues copyVal = new ContentValues();
                    copyVal.put("trang_thai", "CO_SAN");
                    db.update("QUYEN_SACH", copyVal, "ma_sach = ? AND trang_thai = 'KHONG_CO_SAN'", new String[]{String.valueOf(ma_sach)});
                }
            } else if ("SACH_ONLINE".equals(loai_sach)) {
                ContentValues onlineVal = new ContentValues();
                onlineVal.put("url_tai_lieu", url_tai_lieu);
                onlineVal.put("dinh_dang", dinh_dang);
                db.update("SACH_ONLINE", onlineVal, "ma_sach = ?", new String[]{String.valueOf(ma_sach)});
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Xóa đầu sách
     */
    public void deleteBook(int ma_sach) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 1. Kiểm tra quyển sách có đang được mượn không
        String checkBorrow = "SELECT COUNT(*) FROM QUYEN_SACH WHERE ma_sach = ? AND trang_thai = 'DANG_MUON'";
        try (Cursor cursor = db.rawQuery(checkBorrow, new String[]{String.valueOf(ma_sach)})) {
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                throw new Exception("Không thể xóa sách có quyển đang được mượn!");
            }
        }

        // 2. Kiểm tra yêu cầu mượn đang xử lý
        String checkRequest = "SELECT COUNT(*) FROM YEU_CAU_MUON WHERE ma_sach = ? AND trang_thai IN ('CHO_DUYET', 'CHO_LAY_SACH')";
        try (Cursor cursor = db.rawQuery(checkRequest, new String[]{String.valueOf(ma_sach)})) {
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                throw new Exception("Không thể xóa sách có yêu cầu mượn đang chờ xử lý!");
            }
        }

        db.beginTransaction();
        try {
            // Do CASCADE, chỉ cần xóa QUYEN_SACH và SACH
            db.delete("QUYEN_SACH", "ma_sach = ?", new String[]{String.valueOf(ma_sach)});
            db.delete("SACH", "ma_sach = ?", new String[]{String.valueOf(ma_sach)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Quản lý thể loại (Lấy toàn bộ)
     */
    public List<Map<String, Object>> getAllCategories() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM THE_LOAI_SACH ORDER BY ten_the_loai";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_the_loai", c.getInt(c.getColumnIndexOrThrow("ma_the_loai")));
                map.put("ten_the_loai", c.getString(c.getColumnIndexOrThrow("ten_the_loai")));
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Thêm thể loại mới
     */
    public long addCategory(String ten_the_loai) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ten_the_loai", ten_the_loai);
        return db.insert("THE_LOAI_SACH", null, values);
    }

    /**
     * Lấy các bản sao của đầu sách (`QUYEN_SACH`)
     */
    public List<Map<String, Object>> getBookCopies(int ma_sach) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> copies = new ArrayList<>();
        String sql = "SELECT q.*, s.tieu_de, s.tac_gia " +
                     "FROM QUYEN_SACH q " +
                     "JOIN SACH s ON q.ma_sach = s.ma_sach " +
                     "WHERE q.ma_sach = ? " +
                     "ORDER BY q.ma_quyen_sach";

        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(ma_sach)})) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_quyen", c.getInt(c.getColumnIndexOrThrow("ma_quyen")));
                map.put("ma_sach", c.getInt(c.getColumnIndexOrThrow("ma_sach")));
                map.put("ma_quyen_sach", c.getString(c.getColumnIndexOrThrow("ma_quyen_sach")));
                map.put("trang_thai", c.getString(c.getColumnIndexOrThrow("trang_thai")));
                map.put("vi_tri", c.getString(c.getColumnIndexOrThrow("vi_tri")));
                map.put("ghi_chu", c.getString(c.getColumnIndexOrThrow("ghi_chu")));
                map.put("ngay_nhap", c.getString(c.getColumnIndexOrThrow("ngay_nhap")));
                map.put("tieu_de", c.getString(c.getColumnIndexOrThrow("tieu_de")));
                map.put("tac_gia", c.getString(c.getColumnIndexOrThrow("tac_gia")));
                copies.add(map);
            }
        }
        return copies;
    }

    /**
     * Thêm một bản sao quyển sách vật lý mới
     */
    public long addBookCopy(int ma_sach, String vi_tri, String ghi_chu) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Đếm để lấy số quyển tiếp theo
            String countSql = "SELECT COUNT(*) FROM QUYEN_SACH WHERE ma_sach = ?";
            int count = 0;
            try (Cursor c = db.rawQuery(countSql, new String[]{String.valueOf(ma_sach)})) {
                if (c.moveToFirst()) {
                    count = c.getInt(0);
                }
            }
            int next_num = count + 1;
            String ma_quyen_sach = String.format(Locale.getDefault(), "S%04d-Q%03d", ma_sach, next_num);
            String ngay_nhap = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            ContentValues values = new ContentValues();
            values.put("ma_sach", ma_sach);
            values.put("ma_quyen_sach", ma_quyen_sach);
            values.put("trang_thai", "CO_SAN");
            values.put("vi_tri", vi_tri);
            values.put("ghi_chu", ghi_chu);
            values.put("ngay_nhap", ngay_nhap);
            long ma_quyen = db.insertOrThrow("QUYEN_SACH", null, values);

            // Cập nhật số lượng trong SACH_GIAY
            db.execSQL("UPDATE SACH_GIAY SET so_luong = so_luong + 1 WHERE ma_sach = ?", new Object[]{ma_sach});

            db.setTransactionSuccessful();
            return ma_quyen;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Cập nhật thông tin bản sao quyển sách
     */
    public boolean updateBookCopy(int ma_quyen, String trang_thai, String vi_tri, String ghi_chu) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (trang_thai != null) values.put("trang_thai", trang_thai);
        if (vi_tri != null) values.put("vi_tri", vi_tri);
        if (ghi_chu != null) values.put("ghi_chu", ghi_chu);

        int rows = db.update("QUYEN_SACH", values, "ma_quyen = ?", new String[]{String.valueOf(ma_quyen)});
        return rows > 0;
    }

    /**
     * Xóa một bản sao quyển sách
     */
    public void deleteBookCopy(int ma_quyen) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // 1. Kiểm tra trạng thái
        String query = "SELECT trang_thai, ma_sach FROM QUYEN_SACH WHERE ma_quyen = ?";
        String trang_thai = "";
        int ma_sach = 0;
        try (Cursor c = db.rawQuery(query, new String[]{String.valueOf(ma_quyen)})) {
            if (c.moveToFirst()) {
                trang_thai = c.getString(c.getColumnIndexOrThrow("trang_thai"));
                ma_sach = c.getInt(c.getColumnIndexOrThrow("ma_sach"));
            } else {
                throw new Exception("Không tìm thấy quyển sách!");
            }
        }

        if ("DANG_MUON".equals(trang_thai)) {
            throw new Exception("Không thể xóa quyển sách đang được mượn!");
        }

        db.beginTransaction();
        try {
            db.delete("QUYEN_SACH", "ma_quyen = ?", new String[]{String.valueOf(ma_quyen)});
            db.execSQL("UPDATE SACH_GIAY SET so_luong = so_luong - 1 WHERE ma_sach = ? AND so_luong > 0", new Object[]{ma_sach});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
