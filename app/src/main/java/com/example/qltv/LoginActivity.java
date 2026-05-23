package com.example.qltv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText editUsername, editPassword;
    private EditText editFullName, editAddress, editPhone, editEmail;
    private LinearLayout layoutRegisterExtra;
    private Button btnLogin, btnRegister;
    private TextView txtFormTitle, txtToggleForm;

    private UserRepository userRepository;
    private ExecutorService executorService;
    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userRepository = new UserRepository(this);
        executorService = Executors.newSingleThreadExecutor();

        // Kiểm tra xem đã đăng nhập chưa
        SharedPreferences pref = getSharedPreferences("QLTV_PREF", MODE_PRIVATE);
        if (pref.contains("userId")) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Ánh xạ View
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        editFullName = findViewById(R.id.editFullName);
        editAddress = findViewById(R.id.editAddress);
        editPhone = findViewById(R.id.editPhone);
        editEmail = findViewById(R.id.editEmail);

        layoutRegisterExtra = findViewById(R.id.layoutRegisterExtra);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        txtFormTitle = findViewById(R.id.txtFormTitle);
        txtToggleForm = findViewById(R.id.txtToggleForm);

        // Chuyển đổi trạng thái Đăng nhập / Đăng ký
        txtToggleForm.setOnClickListener(v -> toggleFormMode());

        // Đăng nhập
        btnLogin.setOnClickListener(v -> performLogin());

        // Đăng ký
        btnRegister.setOnClickListener(v -> performRegister());
    }

    private void toggleFormMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            txtFormTitle.setText("ĐĂNG NHẬP HỆ THỐNG");
            layoutRegisterExtra.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);
            btnRegister.setVisibility(View.GONE);
            txtToggleForm.setText("Chưa có tài khoản? Đăng ký đọc giả");
        } else {
            txtFormTitle.setText("ĐĂNG KÝ ĐỘC GIẢ");
            layoutRegisterExtra.setVisibility(View.VISIBLE);
            btnLogin.setVisibility(View.GONE);
            btnRegister.setVisibility(View.VISIBLE);
            txtToggleForm.setText("Đã có tài khoản? Đăng nhập ngay");
        }
    }

    private void performLogin() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            try {
                Map<String, Object> user = userRepository.authenticateUser(username, password);
                runOnUiThread(() -> {
                    if (user != null) {
                        // Lưu thông tin đăng nhập vào SharedPreferences
                        SharedPreferences.Editor editor = getSharedPreferences("QLTV_PREF", MODE_PRIVATE).edit();
                        editor.putInt("userId", (int) user.get("userId"));
                        editor.putString("username", (String) user.get("username"));
                        editor.putString("fullName", (String) user.get("fullName"));
                        editor.putString("role", (String) user.get("role"));
                        editor.apply();

                        Toast.makeText(LoginActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Tên đăng nhập hoặc mật khẩu sai!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Lỗi kết nối cơ sở dữ liệu!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void performRegister() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();
        String fullName = editFullName.getText().toString().trim();
        String address = editAddress.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();
        String email = editEmail.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty() || address.isEmpty() || phone.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Vui lòng điền đầy đủ các thông tin đăng ký!", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            try {
                userRepository.registerReader(username, password, fullName, address, phone, email);
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "Đăng ký thành công! Vui lòng đăng nhập.", Toast.LENGTH_LONG).show();
                    toggleFormMode();
                    editPassword.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Đăng ký thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}
