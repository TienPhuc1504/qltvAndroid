package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderAccountFragment extends Fragment {

    // View chính & Header
    private TextView txtHeaderTitle;
    private ImageView btnProfileBack, btnLogoutMenu;
    private View layoutHome, layoutNotifsView;

    // View Hồ sơ & Member Card
    private TextView txtCardName, txtCardCode, txtCardExpiry;
    private TextView txtProfileCode, txtProfilePhone, txtProfileEmail, txtProfileAddress;
    private CardView cardNotifs;

    // View Avatar & Stats mới
    private CardView cardReaderAvatar;
    private ImageView imgReaderAvatar, btnEditReaderProfile;
    private TextView txtReaderProfileName, txtReaderProfileCodeLabel, txtStatBorrowedBooks;

    // View Hộp thư (từ fragment_reader_notifications.xml)
    private TextView btnMarkAllRead;
    private RecyclerView recyclerViewNotifs;
    private TextView txtEmptyNotif;

    // Data Repositories & Threading
    private UserRepository userRepository;
    private NotificationRepository notificationRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private int currentReaderId = 0;
    private List<Map<String, Object>> notifsList = new ArrayList<>();
    private NotifAdapter notifAdapter;

    // 6 mẫu avatar
    private final int[] avatarColors = new int[]{
        0xFF757575, // Medium Grey
        0xFF424242, // Dark Grey
        0xFFBDBDBD, // Light Grey
        0xFF4CAF50, // Very Light Grey
        0xFF212121, // Very Dark Grey
        0xFFEEEEEE  // White-Grey
    };

    private final int[] avatarIcons = new int[]{
        android.R.drawable.ic_menu_compass,
        android.R.drawable.ic_menu_myplaces,
        android.R.drawable.ic_menu_gallery,
        android.R.drawable.ic_menu_today,
        android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_help
    };

    private int tempSelectedAvatarIndex = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reader_account, container, false);

        // Khởi tạo các đối tượng xử lý
        userRepository = new UserRepository(requireContext());
        notificationRepository = new NotificationRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentReaderId = sharedPreferences.getInt("userId", 0);

        // 1. Ánh xạ Header
        txtHeaderTitle = view.findViewById(R.id.txtProfileHeaderTitle);
        btnProfileBack = view.findViewById(R.id.btnProfileBack);
        btnLogoutMenu = view.findViewById(R.id.btnReaderProfileLogoutMenu);
        layoutHome = view.findViewById(R.id.layoutReaderAccountHome);
        layoutNotifsView = view.findViewById(R.id.layoutReaderAccountNotifsView);

        // 2. Ánh xạ Thẻ độc giả & Chi tiết hồ sơ
        txtCardName = view.findViewById(R.id.txtProfileCardName);
        txtCardCode = view.findViewById(R.id.txtProfileCardCode);
        txtCardExpiry = view.findViewById(R.id.txtProfileCardExpiry);

        txtProfileCode = view.findViewById(R.id.txtProfileCode);
        txtProfilePhone = view.findViewById(R.id.txtProfilePhone);
        txtProfileEmail = view.findViewById(R.id.txtProfileEmail);
        txtProfileAddress = view.findViewById(R.id.txtProfileAddress);

        cardNotifs = view.findViewById(R.id.cardReaderProfileNotifs);

        // Ánh xạ Avatar & Stats mới
        cardReaderAvatar = view.findViewById(R.id.cardReaderAvatar);
        imgReaderAvatar = view.findViewById(R.id.imgReaderAvatar);
        btnEditReaderProfile = view.findViewById(R.id.btnEditReaderProfile);
        txtReaderProfileName = view.findViewById(R.id.txtReaderProfileName);
        txtReaderProfileCodeLabel = view.findViewById(R.id.txtReaderProfileCodeLabel);
        txtStatBorrowedBooks = view.findViewById(R.id.txtStatBorrowedBooks);

        // 3. Ánh xạ Hộp thư
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead);
        recyclerViewNotifs = view.findViewById(R.id.recyclerViewNotifs);
        txtEmptyNotif = view.findViewById(R.id.txtEmptyNotif);

        // Ẩn tiêu đề "THÔNG BÁO CỦA TÔI" trong layout included để không bị lặp tiêu đề
        View includedNotifTitle = view.findViewById(R.id.txtNotifTitle);
        if (includedNotifTitle != null) {
            includedNotifTitle.setVisibility(View.GONE);
        }

        recyclerViewNotifs.setLayoutManager(new LinearLayoutManager(requireContext()));
        notifAdapter = new NotifAdapter();
        recyclerViewNotifs.setAdapter(notifAdapter);

        // Thiết lập các sự kiện
        setupEvents();

        // Tải hồ sơ
        loadUserProfile();

        return view;
    }

    private void setupEvents() {
        // Quay lại
        btnProfileBack.setOnClickListener(v -> showPanel(PanelType.HOME));

        // Click xem Hộp thư
        cardNotifs.setOnClickListener(v -> {
            showPanel(PanelType.NOTIFS);
            loadNotifications();
        });

        // Click sửa hồ sơ hoặc chạm vào avatar
        btnEditReaderProfile.setOnClickListener(v -> showEditProfileDialog());
        cardReaderAvatar.setOnClickListener(v -> showEditProfileDialog());

        // Đọc tất cả thông báo
        btnMarkAllRead.setOnClickListener(v -> markAllRead());

        // Click ba chấm Đăng xuất
        btnLogoutMenu.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), btnLogoutMenu);
            popup.getMenu().add("Đăng xuất");
            popup.setOnMenuItemClickListener(item -> {
                if ("Đăng xuất".equals(item.getTitle())) {
                    logout();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private enum PanelType {
        HOME, NOTIFS
    }

    private void showPanel(PanelType type) {
        if (type == PanelType.HOME) {
            layoutHome.setVisibility(View.VISIBLE);
            layoutNotifsView.setVisibility(View.GONE);
            btnProfileBack.setVisibility(View.GONE);
            txtHeaderTitle.setText("TÀI KHOẢN CỦA BẠN");
        } else {
            layoutHome.setVisibility(View.GONE);
            layoutNotifsView.setVisibility(View.VISIBLE);
            btnProfileBack.setVisibility(View.VISIBLE);
            txtHeaderTitle.setText("HỘP THƯ THÔNG BÁO");
        }
    }

    private void loadUserProfile() {
        executorService.execute(() -> {
            Map<String, Object> profile = userRepository.getUserProfile(currentReaderId);
            int borrowedCount = userRepository.getBorrowedBooksCount(currentReaderId);
            if (isAdded() && profile != null) {
                requireActivity().runOnUiThread(() -> {
                    String fullName = (String) profile.get("ho_ten");
                    String code = (String) profile.get("ma_doc_gia");
                    String emailStr = (String) profile.get("email");
                    String phoneStr = (String) profile.get("so_dt");
                    String addressStr = (String) profile.get("dia_chi");
                    String expiry = (String) profile.get("ngay_het_han");

                    txtCardName.setText(fullName.toUpperCase());
                    txtCardCode.setText("MÃ ĐG: " + (code != null ? code : "Đang chờ cấp"));
                    txtCardExpiry.setText("HẠN THẺ: " + (expiry != null ? expiry : "Chưa kích hoạt"));

                    txtProfileCode.setText(code != null ? code : "Đang chờ cấp");
                    txtProfilePhone.setText(phoneStr == null || phoneStr.isEmpty() ? "Chưa có" : phoneStr);
                    txtProfileEmail.setText(emailStr == null || emailStr.isEmpty() ? "Chưa có" : emailStr);
                    txtProfileAddress.setText(addressStr == null || addressStr.isEmpty() ? "Chưa có" : addressStr);

                    // Tải Avatar từ SharedPreferences
                    int avatarIdx = sharedPreferences.getInt("avatar_index_reader_" + currentReaderId, 1);
                    cardReaderAvatar.setCardBackgroundColor(avatarColors[avatarIdx]);
                    imgReaderAvatar.setImageResource(avatarIcons[avatarIdx]);

                    // Gán tên, mã và chỉ số
                    txtReaderProfileName.setText(fullName);
                    txtReaderProfileCodeLabel.setText(code != null ? code : "Đang chờ cấp");
                    txtStatBorrowedBooks.setText(String.valueOf(borrowedCount));
                });
            }
        });
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_reader_profile, null);
        builder.setView(dialogView);

        EditText edtPhone = dialogView.findViewById(R.id.edtEditProfilePhone);
        EditText edtEmail = dialogView.findViewById(R.id.edtEditProfileEmail);
        EditText edtAddress = dialogView.findViewById(R.id.edtEditProfileAddress);
        Button btnChangePass = dialogView.findViewById(R.id.btnEditProfileChangePassword);
        Button btnCancel = dialogView.findViewById(R.id.btnEditProfileCancel);
        Button btnSave = dialogView.findViewById(R.id.btnEditProfileSave);

        // Nạp dữ liệu hiện tại
        edtPhone.setText(txtProfilePhone.getText().toString().equals("Chưa có") ? "" : txtProfilePhone.getText().toString());
        edtEmail.setText(txtProfileEmail.getText().toString().equals("Chưa có") ? "" : txtProfileEmail.getText().toString());
        edtAddress.setText(txtProfileAddress.getText().toString().equals("Chưa có") ? "" : txtProfileAddress.getText().toString());

        // Avatar selector
        final CardView[] avatarBtns = new CardView[6];
        avatarBtns[0] = dialogView.findViewById(R.id.btnAvatar0);
        avatarBtns[1] = dialogView.findViewById(R.id.btnAvatar1);
        avatarBtns[2] = dialogView.findViewById(R.id.btnAvatar2);
        avatarBtns[3] = dialogView.findViewById(R.id.btnAvatar3);
        avatarBtns[4] = dialogView.findViewById(R.id.btnAvatar4);
        avatarBtns[5] = dialogView.findViewById(R.id.btnAvatar5);

        // Lấy index hiện tại
        tempSelectedAvatarIndex = sharedPreferences.getInt("avatar_index_reader_" + currentReaderId, 1);
        
        // Hàm highlight avatar được chọn
        highlightSelectedAvatar(avatarBtns, tempSelectedAvatarIndex);

        // Thêm click listener cho các avatar mẫu
        for (int i = 0; i < 6; i++) {
            final int index = i;
            avatarBtns[i].setOnClickListener(v -> {
                tempSelectedAvatarIndex = index;
                highlightSelectedAvatar(avatarBtns, tempSelectedAvatarIndex);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        // Nút Hủy
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Nút Đổi mật khẩu tài khoản
        btnChangePass.setOnClickListener(v -> {
            dialog.dismiss();
            showChangePasswordDialog();
        });

        // Nút Lưu thay đổi
        btnSave.setOnClickListener(v -> {
            String newPhone = edtPhone.getText().toString().trim();
            String newEmail = edtEmail.getText().toString().trim();
            String newAddress = edtAddress.getText().toString().trim();
            String currentName = txtReaderProfileName.getText().toString(); // giữ nguyên tên

            executorService.execute(() -> {
                boolean success = userRepository.updateProfile(currentReaderId, currentName, newAddress, newPhone, newEmail);
                if (success) {
                    // Lưu avatar vào SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putInt("avatar_index_reader_" + currentReaderId, tempSelectedAvatarIndex);
                    editor.apply();

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Cập nhật hồ sơ thành công!", Toast.LENGTH_SHORT).show();
                            loadUserProfile();
                            dialog.dismiss();
                        });
                    }
                } else {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Cập nhật hồ sơ thất bại!", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });
    }

    private void highlightSelectedAvatar(CardView[] avatarBtns, int selectedIndex) {
        for (int i = 0; i < avatarBtns.length; i++) {
            if (i == selectedIndex) {
                avatarBtns[i].setAlpha(1.0f);
                avatarBtns[i].setCardElevation(8f);
            } else {
                avatarBtns[i].setAlpha(0.4f);
                avatarBtns[i].setCardElevation(0f);
            }
        }
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dv = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_staff, null);
        builder.setView(dv);

        TextView title = dv.findViewById(R.id.txtStaffDialogTitle);
        title.setText("ĐỔI MẬT KHẨU CỦA BẠN");

        EditText edtOld = dv.findViewById(R.id.edtStaffDialogUsername);
        edtOld.setHint("Mật khẩu hiện tại");
        edtOld.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText edtNew = dv.findViewById(R.id.edtStaffDialogPassword);
        edtNew.setHint("Mật khẩu mới");
        edtNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Ẩn các trường không liên quan
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
                boolean success = userRepository.changePassword(currentReaderId, oldPass, newPass);
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

    private void loadNotifications() {
        executorService.execute(() -> {
            notifsList = notificationRepository.getNotifications(currentReaderId, 50, false);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    notifAdapter.notifyDataSetChanged();
                    if (notifsList.isEmpty()) {
                        txtEmptyNotif.setVisibility(View.VISIBLE);
                        recyclerViewNotifs.setVisibility(View.GONE);
                    } else {
                        txtEmptyNotif.setVisibility(View.GONE);
                        recyclerViewNotifs.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void markAllRead() {
        executorService.execute(() -> {
            notificationRepository.markAllNotificationsAsRead(currentReaderId);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Đã đánh dấu đọc tất cả thông báo!", Toast.LENGTH_SHORT).show();
                    loadNotifications();
                });
            }
        });
    }

    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        Toast.makeText(requireContext(), "Đã đăng xuất tài khoản!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    /**
     * Adapter cho RecyclerView Thông báo
     */
    private class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.NotifViewHolder> {

        @NonNull
        @Override
        public NotifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new NotifViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotifViewHolder holder, int position) {
            Map<String, Object> n = notifsList.get(position);
            int maThongBao = (int) n.get("ma_thong_bao");
            String title = (String) n.get("tieu_de");
            String body = (String) n.get("noi_dung");
            String date = (String) n.get("ngay_tao");
            int daDoc = (int) n.get("da_doc");

            holder.txtTitle.setText(title);
            holder.txtBody.setText(body);
            holder.txtDate.setText(date);

            if (daDoc == 0) {
                holder.viewDot.setVisibility(View.VISIBLE);
            } else {
                holder.viewDot.setVisibility(View.GONE);
            }

            // Đánh dấu đã đọc khi chạm vào thông báo
            holder.itemView.setOnClickListener(v -> {
                if (daDoc == 0) {
                    executorService.execute(() -> {
                        notificationRepository.markNotificationAsRead(maThongBao);
                        requireActivity().runOnUiThread(ReaderAccountFragment.this::loadNotifications);
                    });
                }
            });

            // Nút xóa thông báo
            holder.btnDelete.setOnClickListener(v -> {
                executorService.execute(() -> {
                    notificationRepository.deleteNotification(maThongBao);
                    requireActivity().runOnUiThread(ReaderAccountFragment.this::loadNotifications);
                });
            });
        }

        @Override
        public int getItemCount() {
            return notifsList.size();
        }

        class NotifViewHolder extends RecyclerView.ViewHolder {
            View viewDot;
            TextView txtTitle, txtBody, txtDate;
            ImageView btnDelete;

            public NotifViewHolder(@NonNull View itemView) {
                super(itemView);
                viewDot = itemView.findViewById(R.id.viewUnreadDot);
                txtTitle = itemView.findViewById(R.id.txtNotifTitleRow);
                txtBody = itemView.findViewById(R.id.txtNotifBodyRow);
                txtDate = itemView.findViewById(R.id.txtNotifDateRow);
                btnDelete = itemView.findViewById(R.id.btnDeleteNotifRow);
            }
        }
    }
}
