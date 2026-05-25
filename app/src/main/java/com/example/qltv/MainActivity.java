package com.example.qltv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.View;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public void setBottomNavigationVisibility(int visibility) {
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(visibility);
        }
    }

    private BottomNavigationView bottomNavigationView;
    private SharedPreferences sharedPreferences;
    private ExecutorService executorService;
    private String userRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("QLTV_PREF", MODE_PRIVATE);
        executorService = Executors.newSingleThreadExecutor();

        // 1. Kiểm tra đăng nhập
        if (!sharedPreferences.contains("userId")) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userRole = sharedPreferences.getString("role", "");

        // 2. Cấu hình Toolbar (Bỏ theo yêu cầu)

        // 3. Cấu hình Bottom Navigation động theo vai trò
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        configureBottomNavigation();

        // 4. Chạy tác vụ quét nền kiểm tra trễ hạn/quá hạn ngay khi khởi động
        runBackgroundChecks();
    }

    private void configureBottomNavigation() {
        bottomNavigationView.getMenu().clear();
        
        if ("ADMIN".equals(userRole) || "NHAN_VIEN".equals(userRole)) {
            // Nạp menu cho Nhân viên/Admin
            bottomNavigationView.inflateMenu(R.menu.menu_staff);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_staff_books) {
                    replaceFragment(new BookManagementFragment());
                    return true;
                } else if (itemId == R.id.nav_staff_readers) {
                    replaceFragment(new ReaderManagementFragment());
                    return true;
                } else if (itemId == R.id.nav_staff_borrows) {
                    replaceFragment(new BorrowManagementFragment());
                    return true;
                } else if (itemId == R.id.nav_staff_requests) {
                    replaceFragment(new RequestManagementFragment());
                    return true;
                } else if (itemId == R.id.nav_staff_profile) {
                    replaceFragment(new StaffProfileFragment());
                    return true;
                }
                return false;
            });
            // Mặc định load tab sách
            bottomNavigationView.setSelectedItemId(R.id.nav_staff_books);
        } else {
            // Nạp menu cho Độc giả
            bottomNavigationView.inflateMenu(R.menu.menu_reader);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_reader_search) {
                    replaceFragment(new ReaderSearchFragment());
                    return true;
                } else if (itemId == R.id.nav_reader_borrows) {
                    replaceFragment(new ReaderBorrowsFragment());
                    return true;
                } else if (itemId == R.id.nav_reader_requests) {
                    replaceFragment(new ReaderRequestsFragment());
                    return true;
                } else if (itemId == R.id.nav_reader_profile) {
                    replaceFragment(new ReaderAccountFragment());
                    return true;
                }
                return false;
            });
            // Mặc định load tab tìm kiếm
            bottomNavigationView.setSelectedItemId(R.id.nav_reader_search);
        }
    }

    private void replaceFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    private void runBackgroundChecks() {
        executorService.execute(() -> {
            try {
                NotificationRepository notifRepo = new NotificationRepository(MainActivity.this);
                // Quét cập nhật tự động hủy quá hạn lấy sách & nhắc nhở trễ hạn mượn
                notifRepo.checkAndNotifyDueBooks();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
