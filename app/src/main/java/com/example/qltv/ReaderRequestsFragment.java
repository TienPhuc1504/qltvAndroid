package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
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

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderRequestsFragment extends Fragment {

    private Button btnTabBorrow, btnTabCard;
    private MaterialButton btnNewCardRequest;
    private RecyclerView recyclerViewRequests;
    private TextView txtEmptyRequest;

    private RequestRepository requestRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private List<Map<String, Object>> borrowRequestsList = new ArrayList<>();
    private List<Map<String, Object>> cardRequestsList = new ArrayList<>();
    private boolean isBorrowTab = true;
    private int currentReaderId = 0;

    private MyBorrowReqAdapter borrowAdapter;
    private MyCardReqAdapter cardAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reader_requests, container, false);

        requestRepository = new RequestRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentReaderId = sharedPreferences.getInt("userId", 0);

        btnTabBorrow = view.findViewById(R.id.btnTabBorrowReader);
        btnTabCard = view.findViewById(R.id.btnTabCardReader);
        btnNewCardRequest = view.findViewById(R.id.btnNewCardRequest);
        recyclerViewRequests = view.findViewById(R.id.recyclerViewMyRequests);
        txtEmptyRequest = view.findViewById(R.id.txtEmptyMyRequest);

        recyclerViewRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        borrowAdapter = new MyBorrowReqAdapter();
        cardAdapter = new MyCardReqAdapter();

        btnTabBorrow.setOnClickListener(v -> switchTab(true));
        btnTabCard.setOnClickListener(v -> switchTab(false));

        btnNewCardRequest.setOnClickListener(v -> performCardRequest());

        switchTab(true);

        return view;
    }

    private void switchTab(boolean isBorrow) {
        isBorrowTab = isBorrow;
        if (isBorrowTab) {
            btnTabBorrow.setBackgroundColor(0xFF6C63FF);
            btnTabBorrow.setTextColor(0xFFFFFFFF);
            btnTabCard.setBackgroundColor(0x00000000);
            btnTabCard.setTextColor(0xFFB0A8B9);
            btnNewCardRequest.setVisibility(View.GONE);
            recyclerViewRequests.setAdapter(borrowAdapter);
            loadBorrowRequests();
        } else {
            btnTabCard.setBackgroundColor(0xFF6C63FF);
            btnTabCard.setTextColor(0xFFFFFFFF);
            btnTabBorrow.setBackgroundColor(0x00000000);
            btnTabBorrow.setTextColor(0xFFB0A8B9);
            btnNewCardRequest.setVisibility(View.VISIBLE);
            recyclerViewRequests.setAdapter(cardAdapter);
            loadCardRequests();
        }
    }

    private void loadBorrowRequests() {
        executorService.execute(() -> {
            borrowRequestsList = requestRepository.getReaderBorrowRequests(currentReaderId);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    borrowAdapter.notifyDataSetChanged();
                    toggleEmptyState(borrowRequestsList.isEmpty());
                });
            }
        });
    }

    private void loadCardRequests() {
        executorService.execute(() -> {
            cardRequestsList = requestRepository.getReaderCardRequests(currentReaderId);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    cardAdapter.notifyDataSetChanged();
                    toggleEmptyState(cardRequestsList.isEmpty());
                });
            }
        });
    }

    private void toggleEmptyState(boolean isEmpty) {
        if (isEmpty) {
            txtEmptyRequest.setVisibility(View.VISIBLE);
            recyclerViewRequests.setVisibility(View.GONE);
        } else {
            txtEmptyRequest.setVisibility(View.GONE);
            recyclerViewRequests.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Gửi yêu cầu in/cấp thẻ độc giả
     */
    private void performCardRequest() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Yêu cầu in thẻ độc giả")
            .setMessage("Gửi yêu cầu in hoặc cấp lại thẻ độc giả lên Hệ thống thư viện?")
            .setPositiveButton("Gửi yêu cầu", (dialogInterface, i) -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.createCardRequest(currentReaderId);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Gửi yêu cầu in thẻ thành công! Nhân viên sẽ tiếp nhận và phản hồi sớm nhất.", Toast.LENGTH_LONG).show();
                            loadCardRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    /**
     * Hủy yêu cầu mượn
     */
    private void performCancelRequest(int maYeuCau) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Hủy yêu cầu mượn")
            .setMessage("Bạn có chắc chắn muốn hủy yêu cầu mượn cuốn sách này?")
            .setPositiveButton("Hủy yêu cầu", (dialogInterface, i) -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.cancelBorrowRequest(maYeuCau, currentReaderId);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Đã hủy yêu cầu mượn sách thành công!", Toast.LENGTH_SHORT).show();
                            loadBorrowRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("Đóng", null)
            .show();
    }

    /**
     * Adapter RecyclerView Yêu cầu Mượn Sách cá nhân
     */
    class MyBorrowReqAdapter extends RecyclerView.Adapter<MyBorrowReqAdapter.MyBorrowReqViewHolder> {

        @NonNull
        @Override
        public MyBorrowReqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_request_borrow, parent, false);
            return new MyBorrowReqViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyBorrowReqViewHolder holder, int position) {
            Map<String, Object> r = borrowRequestsList.get(position);
            int maYeuCau = (int) r.get("ma_yeu_cau");
            String title = (String) r.get("tieu_de");
            int proposedDays = (int) r.get("so_ngay_muon_de_xuat");
            String reqDate = (String) r.get("ngay_yeu_cau");
            String status = (String) r.get("trang_thai");
            String reason = (String) r.get("ly_do_tu_choi");

            holder.txtBook.setText("Mượn: " + title);
            holder.txtDays.setText("Đề xuất: " + proposedDays + " ngày | Ghi chú: " + (r.get("ghi_chu") != null ? r.get("ghi_chu") : "Không"));
            holder.txtDate.setText("Ngày gửi: " + reqDate);

            holder.badgeStatus.setText(status);
            holder.txtReason.setVisibility(View.GONE);

            // Bật tắt nút hủy
            if ("CHO_DUYET".equals(status) || "CHO_LAY_SACH".equals(status)) {
                holder.btnCancel.setVisibility(View.VISIBLE);
                
                if ("CHO_DUYET".equals(status)) {
                    holder.badgeStatus.setTextColor(0xFFFF9800); // cam
                } else {
                    holder.badgeStatus.setTextColor(0xFF00B0FF); // xanh dương
                }
            } else {
                holder.btnCancel.setVisibility(View.GONE);
                
                if ("DA_LAY".equals(status)) {
                    holder.badgeStatus.setTextColor(0xFF4CAF50); // xanh lá
                } else {
                    holder.badgeStatus.setTextColor(0xFFFF5252); // đỏ
                    if ("TU_CHOI".equals(status) && reason != null) {
                        holder.txtReason.setText("Lý do từ chối: " + reason);
                        holder.txtReason.setVisibility(View.VISIBLE);
                    }
                }
            }

            holder.btnCancel.setOnClickListener(v -> performCancelRequest(maYeuCau));
        }

        @Override
        public int getItemCount() {
            return borrowRequestsList.size();
        }

        class MyBorrowReqViewHolder extends RecyclerView.ViewHolder {
            TextView txtBook, badgeStatus, txtDays, txtDate, txtReason;
            Button btnCancel;

            public MyBorrowReqViewHolder(@NonNull View itemView) {
                super(itemView);
                txtBook = itemView.findViewById(R.id.txtMyReqBook);
                badgeStatus = itemView.findViewById(R.id.badgeMyReqStatus);
                txtDays = itemView.findViewById(R.id.txtMyReqDays);
                txtDate = itemView.findViewById(R.id.txtMyReqDate);
                txtReason = itemView.findViewById(R.id.txtMyReqReason);
                btnCancel = itemView.findViewById(R.id.btnCancelMyReq);
            }
        }
    }

    /**
     * Adapter RecyclerView Yêu cầu Thẻ cá nhân
     */
    class MyCardReqAdapter extends RecyclerView.Adapter<MyCardReqAdapter.MyCardReqViewHolder> {

        @NonNull
        @Override
        public MyCardReqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_request_card, parent, false);
            return new MyCardReqViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyCardReqViewHolder holder, int position) {
            Map<String, Object> r = cardRequestsList.get(position);
            String date = (String) r.get("ngay_yeu_cau");
            String status = (String) r.get("trang_thai");
            int daNhan = (int) r.get("da_nhan");
            String reason = (String) r.get("ly_do_tu_choi");

            holder.txtDate.setText("Ngày yêu cầu: " + date);
            holder.badgeStatus.setText(status);
            
            holder.txtPickup.setVisibility(View.GONE);
            holder.txtReason.setVisibility(View.GONE);

            if ("CHO_DUYET".equals(status)) {
                holder.badgeStatus.setTextColor(0xFFFF9800);
            } else if ("DANG_XU_LY".equals(status)) {
                holder.badgeStatus.setTextColor(0xFF00B0FF);
            } else if ("DA_IN".equals(status)) {
                holder.badgeStatus.setTextColor(0xFF4CAF50);
                if (daNhan == 1) {
                    holder.badgeStatus.setText("ĐÃ GIAO THẺ");
                    holder.badgeStatus.setTextColor(0xFF2E2C4D);
                    holder.txtPickup.setText("Đã nhận thẻ độc giả vật lý tại thư viện!");
                    holder.txtPickup.setVisibility(View.VISIBLE);
                } else {
                    holder.badgeStatus.setText("ĐÃ IN (CHỜ LẤY)");
                    holder.txtPickup.setText("Mời bạn đến quầy thư viện lấy thẻ vật lý!");
                    holder.txtPickup.setVisibility(View.VISIBLE);
                }
            } else {
                holder.badgeStatus.setTextColor(0xFFFF5252);
                if ("TU_CHOI".equals(status) && reason != null) {
                    holder.txtReason.setText("Lý do từ chối: " + reason);
                    holder.txtReason.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return cardRequestsList.size();
        }

        class MyCardReqViewHolder extends RecyclerView.ViewHolder {
            TextView badgeStatus, txtDate, txtPickup, txtReason;

            public MyCardReqViewHolder(@NonNull View itemView) {
                super(itemView);
                badgeStatus = itemView.findViewById(R.id.badgeMyCardReqStatus);
                txtDate = itemView.findViewById(R.id.txtMyCardReqDate);
                txtPickup = itemView.findViewById(R.id.txtMyCardReqPickup);
                txtReason = itemView.findViewById(R.id.txtMyCardReqReason);
            }
        }
    }
}
