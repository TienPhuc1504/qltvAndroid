package com.example.qltv;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DatabaseOpenHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseOpenHelper";
    private static final String DATABASE_NAME = "library.db";
    private static final int DATABASE_VERSION = 1;

    private final Context context;
    private final File databasePath;

    public DatabaseOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        this.databasePath = context.getDatabasePath(DATABASE_NAME);
    }

    /**
     * Kiểm tra database đã tồn tại chưa, nếu chưa thì tiến hành sao chép từ assets.
     */
    public void initializeDatabase() {
        if (!databasePath.exists()) {
            File parentDir = databasePath.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try {
                copyDatabaseFromAssets();
                Log.d(TAG, "Sao chép cơ sở dữ liệu từ Assets thành công!");
            } catch (IOException e) {
                Log.e(TAG, "Lỗi nghiêm trọng khi sao chép cơ sở dữ liệu!", e);
                throw new RuntimeException("Không thể khởi tạo cơ sở dữ liệu", e);
            }
        } else {
            Log.d(TAG, "Cơ sở dữ liệu đã tồn tại trên thiết bị. Không cần sao chép.");
        }
    }

    private void copyDatabaseFromAssets() throws IOException {
        try (InputStream inputStream = context.getAssets().open(DATABASE_NAME);
             OutputStream outputStream = new FileOutputStream(databasePath)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // Bắt buộc kích hoạt ràng buộc khóa ngoại (Foreign Keys)
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Không tạo bảng mới vì dùng database đã có sẵn từ Assets
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nâng cấp schema nếu cần
    }
}
