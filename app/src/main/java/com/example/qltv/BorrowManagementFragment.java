package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BorrowManagementFragment extends Fragment {

    private EditText editSearchBorrow;
    private RecyclerView recyclerViewBorrows;
    private TextView txtEmptyBorrow;
    private FloatingActionButton fabAddBorrow;

    private BorrowRepository borrowRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private List<Map<String, Object>> borrowsList = new ArrayList<>();
    private BorrowAdapter adapter;
    private String currentSearchTerm = "";
    private int currentStaffId = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_borrow_management, container, false);

        borrowRepository = new BorrowRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentStaffId = sharedPreferences.getInt("userId", 0);

        editSearchBorrow = view.findViewById(R.id.editSearchBorrow);
        recyclerViewBorrows = view.findViewById(R.id.recyclerViewBorrows);
        txtEmptyBorrow = view.findViewById(R.id.txtEmptyBorrow);
        fabAddBorrow = view.findViewById(R.id.fabAddBorrow);

        recyclerViewBorrows.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BorrowAdapter();
        recyclerViewBorrows.setAdapter(adapter);

        fabAddBorrow.setOnClickListener(v -> showAddBorrowDialog());

        editSearchBorrow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchTerm = s.toString();
                loadBorrows();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadBorrows();

        return view;
     }

    private void loadBorrows() {
        executorService.execute(() -> {
            // Lấy danh sách phiếu DANG_MUON
            borrowsList = borrowRepository.getAllBorrows(currentSearchTerm, "DANG_MUON");
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (borrowsList.isEmpty()) {
                        txtEmptyBorrow.setVisibility(View.VISIBLE);
                        recyclerViewBorrows.setVisibility(View.GONE);
                    } else {
                        txtEmptyBorrow.setVisibility(View.GONE);
                        recyclerViewBorrows.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private List<Map<String, Object>> getReadersList() {
        List<Map<String, Object>> list = new ArrayList<>();
        SQLiteDatabase db = new DatabaseOpenHelper(requireContext()).getReadableDatabase();
        String sql = "SELECT nd.ma_nd, nd.ho_ten, dg.ma_doc_gia FROM NGUOI_DUNG nd JOIN DOC_GIA dg ON nd.ma_nd = dg.ma_nd ORDER BY nd.ho_ten ASC";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_nd", c.getInt(0));
                map.put("ho_ten", c.getString(1));
                map.put("ma_doc_gia", c.getString(2));
                list.add(map);
            }
        }
        db.close();
        return list;
    }

    private List<Map<String, Object>> getBooksList() {
        List<Map<String, Object>> list = new ArrayList<>();
        SQLiteDatabase db = new DatabaseOpenHelper(requireContext()).getReadableDatabase();
        String sql = "SELECT ma_sach, tieu_de, tac_gia FROM SACH ORDER BY tieu_de ASC";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Map<String, Object> map = new HashMap<>();
                map.put("ma_sach", c.getInt(0));
                map.put("tieu_de", c.getString(1));
                map.put("tac_gia", c.getString(2));
                list.add(map);
            }
        }
        db.close();
        return list;
    }

    /**
     * Lập phiếu mượn tại quầy bằng Autocomplete Dialog
     */
    private void showAddBorrowDialog() {
        executorService.execute(() -> {
            List<Map<String, Object>> readers = getReadersList();
            List<Map<String, Object>> books = getBooksList();

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
                    View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_borrow, null);
                    builder.setView(view);

                    AutoCompleteTextView autoReader = view.findViewById(R.id.autoCompleteReader);
                    AutoCompleteTextView autoBook = view.findViewById(R.id.autoCompleteBook);
                    Button btnSave = view.findViewById(R.id.btnSaveBorrow);

                    List<String> readerStrings = new ArrayList<>();
                    for (Map<String, Object> r : readers) {
                        readerStrings.add(r.get("ho_ten") + " (" + r.get("ma_doc_gia") + ")");
                    }

                    List<String> bookStrings = new ArrayList<>();
                    for (Map<String, Object> b : books) {
                        bookStrings.add(b.get("tieu_de") + " - " + b.get("tac_gia") + " (Mã: " + b.get("ma_sach") + ")");
                    }

                    ArrayAdapter<String> readerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, readerStrings);
                    autoReader.setAdapter(readerAdapter);
                    autoReader.setThreshold(1);

                    ArrayAdapter<String> bookAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, bookStrings);
                    autoBook.setAdapter(bookAdapter);
                    autoBook.setThreshold(1);

                    AlertDialog dialog = builder.create();
                    dialog.show();

                    btnSave.setOnClickListener(v -> {
                        String selectedReaderText = autoReader.getText().toString();
                        String selectedBookText = autoBook.getText().toString();

                        int readerIdx = readerStrings.indexOf(selectedReaderText);
                        int bookIdx = bookStrings.indexOf(selectedBookText);

                        if (readerIdx == -1 || bookIdx == -1) {
                            Toast.makeText(requireContext(), "Vui lòng chọn Độc giả và Đầu sách hợp lệ từ danh sách gợi ý!", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int maNdDocGia = (int) readers.get(readerIdx).get("ma_nd");
                        int maSach = (int) books.get(bookIdx).get("ma_sach");

                        executorService.execute(() -> {
                            try {
                                borrowRepository.createBorrow(maNdDocGia, currentStaffId, maSach, 14);
                                if (isAdded()) {
                                    requireActivity().runOnUiThread(() -> {
                                        Toast.makeText(requireContext(), "Lập phiếu mượn sách thành công!", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                        loadBorrows();
                                    });
                                }
                            } catch (Exception e) {
                                if (isAdded()) {
                                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            }
                        });
                    });
                });
            }
        });
    }

    /**
     * Xử lý trả sách
     */
    private void performReturnBook(Map<String, Object> borrow) {
        int maPhieu = (int) borrow.get("ma_phieu");
        double fine = (double) borrow.get("tien_phat");
        String title = (String) borrow.get("tieu_de");

        if (fine > 0) {
            // Hiển thị hộp thoại nộp phạt trễ hạn
            new AlertDialog.Builder(requireContext())
                .setTitle("Thu phí phạt trễ hạn")
                .setMessage("Đầu sách: \"" + title + "\"\nSố tiền phạt trễ hạn: " + String.format(Locale.getDefault(), "%,.0f", fine) + " VND.\n\nXác nhận thu tiền và hoàn trả sách?")
                .setPositiveButton("Thu tiền & Trả sách", (dialogInterface, i) -> {
                    executorService.execute(() -> {
                        borrowRepository.returnBook(maPhieu, fine);
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "Ghi nhận trả sách và thu phạt thành công!", Toast.LENGTH_SHORT).show();
                                loadBorrows();
                            });
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
        } else {
            // Trả sách trơn tru không phạt
            executorService.execute(() -> {
                borrowRepository.returnBook(maPhieu, 0.0);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Trả sách thành công!", Toast.LENGTH_SHORT).show();
                        loadBorrows();
                    });
                }
            });
        }
    }

    /**
     * Adapter RecyclerView Phiếu mượn
     */
    class BorrowAdapter extends RecyclerView.Adapter<BorrowAdapter.BorrowViewHolder> {

        @NonNull
        @Override
        public BorrowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_borrow, parent, false);
            return new BorrowViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BorrowViewHolder holder, int position) {
            Map<String, Object> b = borrowsList.get(position);
            String readerName = (String) b.get("ten_doc_gia");
            String readerCode = (String) b.get("ma_doc_gia");
            String title = (String) b.get("tieu_de");
            String copyCode = (String) b.get("ma_quyen_sach");
            String ngayMuon = (String) b.get("ngay_muon");
            String ngayHenTra = (String) b.get("ngay_hen_tra");
            double fine = (double) b.get("tien_phat");

            holder.txtReader.setText(readerName + " (" + readerCode + ")");
            holder.txtBookTitle.setText("Sách: " + title);
            
            if (copyCode != null) {
                holder.txtCopyCode.setText("Bản sao: " + copyCode);
                holder.txtCopyCode.setVisibility(View.VISIBLE);
            } else {
                holder.txtCopyCode.setVisibility(View.GONE);
            }

            holder.txtDates.setText("Mượn: " + DateTimeUtils.formatDate(ngayMuon) + " | Hạn trả: " + DateTimeUtils.formatDate(ngayHenTra));

            if (fine > 0) {
                holder.badgeStatus.setText("QUÁ HẠN");
                holder.badgeStatus.setTextColor(0xFFFF5252);
                holder.badgeStatus.setBackgroundColor(0x1BFF5252);
                holder.txtFine.setText("Phạt quá hạn: " + String.format(Locale.getDefault(), "%,.0f", fine) + " VND");
                holder.txtFine.setVisibility(View.VISIBLE);
            } else {
                holder.badgeStatus.setText("ĐANG MƯỢN");
                holder.badgeStatus.setTextColor(0xFFFF9800);
                holder.badgeStatus.setBackgroundColor(0x1BFF9800);
                holder.txtFine.setVisibility(View.GONE);
            }

            holder.btnReturn.setOnClickListener(v -> performReturnBook(b));
        }

        @Override
        public int getItemCount() {
            return borrowsList.size();
        }

        class BorrowViewHolder extends RecyclerView.ViewHolder {
            TextView txtReader, badgeStatus, txtBookTitle, txtCopyCode, txtDates, txtFine;
            Button btnReturn;

            public BorrowViewHolder(@NonNull View itemView) {
                super(itemView);
                txtReader = itemView.findViewById(R.id.txtBorrowReader);
                badgeStatus = itemView.findViewById(R.id.badgeBorrowStatus);
                txtBookTitle = itemView.findViewById(R.id.txtBorrowBookTitle);
                txtCopyCode = itemView.findViewById(R.id.txtBorrowCopyCode);
                txtDates = itemView.findViewById(R.id.txtBorrowDates);
                txtFine = itemView.findViewById(R.id.txtBorrowFine);
                btnReturn = itemView.findViewById(R.id.btnReturnBook);
            }
        }
    }
}
