package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderManagementFragment extends Fragment {

    private EditText editSearchReader;
    private RecyclerView recyclerViewReaders;
    private TextView txtEmptyReader;
    private FloatingActionButton fabAddReader;

    private UserRepository userRepository;
    private BorrowRepository borrowRepository;
    private ExecutorService executorService;
    
    private List<Map<String, Object>> readersList = new ArrayList<>();
    private ReaderAdapter adapter;
    private String currentSearchTerm = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reader_management, container, false);

        userRepository = new UserRepository(requireContext());
        borrowRepository = new BorrowRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        editSearchReader = view.findViewById(R.id.editSearchReader);
        recyclerViewReaders = view.findViewById(R.id.recyclerViewReaders);
        txtEmptyReader = view.findViewById(R.id.txtEmptyReader);
        fabAddReader = view.findViewById(R.id.fabAddReader);

        recyclerViewReaders.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ReaderAdapter();
        recyclerViewReaders.setAdapter(adapter);

        fabAddReader.setOnClickListener(v -> showAddReaderDialog());

        editSearchReader.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchTerm = s.toString();
                loadReaders();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadReaders();

        return view;
    }

    private void loadReaders() {
        executorService.execute(() -> {
            SQLiteDatabase db = new DatabaseOpenHelper(requireContext()).getReadableDatabase();
            List<Map<String, Object>> list = new ArrayList<>();
            String sql = "SELECT nd.*, dg.ma_doc_gia, dg.ngay_dk, " +
                         "tdg.ma_the, tdg.ngay_cap, tdg.ngay_het_han, tdg.trang_thai_the " +
                         "FROM NGUOI_DUNG nd " +
                         "JOIN DOC_GIA dg ON nd.ma_nd = dg.ma_nd " +
                         "LEFT JOIN THE_DOC_GIA tdg ON dg.ma_nd = tdg.ma_nd_doc_gia " +
                         "WHERE nd.loai_nguoi_dung = 'DOC_GIA'";
            
            List<String> params = new ArrayList<>();
            if (!currentSearchTerm.trim().isEmpty()) {
                sql += " AND (nd.ho_ten LIKE ? OR dg.ma_doc_gia LIKE ? OR nd.so_dt LIKE ?)";
                String pattern = "%" + currentSearchTerm.trim() + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
            
            sql += " ORDER BY nd.ma_nd DESC";
            
            try (Cursor cursor = db.rawQuery(sql, params.isEmpty() ? null : params.toArray(new String[0]))) {
                while (cursor.moveToNext()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("ma_nd", cursor.getInt(cursor.getColumnIndexOrThrow("ma_nd")));
                    map.put("ho_ten", cursor.getString(cursor.getColumnIndexOrThrow("ho_ten")));
                    map.put("dia_chi", cursor.getString(cursor.getColumnIndexOrThrow("dia_chi")));
                    map.put("so_dt", cursor.getString(cursor.getColumnIndexOrThrow("so_dt")));
                    map.put("email", cursor.getString(cursor.getColumnIndexOrThrow("email")));
                    map.put("ma_doc_gia", cursor.getString(cursor.getColumnIndexOrThrow("ma_doc_gia")));
                    map.put("ngay_dk", cursor.getString(cursor.getColumnIndexOrThrow("ngay_dk")));
                    map.put("ma_the", cursor.isNull(cursor.getColumnIndexOrThrow("ma_the")) ? null : cursor.getInt(cursor.getColumnIndexOrThrow("ma_the")));
                    map.put("ngay_cap", cursor.getString(cursor.getColumnIndexOrThrow("ngay_cap")));
                    map.put("ngay_het_han", cursor.getString(cursor.getColumnIndexOrThrow("ngay_het_han")));
                    map.put("trang_thai_the", cursor.getString(cursor.getColumnIndexOrThrow("trang_thai_the")));
                    list.add(map);
                }
            }
            db.close();

            readersList.clear();
            readersList.addAll(list);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (readersList.isEmpty()) {
                        txtEmptyReader.setVisibility(View.VISIBLE);
                        recyclerViewReaders.setVisibility(View.GONE);
                    } else {
                        txtEmptyReader.setVisibility(View.GONE);
                        recyclerViewReaders.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /**
     * Dialog Chi Tiết Độc Giả
     */
    private void showReaderDetailDialog(Map<String, Object> reader) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reader_detail, null);
        builder.setView(view);

        TextView txtName = view.findViewById(R.id.txtDetName);
        TextView txtCode = view.findViewById(R.id.txtDetCode);
        TextView txtPhone = view.findViewById(R.id.txtDetPhone);
        TextView txtEmail = view.findViewById(R.id.txtDetEmail);
        TextView txtAddress = view.findViewById(R.id.txtDetAddress);
        TextView txtRegDate = view.findViewById(R.id.txtDetRegDate);
        TextView txtCardStatus = view.findViewById(R.id.txtDetCardStatus);
        TextView txtCardStart = view.findViewById(R.id.txtDetCardStart);
        TextView txtCardEnd = view.findViewById(R.id.txtDetCardEnd);

        Button btnHistory = view.findViewById(R.id.btnReaderHistory);
        Button btnRenew = view.findViewById(R.id.btnReaderCardRenew);
        Button btnPrint = view.findViewById(R.id.btnReaderCardPrint);
        Button btnClose = view.findViewById(R.id.btnReaderClose);

        int maNd = (int) reader.get("ma_nd");
        String name = (String) reader.get("ho_ten");
        String code = (String) reader.get("ma_doc_gia");
        String status = (String) reader.get("trang_thai_the");

        txtName.setText("Họ tên: " + name);
        txtCode.setText("Mã độc giả: " + code);
        txtPhone.setText("Số điện thoại: " + reader.get("so_dt"));
        txtEmail.setText("Email: " + reader.get("email"));
        txtAddress.setText("Địa chỉ: " + reader.get("dia_chi"));
        txtRegDate.setText("Ngày đăng ký: " + reader.get("ngay_dk"));

        if (status != null) {
            txtCardStatus.setText("Trạng thái thẻ: " + status);
            txtCardStart.setText("Ngày cấp thẻ: " + reader.get("ngay_cap"));
            txtCardEnd.setText("Ngày hết hạn: " + reader.get("ngay_het_han"));
            if ("HOAT_DONG".equals(status)) {
                txtCardStatus.setTextColor(0xFF4CAF50);
            } else {
                txtCardStatus.setTextColor(0xFFFF5252);
            }
        } else {
            txtCardStatus.setText("Trạng thái thẻ: CHƯA CÓ THẺ");
            txtCardStatus.setTextColor(0xFFFF5252);
            txtCardStart.setText("Ngày cấp thẻ: -");
            txtCardEnd.setText("Ngày hết hạn: -");
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Xem lịch sử mượn trả
        btnHistory.setOnClickListener(v -> showReaderHistoryDialog(maNd, name));

        // Gia hạn thẻ
        btnRenew.setOnClickListener(v -> showRenewCardDialog(maNd, status));

        // In thẻ độc giả
        btnPrint.setOnClickListener(v -> showCardPreviewDialog(reader));
    }

    /**
     * Dialog Gia hạn Thẻ độc giả
     */
    private void showRenewCardDialog(int maNd, String currentStatus) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_copy, null); // Mẹo dùng chung layout đơn giản
        builder.setView(view);

        TextView txtTitle = view.findViewById(android.R.id.text1); // wait
        // Hoặc tạo dialog nhanh
        String[] daysList = {"30 ngày", "90 ngày", "365 ngày (1 năm)"};
        final int[] days = {30, 90, 365};
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Gia hạn thẻ Độc giả")
            .setSingleChoiceItems(daysList, 0, null)
            .setPositiveButton("Gia hạn", (dialogInterface, whichIdx) -> {
                int selectedIdx = ((AlertDialog) dialogInterface).getListView().getCheckedItemPosition();
                int addDays = days[selectedIdx];
                
                executorService.execute(() -> {
                    SQLiteDatabase db = new DatabaseOpenHelper(requireContext()).getWritableDatabase();
                    db.execSQL("UPDATE THE_DOC_GIA " +
                               "SET ngay_het_han = date(ngay_het_han, '+' || ? || ' days'), " +
                               "trang_thai_the = 'HOAT_DONG' " +
                               "WHERE ma_nd_doc_gia = ?", new Object[]{addDays, maNd});
                    db.close();
                    
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Gia hạn thẻ độc giả thành công!", Toast.LENGTH_SHORT).show();
                            loadReaders();
                        });
                    }
                });
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    /**
     * Dialog In Thẻ Độc giả (Preview)
     */
    private void showCardPreviewDialog(Map<String, Object> reader) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_card_preview, null);
        builder.setView(view);

        TextView txtCardName = view.findViewById(R.id.txtCardName);
        TextView txtCardCode = view.findViewById(R.id.txtCardCode);
        TextView txtCardExpiry = view.findViewById(R.id.txtCardExpiry);
        Button btnPrint = view.findViewById(R.id.btnCardPrintSubmit);
        Button btnClose = view.findViewById(R.id.btnCardPrintClose);

        txtCardName.setText(((String) reader.get("ho_ten")).toUpperCase(Locale.ROOT));
        txtCardCode.setText("MÃ ĐG: " + reader.get("ma_doc_gia"));
        String exp = (String) reader.get("ngay_het_han");
        txtCardExpiry.setText("HẠN THẺ: " + (exp != null ? exp : "CHƯA CÓ"));

        AlertDialog dialog = builder.create();
        dialog.show();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnPrint.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Đã mô phỏng gửi tệp thẻ PDF tới máy in thành công!", Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });
    }

    /**
     * Dialog Xem Lịch sử mượn trả
     */
    private void showReaderHistoryDialog(int maNd, String hoTen) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reader_history, null);
        builder.setView(view);

        TextView txtTitle = view.findViewById(R.id.txtHistoryTitle);
        txtTitle.setText("Lịch sử: " + hoTen);

        RecyclerView rv = view.findViewById(R.id.rvReaderHistory);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        Button btnClose = view.findViewById(R.id.btnHistoryClose);

        List<Map<String, Object>> histList = new ArrayList<>();
        class HistAdapter extends RecyclerView.Adapter<HistAdapter.HistViewHolder> {

            @NonNull
            @Override
            public HistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
                return new HistViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull HistViewHolder holder, int position) {
                Map<String, Object> h = histList.get(position);
                holder.txtTitle.setText((String) h.get("tieu_de"));
                holder.txtDates.setText("Mượn: " + h.get("ngay_muon") + " | Hạn trả: " + h.get("ngay_hen_tra"));
                
                String ngayTra = (String) h.get("ngay_tra_thuc");
                double fine = (double) h.get("tien_phat");
                holder.txtFine.setText("Phạt: " + String.format(Locale.getDefault(), "%,.0f", fine) + " VND");

                if (ngayTra != null) {
                    holder.txtReturn.setText("Đã trả: " + ngayTra);
                    holder.txtReturn.setTextColor(0xFF4CAF50);
                } else {
                    holder.txtReturn.setText("ĐANG MƯỢN");
                    holder.txtReturn.setTextColor(0xFFFF5252);
                }
            }

            @Override
            public int getItemCount() {
                return histList.size();
            }

            class HistViewHolder extends RecyclerView.ViewHolder {
                TextView txtTitle, txtDates, txtReturn, txtFine;

                public HistViewHolder(@NonNull View itemView) {
                    super(itemView);
                    txtTitle = itemView.findViewById(R.id.txtHistBookTitle);
                    txtDates = itemView.findViewById(R.id.txtHistDates);
                    txtReturn = itemView.findViewById(R.id.txtHistReturnDate);
                    txtFine = itemView.findViewById(R.id.txtHistFine);
                }
            }
        }

        HistAdapter histAdapter = new HistAdapter();
        rv.setAdapter(histAdapter);

        executorService.execute(() -> {
            List<Map<String, Object>> list = borrowRepository.getReaderBorrowHistory(maNd);
            histList.clear();
            histList.addAll(list);
            if (isAdded()) {
                requireActivity().runOnUiThread(histAdapter::notifyDataSetChanged);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
        btnClose.setOnClickListener(v -> dialog.dismiss());
    }

    /**
     * Dialog Thêm độc giả mới (FAB làm)
     */
    private void showAddReaderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_book, null); // Tận dụng layout nhập
        builder.setView(view);

        TextView title = view.findViewById(R.id.txtFormTitle); // textview tiêu đề trong dialog_add_book
        title.setText("THÊM ĐỘC GIẢ MỚI");

        EditText edtUsername = view.findViewById(R.id.edtTitle); // Tận dụng edtTitle làm username
        edtUsername.setHint("Tên đăng nhập mới");
        
        EditText edtPassword = view.findViewById(R.id.edtAuthor); // Tận dụng edtAuthor làm password
        edtPassword.setHint("Mật khẩu");
        edtPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Ẩn spinner/radio và các layout online/giấy không dùng
        view.findViewById(R.id.spinDialogCat).setVisibility(View.GONE);
        view.findViewById(R.id.rgType).setVisibility(View.GONE);
        view.findViewById(R.id.layoutPaperOnly).setVisibility(View.GONE);
        
        LinearLayout layoutExtra = view.findViewById(R.id.layoutOnlineOnly);
        layoutExtra.setVisibility(View.VISIBLE);
        
        EditText edtFullName = view.findViewById(R.id.edtUrl); // Tên đầy đủ
        edtFullName.setHint("Họ và tên");
        edtFullName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);

        EditText edtAddress = view.findViewById(R.id.edtFormat); // Địa chỉ
        edtAddress.setHint("Địa chỉ");
        edtAddress.setText("");

        Button btnSave = view.findViewById(R.id.btnSaveBook);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String user = edtUsername.getText().toString().trim();
            String pass = edtPassword.getText().toString();
            String name = edtFullName.getText().toString().trim();
            String addr = edtAddress.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty() || name.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ Tên đăng nhập, Mật khẩu và Họ tên!", Toast.LENGTH_SHORT).show();
                return;
            }

            executorService.execute(() -> {
                try {
                    userRepository.registerReader(user, pass, name, addr, "", "");
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Đăng ký độc giả mới thành công!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadReaders();
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });
    }

    /**
     * Adapter RecyclerView Độc giả
     */
    class ReaderAdapter extends RecyclerView.Adapter<ReaderAdapter.ReaderViewHolder> {

        @NonNull
        @Override
        public ReaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reader, parent, false);
            return new ReaderViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ReaderViewHolder holder, int position) {
            Map<String, Object> reader = readersList.get(position);
            String name = (String) reader.get("ho_ten");
            String code = (String) reader.get("ma_doc_gia");
            String phone = (String) reader.get("so_dt");
            String status = (String) reader.get("trang_thai_the");

            holder.txtName.setText(name);
            holder.txtCodePhone.setText(code + " | SĐT: " + (phone == null || phone.isEmpty() ? "Chưa có" : phone));

            if (status != null) {
                holder.badgeStatus.setText(status);
                if ("HOAT_DONG".equals(status)) {
                    holder.badgeStatus.setTextColor(0xFF4CAF50);
                    holder.badgeStatus.setBackgroundColor(0x1B4CAF50);
                } else {
                    holder.badgeStatus.setTextColor(0xFFFF5252);
                    holder.badgeStatus.setBackgroundColor(0x1BFF5252);
                }
            } else {
                holder.badgeStatus.setText("CHƯA THẺ");
                holder.badgeStatus.setTextColor(0xFFFF5252);
                holder.badgeStatus.setBackgroundColor(0x1BFF5252);
            }

            holder.itemView.setOnClickListener(v -> showReaderDetailDialog(reader));
        }

        @Override
        public int getItemCount() {
            return readersList.size();
        }

        class ReaderViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtCodePhone, badgeStatus;

            public ReaderViewHolder(@NonNull View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txtReaderName);
                txtCodePhone = itemView.findViewById(R.id.txtReaderCodePhone);
                badgeStatus = itemView.findViewById(R.id.badgeCardStatus);
            }
        }
    }
}
