package com.example.qltv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

public class RequestManagementFragment extends Fragment {

    private Button btnTabBorrow, btnTabCard;
    private RecyclerView recyclerViewRequests;
    private TextView txtEmptyRequest;

    private RequestRepository requestRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private List<Map<String, Object>> borrowRequestsList = new ArrayList<>();
    private List<Map<String, Object>> cardRequestsList = new ArrayList<>();
    private boolean isBorrowTab = true;
    private int currentStaffId = 0;

    private BorrowReqAdapter borrowAdapter;
    private CardReqAdapter cardAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_request_management, container, false);

        requestRepository = new RequestRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentStaffId = sharedPreferences.getInt("userId", 0);

        btnTabBorrow = view.findViewById(R.id.btnTabBorrow);
        btnTabCard = view.findViewById(R.id.btnTabCard);
        recyclerViewRequests = view.findViewById(R.id.recyclerViewRequests);
        txtEmptyRequest = view.findViewById(R.id.txtEmptyRequest);

        recyclerViewRequests.setLayoutManager(new LinearLayoutManager(requireContext()));
        borrowAdapter = new BorrowReqAdapter();
        cardAdapter = new CardReqAdapter();

        btnTabBorrow.setOnClickListener(v -> switchTab(true));
        btnTabCard.setOnClickListener(v -> switchTab(false));

        switchTab(true);

        return view;
    }

    private void switchTab(boolean isBorrow) {
        isBorrowTab = isBorrow;
        if (isBorrowTab) {
            btnTabBorrow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00B0FF));
            btnTabBorrow.setTextColor(0xFFFFFFFF);
            btnTabCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
            btnTabCard.setTextColor(0xFFC5C5D2);
            recyclerViewRequests.setAdapter(borrowAdapter);
            loadBorrowRequests();
        } else {
            btnTabCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00B0FF));
            btnTabCard.setTextColor(0xFFFFFFFF);
            btnTabBorrow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
            btnTabBorrow.setTextColor(0xFFC5C5D2);
            recyclerViewRequests.setAdapter(cardAdapter);
            loadCardRequests();
        }
    }

    private void loadBorrowRequests() {
        executorService.execute(() -> {
            borrowRequestsList = requestRepository.getAllBorrowRequests(null, null);
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
            cardRequestsList = requestRepository.getAllCardRequests(null, null);
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
     * Dialog từ chối yêu cầu
     */
    private void showRejectDialog(int maYeuCau, boolean isBorrow) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_copy, null); // Mẹo dùng chung layout
        builder.setView(view);

        TextView title = view.findViewById(android.R.id.text1); // wait
        // Hoặc tạo dialog nhập nhanh
        EditText edtReason = new EditText(requireContext());
        edtReason.setHint("Nhập lý do từ chối...");

        new AlertDialog.Builder(requireContext())
            .setTitle("Từ chối yêu cầu")
            .setView(edtReason)
            .setPositiveButton("Xác nhận từ chối", (dialogInterface, i) -> {
                String reason = edtReason.getText().toString().trim();
                if (reason.isEmpty()) {
                    Toast.makeText(requireContext(), "Lý do từ chối không được trống!", Toast.LENGTH_SHORT).show();
                    return;
                }

                executorService.execute(() -> {
                    try {
                        if (isBorrow) {
                            requestRepository.rejectBorrowRequest(maYeuCau, currentStaffId, reason);
                        } else {
                            requestRepository.rejectCardRequest(maYeuCau, currentStaffId, reason);
                        }
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Đã từ chối yêu cầu!", Toast.LENGTH_SHORT).show();
                            if (isBorrow) loadBorrowRequests();
                            else loadCardRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    /**
     * Adapter RecyclerView Yêu cầu Mượn Sách
     */
    class BorrowReqAdapter extends RecyclerView.Adapter<BorrowReqAdapter.BorrowReqViewHolder> {

        @NonNull
        @Override
        public BorrowReqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request_borrow, parent, false);
            return new BorrowReqViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BorrowReqViewHolder holder, int position) {
            Map<String, Object> r = borrowRequestsList.get(position);
            int maYeuCau = (int) r.get("ma_yeu_cau");
            String readerName = (String) r.get("ten_doc_gia");
            String readerCode = (String) r.get("ma_doc_gia");
            String bookTitle = (String) r.get("tieu_de");
            String loaiSach = (String) r.get("loai_sach");
            int proposedDays = (int) r.get("so_ngay_muon_de_xuat");
            String reqDate = (String) r.get("ngay_yeu_cau");
            String status = (String) r.get("trang_thai");
            String refuseReason = (String) r.get("ly_do_tu_choi");

            holder.txtReader.setText(readerName + " (" + readerCode + ")");
            holder.txtBook.setText("Mượn: " + bookTitle + " (" + ("SACH_ONLINE".equals(loaiSach) ? "Ebook" : "Sách giấy") + ")");
            holder.txtProposed.setText("Đề xuất: " + proposedDays + " ngày | Ghi chú: " + (r.get("ghi_chu") != null ? r.get("ghi_chu") : "Không"));
            holder.txtDate.setText("Ngày yêu cầu: " + reqDate);

            // Nạp trạng thái
            String displayStatus = status;
            if ("CHO_DUYET".equals(status)) {
                displayStatus = "CHỜ DUYỆT";
            } else if ("CHO_LAY_SACH".equals(status)) {
                displayStatus = "CHỜ LẤY SÁCH";
            } else if ("DA_LAY".equals(status)) {
                displayStatus = "ĐÃ LẤY";
            } else if ("TU_CHOI".equals(status)) {
                displayStatus = "BỊ TỪ CHỐI";
            } else if ("DA_HUY".equals(status)) {
                displayStatus = "ĐÃ HỦY";
            }
            holder.badgeStatus.setText(displayStatus);
            holder.txtRefuseReason.setVisibility(View.GONE);

            if ("CHO_DUYET".equals(status)) {
                holder.badgeStatus.setTextColor(0xFFFF9800);
                holder.btnApprove.setVisibility(View.VISIBLE);
                holder.btnReject.setVisibility(View.VISIBLE);
                holder.btnConfirmPickup.setVisibility(View.GONE);
            } else if ("CHO_LAY_SACH".equals(status)) {
                holder.badgeStatus.setTextColor(0xFF00B0FF);
                holder.btnApprove.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnConfirmPickup.setVisibility(View.VISIBLE);
            } else {
                holder.btnApprove.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnConfirmPickup.setVisibility(View.GONE);

                if ("DA_LAY".equals(status)) {
                    holder.badgeStatus.setTextColor(0xFF4CAF50);
                } else {
                    holder.badgeStatus.setTextColor(0xFFFF5252);
                    if ("TU_CHOI".equals(status) && refuseReason != null) {
                        holder.txtRefuseReason.setText("Lý do từ chối: " + refuseReason);
                        holder.txtRefuseReason.setVisibility(View.VISIBLE);
                    }
                }
            }

            // Phê duyệt
            holder.btnApprove.setOnClickListener(v -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.approveBorrowRequest(maYeuCau, currentStaffId, proposedDays);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Phê duyệt mượn sách thành công!", Toast.LENGTH_SHORT).show();
                            loadBorrowRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            });

            // Từ chối
            holder.btnReject.setOnClickListener(v -> showRejectDialog(maYeuCau, true));

            // Xác nhận lấy sách
            holder.btnConfirmPickup.setOnClickListener(v -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.confirmBookPickup(maYeuCau, currentStaffId);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Xác nhận độc giả đã lấy sách thành công!", Toast.LENGTH_SHORT).show();
                            loadBorrowRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return borrowRequestsList.size();
        }

        class BorrowReqViewHolder extends RecyclerView.ViewHolder {
            TextView txtReader, badgeStatus, txtBook, txtProposed, txtDate, txtRefuseReason;
            Button btnReject, btnApprove, btnConfirmPickup;

            public BorrowReqViewHolder(@NonNull View itemView) {
                super(itemView);
                txtReader = itemView.findViewById(R.id.txtReqReader);
                badgeStatus = itemView.findViewById(R.id.badgeReqStatus);
                txtBook = itemView.findViewById(R.id.txtReqBook);
                txtProposed = itemView.findViewById(R.id.txtReqProposed);
                txtDate = itemView.findViewById(R.id.txtReqDate);
                txtRefuseReason = itemView.findViewById(R.id.txtReqRefuseReason);
                btnReject = itemView.findViewById(R.id.btnRejectReq);
                btnApprove = itemView.findViewById(R.id.btnApproveReq);
                btnConfirmPickup = itemView.findViewById(R.id.btnConfirmReqPickup);
            }
        }
    }

    /**
     * Adapter RecyclerView Yêu cầu Cấp Thẻ Độc giả
     */
    class CardReqAdapter extends RecyclerView.Adapter<CardReqAdapter.CardReqViewHolder> {

        @NonNull
        @Override
        public CardReqViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_request_card, parent, false);
            return new CardReqViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CardReqViewHolder holder, int position) {
            Map<String, Object> r = cardRequestsList.get(position);
            int maYeuCau = (int) r.get("ma_yeu_cau");
            String readerName = (String) r.get("ten_doc_gia");
            String readerCode = (String) r.get("ma_doc_gia");
            String reqDate = (String) r.get("ngay_yeu_cau");
            String status = (String) r.get("trang_thai");
            int daNhan = (int) r.get("da_nhan");

            holder.txtReader.setText(readerName + " (" + readerCode + ")");
            holder.txtDate.setText("Ngày yêu cầu: " + reqDate);

            String displayStatus = status;
            if ("CHO_DUYET".equals(status)) {
                displayStatus = "CHỜ DUYỆT";
            } else if ("DANG_XU_LY".equals(status)) {
                displayStatus = "ĐANG XỬ LÝ";
            } else if ("DA_IN".equals(status)) {
                displayStatus = "ĐÃ IN";
            } else if ("TU_CHOI".equals(status)) {
                displayStatus = "BỊ TỪ CHỐI";
            } else if ("DA_HUY".equals(status)) {
                displayStatus = "ĐÃ HỦY";
            }
            holder.badgeStatus.setText(displayStatus);

            // Bật tắt nút theo trạng thái
            if ("CHO_DUYET".equals(status)) {
                holder.badgeStatus.setTextColor(0xFFFF9800);
                holder.btnApprove.setVisibility(View.VISIBLE);
                holder.btnReject.setVisibility(View.VISIBLE);
                holder.btnPrint.setVisibility(View.GONE);
                holder.btnConfirmPickup.setVisibility(View.GONE);
            } else if ("DANG_XU_LY".equals(status)) {
                holder.badgeStatus.setTextColor(0xFF00B0FF);
                holder.btnApprove.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnPrint.setVisibility(View.VISIBLE);
                holder.btnConfirmPickup.setVisibility(View.GONE);
            } else if ("DA_IN".equals(status)) {
                holder.badgeStatus.setTextColor(0xFF4CAF50);
                holder.btnApprove.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnPrint.setVisibility(View.GONE);

                if (daNhan == 1) {
                    holder.badgeStatus.setText("ĐÃ GIAO THẺ");
                    holder.badgeStatus.setTextColor(0xFF2E2C4D);
                    holder.btnConfirmPickup.setVisibility(View.GONE);
                } else {
                    holder.badgeStatus.setText("ĐÃ IN (CHỜ LẤY)");
                    holder.btnConfirmPickup.setVisibility(View.VISIBLE);
                }
            } else {
                holder.badgeStatus.setTextColor(0xFFFF5252);
                holder.btnApprove.setVisibility(View.GONE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnPrint.setVisibility(View.GONE);
                holder.btnConfirmPickup.setVisibility(View.GONE);
            }

            // Tiếp nhận
            holder.btnApprove.setOnClickListener(v -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.approveCardRequest(maYeuCau, currentStaffId);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Tiếp nhận in thẻ thành công!", Toast.LENGTH_SHORT).show();
                            loadCardRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            });

            // Từ chối
            holder.btnReject.setOnClickListener(v -> showRejectDialog(maYeuCau, false));

            // Đã in thẻ
            holder.btnPrint.setOnClickListener(v -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.markCardPrinted(maYeuCau, currentStaffId);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Đánh dấu in thẻ độc giả xong!", Toast.LENGTH_SHORT).show();
                            loadCardRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            });

            // Đã giao thẻ
            holder.btnConfirmPickup.setOnClickListener(v -> {
                executorService.execute(() -> {
                    try {
                        requestRepository.confirmCardPickup(maYeuCau, currentStaffId);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Đã giao thẻ cho độc giả thành công!", Toast.LENGTH_SHORT).show();
                            loadCardRequests();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return cardRequestsList.size();
        }

        class CardReqViewHolder extends RecyclerView.ViewHolder {
            TextView txtReader, badgeStatus, txtDate;
            Button btnReject, btnApprove, btnPrint, btnConfirmPickup;

            public CardReqViewHolder(@NonNull View itemView) {
                super(itemView);
                txtReader = itemView.findViewById(R.id.txtCardReqReader);
                badgeStatus = itemView.findViewById(R.id.badgeCardReqStatus);
                txtDate = itemView.findViewById(R.id.txtCardReqDate);
                btnReject = itemView.findViewById(R.id.btnRejectCardReq);
                btnApprove = itemView.findViewById(R.id.btnApproveCardReq);
                btnPrint = itemView.findViewById(R.id.btnPrintCardReq);
                btnConfirmPickup = itemView.findViewById(R.id.btnConfirmCardPickup);
            }
        }
    }
}
