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

public class UserRepository {

    private final DatabaseOpenHelper dbHelper;

    public UserRepository(Context context) {
        this.dbHelper = new DatabaseOpenHelper(context);
        this.dbHelper.initializeDatabase();
    }

    /**
     * Xác thực tài khoản người dùng đăng nhập
     */
    public Map<String, Object> authenticateUser(String username, String password) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String hashedInputPassword = HashUtils.hashPassword(password);
        if (hashedInputPassword == null) return null;

        String sqlQuery = "SELECT tk.ten_tk, tk.ma_nd, nd.ho_ten, nd.loai_nguoi_dung " +
                          "FROM TAI_KHOAN tk " +
                          "JOIN NGUOI_DUNG nd ON tk.ma_nd = nd.ma_nd " +
                          "WHERE LOWER(tk.ten_tk) = LOWER(?) AND tk.mat_khau = ?";

        Map<String, Object> userData = null;

        try (Cursor cursor = db.rawQuery(sqlQuery, new String[]{username, hashedInputPassword})) {
            if (cursor.moveToFirst()) {
                userData = new HashMap<>();
                userData.put("username", cursor.getString(cursor.getColumnIndexOrThrow("ten_tk")));
                userData.put("userId", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd")));
                userData.put("fullName", cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")));
                userData.put("role", cursor.getString(cursor.getColumnIndexOrThrow("loai_nguoi_dung")));
            }
        }
        return userData;
    }

    /**
     * Đăng ký độc giả mới trực tiếp từ giao diện Mobile
     */
    public long registerReader(String username, String password, String ho_ten, String dia_chi, String so_dt, String email) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // Kiểm tra username đã tồn tại chưa
        String checkQuery = "SELECT 1 FROM TAI_KHOAN WHERE LOWER(ten_tk) = LOWER(?)";
        try (Cursor cursor = db.rawQuery(checkQuery, new String[]{username})) {
            if (cursor.getCount() > 0) {
                throw new Exception("Tên đăng nhập đã tồn tại!");
            }
        }

        db.beginTransaction();
        try {
            // 1. Chèn vào NGUOI_DUNG
            ContentValues userValues = new ContentValues();
            userValues.put("ho_ten", ho_ten);
            userValues.put("dia_chi", dia_chi);
            userValues.put("so_dt", so_dt);
            userValues.put("email", email);
            userValues.put("loai_nguoi_dung", "DOC_GIA");
            long ma_nd = db.insertOrThrow("NGUOI_DUNG", null, userValues);

            // 2. Sinh mã độc giả dạng DGXXXX và chèn vào DOC_GIA
            String ma_doc_gia = String.format(Locale.getDefault(), "DG%04d", ma_nd);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String ngay_dk = sdf.format(new Date());

            ContentValues readerValues = new ContentValues();
            readerValues.put("ma_nd", ma_nd);
            readerValues.put("ma_doc_gia", ma_doc_gia);
            readerValues.put("ngay_dk", ngay_dk);
            db.insertOrThrow("DOC_GIA", null, readerValues);

            // 3. Tạo thẻ độc giả THE_DOC_GIA (hạn 1 năm)
            Calendar cal = Calendar.getInstance();
            String ngay_cap = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 365);
            String ngay_het_han = sdf.format(cal.getTime());

            ContentValues cardValues = new ContentValues();
            cardValues.put("ma_nd_doc_gia", ma_nd);
            cardValues.put("ngay_cap", ngay_cap);
            cardValues.put("ngay_het_han", ngay_het_han);
            cardValues.put("trang_thai_the", "HOAT_DONG");
            db.insertOrThrow("THE_DOC_GIA", null, cardValues);

            // 4. Tạo tài khoản TAI_KHOAN
            String hashedPw = HashUtils.hashPassword(password);
            ContentValues accountValues = new ContentValues();
            accountValues.put("ten_tk", username.toLowerCase(Locale.ROOT));
            accountValues.put("mat_khau", hashedPw);
            accountValues.put("ma_nd", ma_nd);
            db.insertOrThrow("TAI_KHOAN", null, accountValues);

            db.setTransactionSuccessful();
            return ma_nd;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Đổi mật khẩu người dùng
     */
    public boolean changePassword(int ma_nd, String oldPassword, String newPassword) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String oldHashed = HashUtils.hashPassword(oldPassword);
        String newHashed = HashUtils.hashPassword(newPassword);

        // Kiểm tra mật khẩu cũ
        String checkQuery = "SELECT 1 FROM TAI_KHOAN WHERE ma_nd = ? AND mat_khau = ?";
        try (Cursor cursor = db.rawQuery(checkQuery, new String[]{String.valueOf(ma_nd), oldHashed})) {
            if (cursor.getCount() == 0) {
                return false; // Mật khẩu cũ sai
            }
        }

        // Cập nhật mật khẩu mới
        ContentValues values = new ContentValues();
        values.put("mat_khau", newHashed);
        int rows = db.update("TAI_KHOAN", values, "ma_nd = ?", new String[]{String.valueOf(ma_nd)});
        return rows > 0;
    }

