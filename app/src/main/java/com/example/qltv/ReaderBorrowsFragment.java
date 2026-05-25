package com.example.qltv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderBorrowsFragment extends Fragment {

    private RecyclerView recyclerViewMyBorrows;
    private TextView txtEmpty;

    private BorrowRepository borrowRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private List<Map<String, Object>> borrowsList = new ArrayList<>();
    private MyBorrowAdapter adapter;
    private int currentReaderId = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reader_borrows, container, false);

        borrowRepository = new BorrowRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentReaderId = sharedPreferences.getInt("userId", 0);

        recyclerViewMyBorrows = view.findViewById(R.id.recyclerViewMyBorrows);
        txtEmpty = view.findViewById(R.id.txtEmptyMyBorrows);

        recyclerViewMyBorrows.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MyBorrowAdapter();
        recyclerViewMyBorrows.setAdapter(adapter);

        loadMyBorrows();

        return view;
    }

    private void loadMyBorrows() {
        executorService.execute(() -> {
            borrowsList = borrowRepository.getReaderBorrowHistory(currentReaderId);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (borrowsList.isEmpty()) {
                        txtEmpty.setVisibility(View.VISIBLE);
                        recyclerViewMyBorrows.setVisibility(View.GONE);
                    } else {
                        txtEmpty.setVisibility(View.GONE);
                        recyclerViewMyBorrows.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    /**
     * Adapter RecyclerView Phiếu mượn cá nhân Độc giả
     */
    class MyBorrowAdapter extends RecyclerView.Adapter<MyBorrowAdapter.MyBorrowViewHolder> {

        @NonNull
        @Override
        public MyBorrowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_borrow, parent, false);
            return new MyBorrowViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyBorrowViewHolder holder, int position) {
            Map<String, Object> b = borrowsList.get(position);
            String title = (String) b.get("tieu_de");
            String author = (String) b.get("tac_gia");
            String loaiSach = (String) b.get("loai_sach");
            String copyCode = (String) b.get("ma_quyen_sach");
            String ngayMuon = (String) b.get("ngay_muon");
            String ngayHenTra = (String) b.get("ngay_hen_tra");
            String ngayTraThuc = (String) b.get("ngay_tra_thuc");
            String trangThai = (String) b.get("trang_thai_phieu");
            double fine = (double) b.get("tien_phat");
            String url = (String) b.get("url_tai_lieu");

            holder.txtTitle.setText(title);
            holder.txtAuthor.setText("Tác giả: " + author + (copyCode != null ? " | Bản sao: " + copyCode : ""));
            holder.txtDates.setText("Mượn: " + DateTimeUtils.formatDate(ngayMuon) + " | Hạn trả: " + DateTimeUtils.formatDate(ngayHenTra));

            // Xử lý nạp trạng thái & tiền phạt
            holder.badgeStatus.setText(trangThai);
            holder.txtReturnDate.setVisibility(View.GONE);
            holder.txtFine.setVisibility(View.GONE);
            holder.btnReadOnline.setVisibility(View.GONE);

            if ("DANG_MUON".equals(trangThai)) {
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
                }

                if ("SACH_ONLINE".equals(loaiSach) || "CA_HAI".equals(loaiSach)) {
                    holder.btnReadOnline.setVisibility(View.VISIBLE);
                }
            } else {
                holder.badgeStatus.setText("ĐÃ TRẢ");
                holder.badgeStatus.setTextColor(0xFF4CAF50);
                holder.badgeStatus.setBackgroundColor(0x1B4CAF50);
                holder.txtReturnDate.setText("Ngày trả thực: " + DateTimeUtils.formatDate(ngayTraThuc));
                holder.txtReturnDate.setVisibility(View.VISIBLE);

                if (fine > 0) {
                    holder.txtFine.setText("Đã nộp phạt: " + String.format(Locale.getDefault(), "%,.0f", fine) + " VND");
                    holder.txtFine.setVisibility(View.VISIBLE);
                }
            }

            // Đọc online
            holder.btnReadOnline.setOnClickListener(v -> {
                if (url != null && !url.isEmpty()) {
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(browserIntent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Mở tài liệu: " + title, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return borrowsList.size();
        }

        class MyBorrowViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, badgeStatus, txtAuthor, txtDates, txtReturnDate, txtFine;
            Button btnReadOnline;

            public MyBorrowViewHolder(@NonNull View itemView) {
                super(itemView);
                txtTitle = itemView.findViewById(R.id.txtMyBorrowBookTitle);
                badgeStatus = itemView.findViewById(R.id.badgeMyBorrowStatus);
                txtAuthor = itemView.findViewById(R.id.txtMyBorrowAuthor);
                txtDates = itemView.findViewById(R.id.txtMyBorrowDates);
                txtReturnDate = itemView.findViewById(R.id.txtMyBorrowReturnDate);
                txtFine = itemView.findViewById(R.id.txtMyBorrowFine);
                btnReadOnline = itemView.findViewById(R.id.btnMyBorrowReadOnline);
            }
        }
    }
}
