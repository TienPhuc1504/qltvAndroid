package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderSearchFragment extends Fragment {

    private EditText editSearch;
    private AutoCompleteTextView spinnerCategory;
    private RecyclerView recyclerViewBooks;
    private TextView txtEmpty;

    private BookRepository bookRepository;
    private RequestRepository requestRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private List<Map<String, Object>> booksList = new ArrayList<>();
    private List<Map<String, Object>> categoriesList = new ArrayList<>();
    private BookReaderAdapter adapter;

    private Integer selectedCategoryId = 0;
    private String currentSearchTerm = "";
    private int currentReaderId = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reader_search, container, false);

        bookRepository = new BookRepository(requireContext());
        requestRepository = new RequestRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentReaderId = sharedPreferences.getInt("userId", 0);

        editSearch = view.findViewById(R.id.editSearchReaderCatalog);
        spinnerCategory = view.findViewById(R.id.spinnerCatReader);
        recyclerViewBooks = view.findViewById(R.id.recyclerViewReaderCatalog);
        txtEmpty = view.findViewById(R.id.txtEmptyReaderCatalog);

        recyclerViewBooks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookReaderAdapter();
        recyclerViewBooks.setAdapter(adapter);

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchTerm = s.toString();
                loadBooks();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadCategories();
        loadBooks();

        return view;
    }

    private void loadCategories() {
        executorService.execute(() -> {
            categoriesList = bookRepository.getAllCategories();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    List<String> names = new ArrayList<>();
                    names.add("Tất cả thể loại");
                    for (Map<String, Object> cat : categoriesList) {
                        names.add((String) cat.get("ten_the_loai"));
                    }
                    ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
                    spinnerCategory.setAdapter(spinAdapter);
                    spinnerCategory.setText("Tất cả thể loại", false);

                    spinnerCategory.setOnItemClickListener((parent, view1, position, id) -> {
                        if (position == 0) {
                            selectedCategoryId = 0;
                        } else {
                            selectedCategoryId = (Integer) categoriesList.get(position - 1).get("ma_the_loai");
                        }
                        loadBooks();
                    });
                });
            }
        });
    }

    private void loadBooks() {
        executorService.execute(() -> {
            booksList = bookRepository.getAllBooks(currentSearchTerm, selectedCategoryId);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (booksList.isEmpty()) {
                        txtEmpty.setVisibility(View.VISIBLE);
                        recyclerViewBooks.setVisibility(View.GONE);
                    } else {
                        txtEmpty.setVisibility(View.GONE);
                        recyclerViewBooks.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /**
     * Dialog Độc giả gửi yêu cầu mượn sách giấy
     */
    private void showRequestBorrowDialog(Map<String, Object> book) {
        int maSach = (int) book.get("ma_sach");
        String title = (String) book.get("tieu_de");

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View formView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_request_borrow, null);
        builder.setView(formView);

        TextView formTitle = formView.findViewById(R.id.txtBorrowDialogTitle);
        formTitle.setText("YÊU CẦU MƯỢN: " + title.toUpperCase());

        EditText edtDays = formView.findViewById(R.id.edtBorrowDialogDays);
        EditText edtNotes = formView.findViewById(R.id.edtBorrowDialogNotes);
        Button btnSend = formView.findViewById(R.id.btnBorrowDialogSend);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSend.setOnClickListener(v -> {
            String daysStr = edtDays.getText().toString().trim();
            String notes = edtNotes.getText().toString().trim();

            if (daysStr.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập số ngày mượn đề xuất!", Toast.LENGTH_SHORT).show();
                return;
            }

            int proposedDays = Integer.parseInt(daysStr);
            executorService.execute(() -> {
                try {
                    requestRepository.createBorrowRequest(currentReaderId, maSach, proposedDays, notes);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Gửi yêu cầu mượn sách thành công! Quyển sách đã được đặt trước chờ thủ thư phê duyệt.", Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            loadBooks();
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
     * Adapter RecyclerView Sách dành cho Độc giả
     */
    class BookReaderAdapter extends RecyclerView.Adapter<BookReaderAdapter.BookReaderViewHolder> {

        @NonNull
        @Override
        public BookReaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book_reader, parent, false);
            return new BookReaderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookReaderViewHolder holder, int position) {
            Map<String, Object> book = booksList.get(position);
            String tieuDe = (String) book.get("tieu_de");
            String tacGia = (String) book.get("tac_gia");
            String loaiSach = (String) book.get("loai_sach");
            String tenTheLoai = (String) book.get("ten_the_loai");
            int soQuyenCoSan = (int) book.get("so_quyen_co_san");
            int tongSoQuyen = (int) book.get("tong_so_quyen");
            String url = (String) book.get("url_tai_lieu");

            holder.txtBookTitleReader.setText(tieuDe);
            holder.txtBookAuthorReader.setText(tacGia);
            holder.txtBookCategoryReader.setText(tenTheLoai != null ? tenTheLoai : "Thể loại");

            LinearLayout.LayoutParams paramsBorrow = (LinearLayout.LayoutParams) holder.btnBorrowActionReader.getLayoutParams();
            LinearLayout.LayoutParams paramsRead = (LinearLayout.LayoutParams) holder.btnReadOnlineReader.getLayoutParams();

            // Tính toán kích thước nút cố định (125dp) cho nút đơn
            int singleButtonWidthPx = (int) (125 * holder.itemView.getContext().getResources().getDisplayMetrics().density);

            if ("CA_HAI".equals(loaiSach)) {
                holder.txtAvailabilityReader.setText("Còn: " + soQuyenCoSan + " / " + tongSoQuyen + " quyển | Online");
                holder.btnBorrowActionReader.setVisibility(View.VISIBLE);
                holder.btnReadOnlineReader.setVisibility(View.VISIBLE);

                // Khi có hai nút, tự động chia đôi tỷ lệ 50/50 mượt mà để chống tràn
                paramsBorrow.width = 0;
                paramsBorrow.weight = 1.0f;
                paramsRead.width = 0;
                paramsRead.weight = 1.0f;

                if (soQuyenCoSan > 0) {
                    holder.txtAvailabilityReader.setTextColor(0xFF4CAF50); // Xanh lá
                    holder.btnBorrowActionReader.setEnabled(true);
                    holder.btnBorrowActionReader.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00B0FF));
                } else {
                    holder.txtAvailabilityReader.setTextColor(0xFFFF5252); // Đỏ
                    holder.btnBorrowActionReader.setEnabled(false);
                    holder.btnBorrowActionReader.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E2C4D));
                }
            } else if ("SACH_GIAY".equals(loaiSach)) {
                holder.txtAvailabilityReader.setText("Còn: " + soQuyenCoSan + " / " + tongSoQuyen + " quyển");
                holder.btnBorrowActionReader.setVisibility(View.VISIBLE);
                holder.btnReadOnlineReader.setVisibility(View.GONE);

                // Nút đơn có độ dài cố định 125dp để đồng bộ
                paramsBorrow.width = singleButtonWidthPx;
                paramsBorrow.weight = 0.0f;

                if (soQuyenCoSan > 0) {
                    holder.txtAvailabilityReader.setTextColor(0xFF4CAF50);
                    holder.btnBorrowActionReader.setEnabled(true);
                    holder.btnBorrowActionReader.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00B0FF));
                } else {
                    holder.txtAvailabilityReader.setTextColor(0xFFFF5252);
                    holder.btnBorrowActionReader.setEnabled(false);
                    holder.btnBorrowActionReader.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E2C4D));
                }
            } else {
                holder.txtAvailabilityReader.setText("Đọc trực tuyến");
                holder.txtAvailabilityReader.setTextColor(0xFF00B0FF);
                holder.btnBorrowActionReader.setVisibility(View.GONE);
                holder.btnReadOnlineReader.setVisibility(View.VISIBLE);

                // Nút đơn có độ dài cố định 125dp để đồng bộ
                paramsRead.width = singleButtonWidthPx;
                paramsRead.weight = 0.0f;
            }

            holder.btnBorrowActionReader.setLayoutParams(paramsBorrow);
            holder.btnReadOnlineReader.setLayoutParams(paramsRead);

            // Mượn sách giấy
            holder.btnBorrowActionReader.setOnClickListener(v -> showRequestBorrowDialog(book));

            // Đọc sách online
            holder.btnReadOnlineReader.setOnClickListener(v -> {
                if (url != null && !url.isEmpty()) {
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(browserIntent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Đang đọc sách online: " + tieuDe, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "Liên kết tài liệu không khả dụng!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return booksList.size();
        }

        class BookReaderViewHolder extends RecyclerView.ViewHolder {
            TextView txtBookTitleReader, txtBookAuthorReader, txtBookCategoryReader, txtAvailabilityReader;
            Button btnBorrowActionReader, btnReadOnlineReader;

            public BookReaderViewHolder(@NonNull View itemView) {
                super(itemView);
                txtBookTitleReader = itemView.findViewById(R.id.txtBookTitleReader);
                txtBookAuthorReader = itemView.findViewById(R.id.txtBookAuthorReader);
                txtBookCategoryReader = itemView.findViewById(R.id.txtBookCategoryReader);
                txtAvailabilityReader = itemView.findViewById(R.id.txtAvailabilityReader);
                btnBorrowActionReader = itemView.findViewById(R.id.btnBorrowActionReader);
                btnReadOnlineReader = itemView.findViewById(R.id.btnReadOnlineReader);
            }
        }
    }
}