    /**
     * Thêm nhân viên mới (chỉ Admin)
     */
    public long addStaff(String ho_ten, String dia_chi, String so_dt, String email, String ma_nhan_vien, String username, String password) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Kiểm tra username đã tồn tại chưa
        String checkQuery = "SELECT 1 FROM TAI_KHOAN WHERE LOWER(ten_tk) = LOWER(?)";
        try (Cursor cursor = db.rawQuery(checkQuery, new String[]{username})) {
            if (cursor.getCount() > 0) {
                throw new Exception("Tên đăng nhập đã tồn tại!");
            }
        }

        db.beginTransaction();
        try {
            ContentValues userValues = new ContentValues();
            userValues.put("ho_ten", ho_ten);
            userValues.put("dia_chi", dia_chi);
            userValues.put("so_dt", so_dt);
            userValues.put("email", email);
            userValues.put("loai_nguoi_dung", "NHAN_VIEN");
            long ma_nd = db.insertOrThrow("NGUOI_DUNG", null, userValues);

            ContentValues staffValues = new ContentValues();
            staffValues.put("ma_nd", ma_nd);
            staffValues.put("ma_nhan_vien", ma_nhan_vien);
            db.insertOrThrow("NHAN_VIEN", null, staffValues);

            String hashedPw = HashUtils.hashPassword(password);
            ContentValues accountValues = new ContentValues();
            accountValues.put("ten_tk", username.toLowerCase(Locale.ROOT));
            accountValues.put("mat_khau", hashedPw);
            accountValues.put("ma_nd", ma_nd);
            db.insertOrThrow("TAI_KHOAN", null, accountValues);

            db.setTransactionSuccessful();
            return ma_nd;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Lấy danh sách toàn bộ nhân viên (chỉ Admin)
     */
    public List<Map<String, Object>> getAllStaff(String searchTerm) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Map<String, Object>> list = new ArrayList<>();

        String sqlQuery = "SELECT nd.*, nv.ma_nhan_vien " +
                          "FROM NGUOI_DUNG nd " +
                          "JOIN NHAN_VIEN nv ON nd.ma_nd = nv.ma_nd " +
                          "WHERE nd.loai_nguoi_dung = 'NHAN_VIEN'";

        String[] params = null;
        if (searchTerm != null && !searchTerm.isEmpty()) {
            sqlQuery += " AND (nd.ho_ten LIKE ? OR nv.ma_nhan_vien LIKE ?)";
            params = new String[]{"%" + searchTerm + "%", "%" + searchTerm + "%"};
        }

        sqlQuery += " ORDER BY nd.ma_nd DESC";

        try (Cursor cursor = db.rawQuery(sqlQuery, params)) {
            while (cursor.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_nd", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd")));
                map.put("ho_ten", cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")));
                map.put("dia_chi", cursor.getString(cursor.getColumnIndexOrThrow("dia_chi")));
                map.put("so_dt", cursor.getString(cursor.getColumnIndexOrThrow("so_dt")));
                map.put("email", cursor.getString(cursor.getColumnIndexOrThrow("email")));
                map.put("ma_nhan_vien", cursor.getString(cursor.getColumnIndexOrThrow("ma_nhan_vien")));
                list.add(map);
            }
        }
        return list;
    }

    /**
     * Cập nhật thông tin nhân viên hoặc người dùng
     */
    public boolean updateProfile(int ma_nd, String ho_ten, String dia_chi, String so_dt, String email) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ho_ten", ho_ten);
        values.put("dia_chi", dia_chi);
        values.put("so_dt", so_dt);
        values.put("email", email);
        int rows = db.update("NGUOI_DUNG", values, "ma_nd = ?", new String[]{String.valueOf(ma_nd)});
        return rows > 0;
    }

    /**
     * Xóa nhân viên (chỉ Admin)
     */
    public boolean deleteStaff(int ma_nd) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Do có CASCADE constraint trong DB, xóa NGUOI_DUNG sẽ tự động xóa trong NHAN_VIEN và TAI_KHOAN
        int rows = db.delete("NGUOI_DUNG", "ma_nd = ?", new String[]{String.valueOf(ma_nd)});
        return rows > 0;
    }

    /**
     * Lấy thông tin cá nhân của một người dùng
     */
    public Map<String, Object> getUserProfile(int ma_nd) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT nd.*, dg.ma_doc_gia, nv.ma_nhan_vien, " +
                     "tdg.ma_the, tdg.ngay_cap, tdg.ngay_het_han, tdg.trang_thai_the " +
                     "FROM NGUOI_DUNG nd " +
                     "LEFT JOIN DOC_GIA dg ON nd.ma_nd = dg.ma_nd " +
                     "LEFT JOIN NHAN_VIEN nv ON nd.ma_nd = nv.ma_nd " +
                     "LEFT JOIN THE_DOC_GIA tdg ON dg.ma_nd = tdg.ma_nd_doc_gia " +
                     "WHERE nd.ma_nd = ?";

        Map<String, Object> profile = null;
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(ma_nd)})) {
            if (cursor.moveToFirst()) {
                profile = new HashMap<>();
                profile.put("ma_nd", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd")));
                profile.put("ho_ten", cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")));
                profile.put("dia_chi", cursor.getString(cursor.getColumnIndexOrThrow("dia_chi")));
                profile.put("so_dt", cursor.getString(cursor.getColumnIndexOrThrow("so_dt")));
                profile.put("email", cursor.getString(cursor.getColumnIndexOrThrow("email")));
                profile.put("loai_nguoi_dung", cursor.getString(cursor.getColumnIndexOrThrow("loai_nguoi_dung")));
                
                String maDocGia = cursor.getString(cursor.getColumnIndexOrThrow("ma_doc_gia"));
                if (maDocGia != null) profile.put("ma_doc_gia", maDocGia);
                
                String maNhanVien = cursor.getString(cursor.getColumnIndexOrThrow("ma_nhan_vien"));
                if (maNhanVien != null) profile.put("ma_nhan_vien", maNhanVien);

                int maThe = cursor.getInt(cursor.getColumnIndexOrThrow("ma_the"));
                if (!cursor.isNull(cursor.getColumnIndexOrThrow("ma_the"))) {
                    profile.put("ma_the", maThe);
                    profile.put("ngay_cap", cursor.getString(cursor.getColumnIndexOrThrow("ngay_cap")));
                    profile.put("ngay_het_han", cursor.getString(cursor.getColumnIndexOrThrow("ngay_het_han")));
                    profile.put("trang_thai_the", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_the")));
                }
            }
        }
        return profile;
    }
}
