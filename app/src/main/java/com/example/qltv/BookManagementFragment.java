package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BookManagementFragment extends Fragment {

    private EditText editSearch;
    private AutoCompleteTextView spinnerCategory;
    private RecyclerView recyclerViewBooks;
    private TextView txtEmpty;
    private FloatingActionButton fabAddBook;

    private BookRepository bookRepository;
    private ExecutorService executorService;
    private List<Map<String, Object>> booksList = new ArrayList<>();
    private List<Map<String, Object>> categoriesList = new ArrayList<>();
    private BookAdapter adapter;

    private Integer selectedCategoryId = 0;
    private String currentSearchTerm = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_book_management, container, false);

        bookRepository = new BookRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        editSearch = view.findViewById(R.id.editSearch);
        spinnerCategory = view.findViewById(R.id.spinnerCategory);
        recyclerViewBooks = view.findViewById(R.id.recyclerViewBooks);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        fabAddBook = view.findViewById(R.id.fabAddBook);

        recyclerViewBooks.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookAdapter();
        recyclerViewBooks.setAdapter(adapter);

        // Nút thêm sách mới
        fabAddBook.setOnClickListener(v -> showAddBookDialog());

        // Lọc theo từ khóa tìm kiếm
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
     * Dialog Thêm Sách Mới
     */
    private void showAddBookDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_book, null);
        builder.setView(view);

        EditText edtTitle = view.findViewById(R.id.edtTitle);
        EditText edtAuthor = view.findViewById(R.id.edtAuthor);
        AutoCompleteTextView spinCat = view.findViewById(R.id.spinDialogCat);
        android.widget.CheckBox cbPaper = view.findViewById(R.id.cbPaper);
        android.widget.CheckBox cbOnline = view.findViewById(R.id.cbOnline);
        LinearLayout layoutPaper = view.findViewById(R.id.layoutPaperOnly);
        LinearLayout layoutOnline = view.findViewById(R.id.layoutOnlineOnly);
        EditText edtQty = view.findViewById(R.id.edtQty);
        EditText edtUrl = view.findViewById(R.id.edtUrl);
        EditText edtFormat = view.findViewById(R.id.edtFormat);
        Button btnSave = view.findViewById(R.id.btnSaveBook);

        // Điền Thể Loại AutoCompleteTextView
        List<String> names = new ArrayList<>();
        for (Map<String, Object> cat : categoriesList) {
            names.add((String) cat.get("ten_the_loai"));
        }
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
        spinCat.setAdapter(spinAdapter);
        if (!names.isEmpty()) {
            spinCat.setText(names.get(0), false);
        }

        // Chuyển loại sách giấy/online bằng CheckBox
        cbPaper.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutPaper.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        cbOnline.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutOnline.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String title = edtTitle.getText().toString().trim();
            String author = edtAuthor.getText().toString().trim();
            if (title.isEmpty() || author.isEmpty() || categoriesList.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ tiêu đề và tác giả!", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean hasPaper = cbPaper.isChecked();
            boolean hasOnline = cbOnline.isChecked();
            if (!hasPaper && !hasOnline) {
                Toast.makeText(requireContext(), "Vui lòng chọn ít nhất một loại sách (Giấy hoặc Online)!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Tìm thể loại
            String selectedCatName = spinCat.getText().toString().trim();
            int findCatPos = -1;
            for (int i = 0; i < categoriesList.size(); i++) {
                if (categoriesList.get(i).get("ten_the_loai").equals(selectedCatName)) {
                    findCatPos = i;
                    break;
                }
            }
            if (findCatPos == -1) {
                Toast.makeText(requireContext(), "Vui lòng chọn thể loại hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }
            int catId = (int) categoriesList.get(findCatPos).get("ma_the_loai");

            // Xác định loại sách (Giấy, Online hoặc Cả hai)
            String loaiSach = "SACH_GIAY";
            if (hasPaper && hasOnline) {
                loaiSach = "CA_HAI";
            } else if (hasOnline) {
                loaiSach = "SACH_ONLINE";
            }

            int qty = 1;
            String url = "";
            String format = "PDF";

            if (hasPaper) {
                String qtyStr = edtQty.getText().toString().trim();
                if (!qtyStr.isEmpty()) {
                    qty = Integer.parseInt(qtyStr);
                }
            }
            if (hasOnline) {
                url = edtUrl.getText().toString().trim();
                format = edtFormat.getText().toString().trim();
                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), "Vui lòng nhập URL tài liệu!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            final int finalQty = qty;
            final String finalUrl = url;
            final String finalFormat = format;
            final String finalLoaiSach = loaiSach;

            executorService.execute(() -> {
                bookRepository.addBook(title, author, catId, finalLoaiSach, finalQty, finalUrl, finalFormat);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Thêm đầu sách thành công!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadBooks();
                    });
                }
            });
        });
    }

    /**
     * Dialog Sửa Đầu Sách
     */
    private void showEditBookDialog(Map<String, Object> book) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_book, null);
        builder.setView(view);

        EditText edtTitle = view.findViewById(R.id.edtTitleEdit);
        EditText edtAuthor = view.findViewById(R.id.edtAuthorEdit);
        AutoCompleteTextView spinCat = view.findViewById(R.id.spinDialogCatEdit);
        AutoCompleteTextView spinStatus = view.findViewById(R.id.spinBookStatusEdit);
        View layoutBookStatusEditContainer = view.findViewById(R.id.layoutBookStatusEditContainer);
        LinearLayout layoutOnline = view.findViewById(R.id.layoutOnlineOnlyEdit);
        EditText edtUrl = view.findViewById(R.id.edtUrlEdit);
        EditText edtFormat = view.findViewById(R.id.edtFormatEdit);
        Button btnSave = view.findViewById(R.id.btnSaveBookEdit);

        int maSach = (int) book.get("ma_sach");
        String loaiSach = (String) book.get("loai_sach");

        edtTitle.setText((String) book.get("tieu_de"));
        edtAuthor.setText((String) book.get("tac_gia"));

        // Load Thể loại
        List<String> names = new ArrayList<>();
        int selectedCatPos = 0;
        for (int i = 0; i < categoriesList.size(); i++) {
            Map<String, Object> cat = categoriesList.get(i);
            names.add((String) cat.get("ten_the_loai"));
            if (cat.get("ma_the_loai").equals(book.get("ma_the_loai"))) {
                selectedCatPos = i;
            }
        }
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
        spinCat.setAdapter(spinAdapter);
        if (selectedCatPos >= 0 && selectedCatPos < names.size()) {
            spinCat.setText(names.get(selectedCatPos), false);
        }

        // Load Trạng thái Sách giấy/Online
        if ("CA_HAI".equals(loaiSach)) {
            layoutOnline.setVisibility(View.VISIBLE);
            edtUrl.setText((String) book.get("url_tai_lieu"));
            edtFormat.setText((String) book.get("dinh_dang"));
            layoutBookStatusEditContainer.setVisibility(View.VISIBLE);
            
            String[] displayStatuses = {"CÓ SẴN", "KHÔNG CÓ SẴN"};
            ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, displayStatuses);
            spinStatus.setAdapter(statusAdapter);
            if ("KHONG_CO_SAN".equals(book.get("trang_thai_sach"))) {
                spinStatus.setText("KHÔNG CÓ SẴN", false);
            } else {
                spinStatus.setText("CÓ SẴN", false);
            }
        } else if ("SACH_ONLINE".equals(loaiSach)) {
            layoutOnline.setVisibility(View.VISIBLE);
            edtUrl.setText((String) book.get("url_tai_lieu"));
            edtFormat.setText((String) book.get("dinh_dang"));
            layoutBookStatusEditContainer.setVisibility(View.GONE); // Online không cần trạng thái giấy
        } else {
            layoutOnline.setVisibility(View.GONE);
            layoutBookStatusEditContainer.setVisibility(View.VISIBLE);
            // Sách giấy: CO_SAN, KHONG_CO_SAN -> Việt hóa thành: CÓ SẴN, KHÔNG CÓ SẴN
            String[] displayStatuses = {"CÓ SẴN", "KHÔNG CÓ SẴN"};
            ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, displayStatuses);
            spinStatus.setAdapter(statusAdapter);
            if ("KHONG_CO_SAN".equals(book.get("trang_thai_sach"))) {
                spinStatus.setText("KHÔNG CÓ SẴN", false);
            } else {
                spinStatus.setText("CÓ SẴN", false);
            }
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String title = edtTitle.getText().toString().trim();
            String author = edtAuthor.getText().toString().trim();
            if (title.isEmpty() || author.isEmpty()) {
                Toast.makeText(requireContext(), "Tiêu đề và tác giả không được để trống!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Tìm thể loại được chọn
            String selectedCatName = spinCat.getText().toString().trim();
            int findCatPos = -1;
            for (int i = 0; i < categoriesList.size(); i++) {
                if (categoriesList.get(i).get("ten_the_loai").equals(selectedCatName)) {
                    findCatPos = i;
                    break;
                }
            }
            if (findCatPos == -1) {
                Toast.makeText(requireContext(), "Vui lòng chọn thể loại hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }
            int catId = (int) categoriesList.get(findCatPos).get("ma_the_loai");

            // Lấy trạng thái được chọn (Việt hóa ngược lại DB thô)
            String selectedStatusText = spinStatus.getText().toString().trim();
            String trangThai = "CO_SAN";
            if ("KHÔNG CÓ SẴN".equals(selectedStatusText)) {
                trangThai = "KHONG_CO_SAN";
            }

            String url = edtUrl.getText().toString().trim();
            String format = edtFormat.getText().toString().trim();

            if (("SACH_ONLINE".equals(loaiSach) || "CA_HAI".equals(loaiSach)) && url.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập URL tài liệu!", Toast.LENGTH_SHORT).show();
                return;
            }

            final String finalTrangThai = trangThai;
            executorService.execute(() -> {
                bookRepository.updateBook(maSach, title, author, catId, finalTrangThai, url, format);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Cập nhật sách thành công!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        loadBooks();
                    });
                }
            });
        });
    }

    /**
     * Dialog Quản lý Các Bản Sao Sách Vật Lý (`QUYEN_SACH`)
     */
    private void showManageCopiesDialog(Map<String, Object> book) {
        int maSach = (int) book.get("ma_sach");
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manage_copies, null);
        builder.setView(view);

        TextView txtTitle = view.findViewById(R.id.txtDialogCopiesTitle);
        txtTitle.setText("Bản sao: " + book.get("tieu_de"));

        RecyclerView rvCopies = view.findViewById(R.id.rvDialogCopies);
        rvCopies.setLayoutManager(new LinearLayoutManager(requireContext()));

        EditText edtViTri = view.findViewById(R.id.edtNewCopyViTri);
        EditText edtGhiChu = view.findViewById(R.id.edtNewCopyGhiChu);
        Button btnAddCopy = view.findViewById(R.id.btnDialogAddCopy);

        List<Map<String, Object>> copiesList = new ArrayList<>();
        class CopyAdapter extends RecyclerView.Adapter<CopyAdapter.CopyViewHolder> {

            @NonNull
            @Override
            public CopyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_copy, parent, false);
                return new CopyViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull CopyViewHolder holder, int position) {
                Map<String, Object> copy = copiesList.get(position);
                int maQuyen = (int) copy.get("ma_quyen");
                String maQuyenSach = (String) copy.get("ma_quyen_sach");
                String trangThai = (String) copy.get("trang_thai");
                String viTri = (String) copy.get("vi_tri");
                String ghiChu = (String) copy.get("ghi_chu");

                holder.txtCode.setText(maQuyenSach);
                holder.txtInfo.setText("Vị trí: " + (viTri == null ? "Chưa rõ" : viTri) + " | " + (ghiChu == null ? "Không ghi chú" : ghiChu));

                // Việt hóa trạng thái quyển sách và thiết lập màu sắc tương ứng
                String trangThaiViet = trangThai;
                if ("CO_SAN".equals(trangThai)) {
                    trangThaiViet = "CÓ SẴN";
                    holder.txtStatus.setTextColor(0xFF4CAF50); // Xanh lá
                } else if ("DANG_MUON".equals(trangThai)) {
                    trangThaiViet = "ĐANG MƯỢN";
                    holder.txtStatus.setTextColor(0xFF00B0FF); // Xanh dương
                } else if ("DAT_TRUOC".equals(trangThai)) {
                    trangThaiViet = "ĐẶT TRƯỚC";
                    holder.txtStatus.setTextColor(0xFFFF9800); // Cam
                } else if ("KHONG_CO_SAN".equals(trangThai)) {
                    trangThaiViet = "KHÔNG CÓ SẴN";
                    holder.txtStatus.setTextColor(0xFFBFBFCF); // Bạc/xám
                } else if ("HONG".equals(trangThai)) {
                    trangThaiViet = "HỎNG";
                    holder.txtStatus.setTextColor(0xFFFF5252); // Đỏ
                } else if ("MAT".equals(trangThai)) {
                    trangThaiViet = "MẤT";
                    holder.txtStatus.setTextColor(0xFFFF5252); // Đỏ
                }
                holder.txtStatus.setText(trangThaiViet);

                // Nút sửa vị trí/ghi chú bản sao
                holder.btnEdit.setOnClickListener(v -> {
                    if ("DANG_MUON".equals(trangThai) || "DAT_TRUOC".equals(trangThai)) {
                        Toast.makeText(requireContext(), "Không thể chỉnh sửa bản sao đang được mượn hoặc đặt trước!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    AlertDialog.Builder editB = new AlertDialog.Builder(requireContext());
                    View ev = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_copy, null);
                    editB.setView(ev);

                    EditText edPos = ev.findViewById(R.id.edtEditCopyViTri);
                    EditText edNote = ev.findViewById(R.id.edtEditCopyGhiChu);
                    Spinner spStatus = ev.findViewById(R.id.spinEditCopyStatus);
                    Button btnSave = ev.findViewById(R.id.btnSaveCopyEdit);

                    edPos.setText(viTri);
                    edNote.setText(ghiChu);

                    String[] statuses = {"CO_SAN", "KHONG_CO_SAN", "HONG", "MAT"};
                    String[] displayStatuses = {"CÓ SẴN", "KHÔNG CÓ SẴN", "HỎNG", "MẤT"};
                    ArrayAdapter<String> sAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, displayStatuses);
                    spStatus.setAdapter(sAdapter);
                    for (int i = 0; i < statuses.length; i++) {
                        if (statuses[i].equals(trangThai)) {
                            spStatus.setSelection(i);
                            break;
                        }
                    }

                    AlertDialog eDialog = editB.create();
                    eDialog.show();

                    btnSave.setOnClickListener(sv -> {
                        String newPos = edPos.getText().toString().trim();
                        String newNote = edNote.getText().toString().trim();
                        int selectedPos = spStatus.getSelectedItemPosition();
                        String newStatus = (selectedPos >= 0 && selectedPos < statuses.length) ? statuses[selectedPos] : "CO_SAN";

                        executorService.execute(() -> {
                            bookRepository.updateBookCopy(maQuyen, newStatus, newPos, newNote);
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "Cập nhật bản sao thành công!", Toast.LENGTH_SHORT).show();
                                eDialog.dismiss();
                                // Reload danh sách bản sao
                                btnAddCopy.post(() -> btnAddCopy.performClick()); // Mẹo để reload
                            });
                        });
                    });
                });

                // Nút xóa bản sao
                holder.btnDelete.setOnClickListener(v -> {
                    if ("DANG_MUON".equals(trangThai) || "DAT_TRUOC".equals(trangThai)) {
                        Toast.makeText(requireContext(), "Không thể xóa bản sao đang được mượn hoặc đặt trước!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    new AlertDialog.Builder(requireContext())
                        .setTitle("Xóa bản sao")
                        .setMessage("Bạn có chắc chắn muốn xóa bản sao " + maQuyenSach + "?")
                        .setPositiveButton("Xóa", (dialogInterface, i) -> {
                            executorService.execute(() -> {
                                try {
                                    bookRepository.deleteBookCopy(maQuyen);
                                    requireActivity().runOnUiThread(() -> {
                                        Toast.makeText(requireContext(), "Đã xóa bản sao!", Toast.LENGTH_SHORT).show();
                                        btnAddCopy.post(() -> btnAddCopy.performClick()); // Reload
                                    });
                                } catch (Exception e) {
                                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
                                }
                            });
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
                });
            }

            @Override
            public int getItemCount() {
                return copiesList.size();
            }

            class CopyViewHolder extends RecyclerView.ViewHolder {
                TextView txtCode, txtInfo, txtStatus;
                ImageView btnEdit, btnDelete;

                public CopyViewHolder(@NonNull View itemView) {
                    super(itemView);
                    txtCode = itemView.findViewById(R.id.txtCopyCode);
                    txtInfo = itemView.findViewById(R.id.txtCopyInfo);
                    txtStatus = itemView.findViewById(R.id.txtCopyStatus);
                    btnEdit = itemView.findViewById(R.id.btnEditCopy);
                    btnDelete = itemView.findViewById(R.id.btnDeleteCopy);
                }
            }
        }

        CopyAdapter copyAdapter = new CopyAdapter();
        rvCopies.setAdapter(copyAdapter);

        Runnable loadCopiesRunnable = () -> executorService.execute(() -> {
            List<Map<String, Object>> list = bookRepository.getBookCopies(maSach);
            copiesList.clear();
            copiesList.addAll(list);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    copyAdapter.notifyDataSetChanged();
                    loadBooks(); // Cập nhật lại số lượng ngoài màn hình chính
                });
            }
        });

        loadCopiesRunnable.run();

        AlertDialog copiesDialog = builder.create();
        copiesDialog.show();

        // Nút thêm bản sao mới
        btnAddCopy.setOnClickListener(v -> {
            String viTri = edtViTri.getText().toString().trim();
            String ghiChu = edtGhiChu.getText().toString().trim();

            executorService.execute(() -> {
                bookRepository.addBookCopy(maSach, viTri, ghiChu);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Thêm bản sao thành công!", Toast.LENGTH_SHORT).show();
                    edtViTri.setText("");
                    edtGhiChu.setText("");
                    loadCopiesRunnable.run(); // reload list
                });
            });
        });
    }

    /**
     * Adapter RecyclerView Sách
     */
    class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

        @NonNull
        @Override
        public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
            return new BookViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
            Map<String, Object> book = booksList.get(position);
            int maSach = (int) book.get("ma_sach");
            String tieuDe = (String) book.get("tieu_de");
            String tacGia = (String) book.get("tac_gia");
            String loaiSach = (String) book.get("loai_sach");
            String tenTheLoai = (String) book.get("ten_the_loai");
            int soQuyenCoSan = (int) book.get("so_quyen_co_san");
            int tongSoQuyen = (int) book.get("tong_so_quyen");

            holder.txtBookTitle.setText(tieuDe);
            holder.txtBookAuthor.setText(tacGia);
            holder.txtBookCategory.setText(tenTheLoai != null ? tenTheLoai : "Không thể loại");

            if ("CA_HAI".equals(loaiSach)) {
                holder.badgeBookType.setText("GIẤY & ONLINE");
                holder.badgeBookType.setTextColor(0xFFE040FB); // Tím hồng sáng
                holder.txtAvailability.setText("Có sẵn: " + soQuyenCoSan + " / " + tongSoQuyen + " quyển");
                holder.btnManageCopies.setVisibility(View.VISIBLE);

                if (soQuyenCoSan > 0) {
                    holder.txtAvailability.setTextColor(0xFF4CAF50); // xanh lá
                } else {
                    holder.txtAvailability.setTextColor(0xFFFF5252); // đỏ
                }
            } else if ("SACH_GIAY".equals(loaiSach)) {
                holder.badgeBookType.setText("SÁCH GIẤY");
                holder.badgeBookType.setTextColor(0xFF00B0FF);
                holder.txtAvailability.setText("Có sẵn: " + soQuyenCoSan + " / " + tongSoQuyen + " quyển");
                holder.btnManageCopies.setVisibility(View.VISIBLE);
                
                if (soQuyenCoSan > 0) {
                    holder.txtAvailability.setTextColor(0xFF4CAF50); // xanh lá
                } else {
                    holder.txtAvailability.setTextColor(0xFFFF5252); // đỏ
                }
            } else {
                holder.badgeBookType.setText("SÁCH ONLINE");
                holder.badgeBookType.setTextColor(0xFFFF9800); // cam
                holder.txtAvailability.setText("Đọc trực tuyến");
                holder.txtAvailability.setTextColor(0xFF00B0FF);
                holder.btnManageCopies.setVisibility(View.GONE);
            }

            // Nút sửa sách
            holder.btnEditBook.setOnClickListener(v -> showEditBookDialog(book));

            // Nút xóa sách
            holder.btnDeleteBook.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle("Xóa đầu sách")
                    .setMessage("Bạn có chắc chắn muốn xóa đầu sách \"" + tieuDe + "\"? Hành động này sẽ xóa tất cả bản sao liên quan.")
                    .setPositiveButton("Xóa", (dialogInterface, i) -> {
                        executorService.execute(() -> {
                            try {
                                bookRepository.deleteBook(maSach);
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "Đã xóa đầu sách!", Toast.LENGTH_SHORT).show();
                                    loadBooks();
                                });
                            } catch (Exception e) {
                                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        });
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
            });

            // Nút quản lý bản sao
            holder.btnManageCopies.setOnClickListener(v -> showManageCopiesDialog(book));
        }

        @Override
        public int getItemCount() {
            return booksList.size();
        }

        class BookViewHolder extends RecyclerView.ViewHolder {
            TextView txtBookTitle, txtBookAuthor, badgeBookType, txtBookCategory, txtAvailability;
            Button btnManageCopies;
            ImageView btnEditBook, btnDeleteBook;

            public BookViewHolder(@NonNull View itemView) {
                super(itemView);
                txtBookTitle = itemView.findViewById(R.id.txtBookTitle);
                txtBookAuthor = itemView.findViewById(R.id.txtBookAuthor);
                badgeBookType = itemView.findViewById(R.id.badgeBookType);
                txtBookCategory = itemView.findViewById(R.id.txtBookCategory);
                txtAvailability = itemView.findViewById(R.id.txtAvailability);
                btnManageCopies = itemView.findViewById(R.id.btnManageCopies);
                btnEditBook = itemView.findViewById(R.id.btnEditBook);
                btnDeleteBook = itemView.findViewById(R.id.btnDeleteBook);
            }
        }
    }
}
