package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StaffProfileFragment extends Fragment {

    // View chính & Header
    private TextView txtHeaderTitle;
    private ImageView btnProfileBack;
    private ImageView btnLogoutMenu;
    private View layoutHome, layoutStatsView, layoutStaffMgmtView;

    // View Hồ sơ cá nhân
    private TextView txtProfileAvatar, txtProfileName, txtProfileUsername, badgeProfileRole;
    private TextView txtProfileCode, txtProfilePhone, txtProfileEmail, txtProfileAddress;
    private Button btnChangePassword;
    private CardView cardStats, cardStaffMgmt;

    // View Thống kê (từ fragment_statistics.xml)
    private TextView txtStatTotalBooks, txtStatTotalBorrows, txtStatOverdue, txtStatFines;
    private LinearLayout layoutCategoryStatsContainer, layoutTopBooksContainer;

    // View Quản lý nhân viên
    private EditText editSearchStaff;
    private TextView txtEmptyStaff;
    private RecyclerView recyclerViewStaff;
    private FloatingActionButton fabAddStaff;

    // Data Repositories & Threading
    private UserRepository userRepository;
    private BorrowRepository borrowRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private int currentUserId = 0;
    private String currentUserRole = "";
    private List<Map<String, Object>> staffList = new ArrayList<>();
    private StaffAdapter staffAdapter;
    private String currentSearchTerm = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_staff_profile, container, false);

        // Khởi tạo các đối tượng xử lý
        userRepository = new UserRepository(requireContext());
        borrowRepository = new BorrowRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        
        currentUserId = sharedPreferences.getInt("userId", 0);
        currentUserRole = sharedPreferences.getString("role", "");

        // 1. Ánh xạ Header
        txtHeaderTitle = view.findViewById(R.id.txtProfileHeaderTitle);
        btnProfileBack = view.findViewById(R.id.btnProfileBack);
        btnLogoutMenu = view.findViewById(R.id.btnStaffProfileLogoutMenu);
        layoutHome = view.findViewById(R.id.layoutStaffProfileHome);
        layoutStatsView = view.findViewById(R.id.layoutStaffProfileStatsView);
        layoutStaffMgmtView = view.findViewById(R.id.layoutStaffProfileStaffMgmtView);

        // 2. Ánh xạ Hồ sơ cá nhân
        txtProfileAvatar = view.findViewById(R.id.txtStaffProfileAvatar);
        txtProfileName = view.findViewById(R.id.txtStaffProfileName);
        txtProfileUsername = view.findViewById(R.id.txtStaffProfileUsername);
        badgeProfileRole = view.findViewById(R.id.badgeStaffProfileRole);
        txtProfileCode = view.findViewById(R.id.txtStaffProfileCode);
        txtProfilePhone = view.findViewById(R.id.txtStaffProfilePhone);
        txtProfileEmail = view.findViewById(R.id.txtStaffProfileEmail);
        txtProfileAddress = view.findViewById(R.id.txtStaffProfileAddress);
        btnChangePassword = view.findViewById(R.id.btnStaffProfileChangePassword);
        cardStats = view.findViewById(R.id.cardStaffProfileStats);
        cardStaffMgmt = view.findViewById(R.id.cardStaffProfileStaffMgmt);

        // 3. Ánh xạ Thống kê
        txtStatTotalBooks = view.findViewById(R.id.txtStatTotalBooks);
        txtStatTotalBorrows = view.findViewById(R.id.txtStatTotalBorrows);
        txtStatOverdue = view.findViewById(R.id.txtStatOverdue);
        txtStatFines = view.findViewById(R.id.txtStatFines);
        layoutCategoryStatsContainer = view.findViewById(R.id.layoutCategoryStatsContainer);
        layoutTopBooksContainer = view.findViewById(R.id.layoutTopBooksContainer);

        // 4. Ánh xạ Quản lý nhân viên
        editSearchStaff = view.findViewById(R.id.editSearchStaff);
        txtEmptyStaff = view.findViewById(R.id.txtEmptyStaff);
        recyclerViewStaff = view.findViewById(R.id.recyclerViewStaff);
        fabAddStaff = view.findViewById(R.id.fabAddStaff);

        recyclerViewStaff.setLayoutManager(new LinearLayoutManager(requireContext()));
        staffAdapter = new StaffAdapter();
        recyclerViewStaff.setAdapter(staffAdapter);

        // Cấu hình phân quyền
        if (!"ADMIN".equals(currentUserRole)) {
            cardStaffMgmt.setVisibility(View.GONE);
        }

        // Thiết lập sự kiện
        setupEvents();

        // Tải thông tin hồ sơ
        loadUserProfile();

        return view;
    }

    private void setupEvents() {
        // Nút quay lại
        btnProfileBack.setOnClickListener(v -> showPanel(PanelType.HOME));

        // Nút ba chấm Đăng xuất
        btnLogoutMenu.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), btnLogoutMenu);
            popup.getMenu().add("Đăng xuất");
            popup.setOnMenuItemClickListener(item -> {
                if ("Đăng xuất".equals(item.getTitle())) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.clear();
                    editor.apply();
                    Toast.makeText(requireContext(), "Đã đăng xuất tài khoản!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        // Click xem Thống kê
        cardStats.setOnClickListener(v -> {
            showPanel(PanelType.STATS);
            loadStatistics();
        });

        // Click xem Quản lý nhân viên (chỉ Admin)
        cardStaffMgmt.setOnClickListener(v -> {
            showPanel(PanelType.STAFF_MGMT);
            loadStaff();
        });

        // Nút đổi mật khẩu cá nhân
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // Tìm kiếm nhân viên
        editSearchStaff.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchTerm = s.toString();
                loadStaff();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Nút thêm nhân viên mới (FAB)
        fabAddStaff.setOnClickListener(v -> showAddStaffDialog());
    }

    private enum PanelType {
        HOME, STATS, STAFF_MGMT
    }

    private void showPanel(PanelType type) {
        if (type == PanelType.HOME) {
            layoutHome.setVisibility(View.VISIBLE);
            layoutStatsView.setVisibility(View.GONE);
            layoutStaffMgmtView.setVisibility(View.GONE);
            btnProfileBack.setVisibility(View.GONE);
            txtHeaderTitle.setText("HỒ SƠ CỦA BẠN");
        } else if (type == PanelType.STATS) {
            layoutHome.setVisibility(View.GONE);
            layoutStatsView.setVisibility(View.VISIBLE);
            layoutStaffMgmtView.setVisibility(View.GONE);
            btnProfileBack.setVisibility(View.VISIBLE);
            txtHeaderTitle.setText("BÁO CÁO THỐNG KÊ");
        } else {
            layoutHome.setVisibility(View.GONE);
            layoutStatsView.setVisibility(View.GONE);
            layoutStaffMgmtView.setVisibility(View.VISIBLE);
            btnProfileBack.setVisibility(View.VISIBLE);
            txtHeaderTitle.setText("QUẢN LÝ NHÂN VIÊN");
        }
    }

    private void loadUserProfile() {
        executorService.execute(() -> {
            Map<String, Object> profile = userRepository.getUserProfile(currentUserId);
            if (isAdded() && profile != null) {
                requireActivity().runOnUiThread(() -> {
                    String hoTen = (String) profile.get("ho_ten");
                    String user = sharedPreferences.getString("username", "user");
                    String phone = (String) profile.get("so_dt");
                    String email = (String) profile.get("email");
                    String address = (String) profile.get("dia_chi");
                    String code = (String) profile.get("ma_nhan_vien");

                    txtProfileAvatar.setText(hoTen.substring(0, 1).toUpperCase());
                    txtProfileName.setText(hoTen);
                    txtProfileUsername.setText("@" + user);
                    badgeProfileRole.setText(currentUserRole);

                    txtProfileCode.setText(code != null ? code : "ADMIN");
                    txtProfilePhone.setText(phone == null || phone.isEmpty() ? "Chưa có" : phone);
                    txtProfileEmail.setText(email == null || email.isEmpty() ? "Chưa có" : email);
                    txtProfileAddress.setText(address == null || address.isEmpty() ? "Chưa có" : address);
                });
            }
        });
    }

    /**
     * Dialog đổi mật khẩu cá nhân
     */
    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_staff, null);
        builder.setView(dv);

        TextView title = dv.findViewById(R.id.txtStaffDialogTitle);
        title.setText("ĐỔI MẬT KHẨU CỦA BẠN");

        EditText edtOld = dv.findViewById(R.id.edtStaffDialogUsername);
        edtOld.setHint("Mật khẩu hiện tại");
        edtOld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText edtNew = dv.findViewById(R.id.edtStaffDialogPassword);
        edtNew.setHint("Mật khẩu mới");
        edtNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Ẩn tất cả trường khác
        dv.findViewById(R.id.edtStaffDialogFullName).setVisibility(View.GONE);
        dv.findViewById(R.id.edtStaffDialogCode).setVisibility(View.GONE);
        dv.findViewById(R.id.edtStaffDialogPhone).setVisibility(View.GONE);
        dv.findViewById(R.id.edtStaffDialogEmail).setVisibility(View.GONE);
        dv.findViewById(R.id.edtStaffDialogAddress).setVisibility(View.GONE);

        Button btnSave = dv.findViewById(R.id.btnStaffDialogSave);
        btnSave.setText("CẬP NHẬT MẬT KHẨU");

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String oldPass = edtOld.getText().toString();
            String newPass = edtNew.getText().toString();

            if (oldPass.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ thông tin mật khẩu!", Toast.LENGTH_SHORT).show();
                return;
            }

            executorService.execute(() -> {
                boolean success = userRepository.changePassword(currentUserId, oldPass, newPass);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(requireContext(), "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(requireContext(), "Mật khẩu hiện tại không chính xác!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
    }

    /**
     * Đồng bộ logic hiển thị số liệu Thống Kê
     */
    private void loadStatistics() {
        executorService.execute(() -> {
            Map<String, Object> stats = borrowRepository.getLibraryStatistics();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    int totalBooks = stats.containsKey("total_books") ? (int) stats.get("total_books") : 0;
                    int totalBorrows = stats.containsKey("total_borrows") ? (int) stats.get("total_borrows") : 0;
                    int overdueCount = stats.containsKey("overdue_count") ? (int) stats.get("overdue_count") : 0;
                    double totalFines = stats.containsKey("total_fines") ? (double) stats.get("total_fines") : 0.0;

                    txtStatTotalBooks.setText(String.valueOf(totalBooks));
                    txtStatTotalBorrows.setText(String.valueOf(totalBorrows));
                    txtStatOverdue.setText(String.valueOf(overdueCount));
                    txtStatFines.setText(String.format(Locale.getDefault(), "%,.0f VND", totalFines));

                    // Điền cơ cấu
                    layoutCategoryStatsContainer.removeAllViews();
                    List<Map<String, Object>> categories = (List<Map<String, Object>>) stats.get("by_category");
                    if (categories != null) {
                        for (Map<String, Object> cat : categories) {
                            String name = (String) cat.get("category_name");
                            int count = (int) cat.get("count");

                            View catView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_stat_row, layoutCategoryStatsContainer, false);
                            TextView txtName = catView.findViewById(R.id.txtStatRowName);
                            TextView txtCount = catView.findViewById(R.id.txtStatRowCount);
                            ProgressBar pb = catView.findViewById(R.id.pbStatRowPercent);

                            txtName.setText(name);
                            txtCount.setText(count + " sách");

                            int percent = 0;
                            if (totalBooks > 0) {
                                percent = (int) Math.round(((double) count / totalBooks) * 100);
                            }
                            pb.setProgress(percent);
                            layoutCategoryStatsContainer.addView(catView);
                        }
                    }

                    // Điền Top 10 cuốn sách
                    layoutTopBooksContainer.removeAllViews();
                    List<Map<String, Object>> topBooks = (List<Map<String, Object>>) stats.get("most_borrowed");
                    if (topBooks != null) {
                        int rank = 1;
                        for (Map<String, Object> book : topBooks) {
                            String title = (String) book.get("tieu_de");
                            String author = (String) book.get("tac_gia");
                            int borrowCount = (int) book.get("borrow_count");

                            View bookView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_top_book_row, layoutTopBooksContainer, false);
                            TextView txtRank = bookView.findViewById(R.id.txtTopRank);
                            TextView txtTitle = bookView.findViewById(R.id.txtTopTitle);
                            TextView txtSub = bookView.findViewById(R.id.txtTopSub);
                            TextView txtCount = bookView.findViewById(R.id.txtTopCount);

                            txtRank.setText("#" + rank);
                            txtTitle.setText(title);
                            txtSub.setText(author);
                            txtCount.setText(borrowCount + " lượt");

                            if (rank == 1) {
                                txtRank.setTextColor(0xFFFFD700);
                            } else if (rank == 2) {
                                txtRank.setTextColor(0xFFC0C0C0);
                            } else if (rank == 3) {
                                txtRank.setTextColor(0xFFCD7F32);
                            } else {
                                txtRank.setTextColor(0xFFB0A8B9);
                            }

                            layoutTopBooksContainer.addView(bookView);
                            rank++;
                        }
                    }
                });
            }
        });
    }

    /**
     * Tải danh sách nhân viên phục vụ Admin
     */
    private void loadStaff() {
        executorService.execute(() -> {
            List<Map<String, Object>> list = userRepository.getAllStaff(currentSearchTerm);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    staffList.clear();
                    staffList.addAll(list);
                    staffAdapter.notifyDataSetChanged();

                    if (staffList.isEmpty()) {
                        txtEmptyStaff.setVisibility(View.VISIBLE);
                        recyclerViewStaff.setVisibility(View.GONE);
                    } else {
                        txtEmptyStaff.setVisibility(View.GONE);
                        recyclerViewStaff.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /**
     * Thêm nhân viên mới dialog
     */
    private void showAddStaffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_staff, null);
        builder.setView(view);

        EditText edtUser = view.findViewById(R.id.edtStaffDialogUsername);
        EditText edtPass = view.findViewById(R.id.edtStaffDialogPassword);
        EditText edtName = view.findViewById(R.id.edtStaffDialogFullName);
        EditText edtCode = view.findViewById(R.id.edtStaffDialogCode);
        EditText edtPhone = view.findViewById(R.id.edtStaffDialogPhone);
        EditText edtEmail = view.findViewById(R.id.edtStaffDialogEmail);
        EditText edtAddress = view.findViewById(R.id.edtStaffDialogAddress);
        Button btnSave = view.findViewById(R.id.btnStaffDialogSave);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String userStr = edtUser.getText().toString().trim();
            String passStr = edtPass.getText().toString();
            String nameStr = edtName.getText().toString().trim();
            String codeStr = edtCode.getText().toString().trim();
            String phoneStr = edtPhone.getText().toString().trim();
            String emailStr = edtEmail.getText().toString().trim();
            String addrStr = edtAddress.getText().toString().trim();

            if (userStr.isEmpty() || passStr.isEmpty() || nameStr.isEmpty() || codeStr.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng điền đủ: Đăng nhập, Mật khẩu, Họ tên và Mã nhân viên!", Toast.LENGTH_SHORT).show();
                return;
            }

            executorService.execute(() -> {
                try {
                    userRepository.addStaff(nameStr, addrStr, phoneStr, emailStr, codeStr, userStr, passStr);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Thêm nhân viên mới thành công!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadStaff();
                        });
                    }
                } catch (Exception e) {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }
            });
        });
    }

    /**
     * Sửa thông tin nhân viên dialog
     */
    private void showEditStaffDialog(Map<String, Object> staff) {
        int maNd = (int) staff.get("ma_nd");
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_staff, null);
        builder.setView(view);

        TextView txtTitle = view.findViewById(R.id.txtStaffDialogTitle);
        txtTitle.setText("SỬA THÔNG TIN NHÂN VIÊN");

        EditText edtUser = view.findViewById(R.id.edtStaffDialogUsername);
        EditText edtPass = view.findViewById(R.id.edtStaffDialogPassword);
        
        // Sửa thì ẩn tài khoản đăng nhập & mật khẩu (chỉ sửa thông tin cá nhân)
        edtUser.setVisibility(View.GONE);
        edtPass.setVisibility(View.GONE);

        EditText edtName = view.findViewById(R.id.edtStaffDialogFullName);
        EditText edtCode = view.findViewById(R.id.edtStaffDialogCode);
        EditText edtPhone = view.findViewById(R.id.edtStaffDialogPhone);
        EditText edtEmail = view.findViewById(R.id.edtStaffDialogEmail);
        EditText edtAddress = view.findViewById(R.id.edtStaffDialogAddress);
        Button btnSave = view.findViewById(R.id.btnStaffDialogSave);

        edtName.setText((String) staff.get("ho_ten"));
        edtCode.setText((String) staff.get("ma_nhan_vien"));
        edtCode.setEnabled(false); // Mã nhân viên không cho đổi
        edtPhone.setText((String) staff.get("so_dt"));
        edtEmail.setText((String) staff.get("email"));
        edtAddress.setText((String) staff.get("dia_chi"));

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String nameStr = edtName.getText().toString().trim();
            String phoneStr = edtPhone.getText().toString().trim();
            String emailStr = edtEmail.getText().toString().trim();
            String addrStr = edtAddress.getText().toString().trim();

            if (nameStr.isEmpty()) {
                Toast.makeText(requireContext(), "Họ và tên không được để trống!", Toast.LENGTH_SHORT).show();
                return;
            }

            executorService.execute(() -> {
                boolean success = userRepository.updateProfile(maNd, nameStr, addrStr, phoneStr, emailStr);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(requireContext(), "Cập nhật nhân viên thành công!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadStaff();
                        } else {
                            Toast.makeText(requireContext(), "Cập nhật thất bại!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
    }

    /**
     * Adapter cho RecyclerView Nhân viên
     */
    private class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {

        @NonNull
        @Override
        public StaffViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_staff, parent, false);
            return new StaffViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StaffViewHolder holder, int position) {
            Map<String, Object> staff = staffList.get(position);
            int maNd = (int) staff.get("ma_nd");
            String hoTen = (String) staff.get("ho_ten");
            String phone = (String) staff.get("so_dt");
            String email = (String) staff.get("email");
            String code = (String) staff.get("ma_nhan_vien");

            holder.txtName.setText(hoTen);
            holder.txtCodePhone.setText("MÃ NV: " + code + " | SĐT: " + (phone == null || phone.isEmpty() ? "Chưa có" : phone));
            holder.txtEmail.setText(email == null || email.isEmpty() ? "Chưa có email" : email);

            // Click sửa
            holder.btnEdit.setOnClickListener(v -> showEditStaffDialog(staff));

            // Click xóa
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle("Xóa Nhân Viên")
                    .setMessage("Bạn có chắc chắn muốn xóa nhân viên \"" + hoTen + "\"? Hành động này sẽ xóa tài khoản tương ứng.")
                    .setPositiveButton("Xóa", (dialogInterface, i) -> {
                        executorService.execute(() -> {
                            boolean deleted = userRepository.deleteStaff(maNd);
                            if (isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    if (deleted) {
                                        Toast.makeText(requireContext(), "Đã xóa nhân viên!", Toast.LENGTH_SHORT).show();
                                        loadStaff();
                                    } else {
                                        Toast.makeText(requireContext(), "Không thể xóa nhân viên này!", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
            });
        }

        @Override
        public int getItemCount() {
            return staffList.size();
        }

        class StaffViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtCodePhone, txtEmail;
            ImageView btnEdit, btnDelete;

            public StaffViewHolder(@NonNull View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txtStaffListName);
                txtCodePhone = itemView.findViewById(R.id.txtStaffListCodePhone);
                txtEmail = itemView.findViewById(R.id.txtStaffListEmail);
                btnEdit = itemView.findViewById(R.id.btnEditStaff);
                btnDelete = itemView.findViewById(R.id.btnDeleteStaff);
            }
        }
    }
}
