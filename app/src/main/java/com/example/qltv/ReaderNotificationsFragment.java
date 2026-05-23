package com.example.qltv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

public class ReaderNotificationsFragment extends Fragment {

    private TextView btnMarkAllRead;
    private RecyclerView recyclerViewNotifs;
    private TextView txtEmpty;

    private NotificationRepository notificationRepository;
    private ExecutorService executorService;
    private SharedPreferences sharedPreferences;

    private List<Map<String, Object>> notifsList = new ArrayList<>();
    private NotifAdapter adapter;
    private int currentReaderId = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reader_notifications, container, false);

        notificationRepository = new NotificationRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();
        sharedPreferences = requireContext().getSharedPreferences("QLTV_PREF", Context.MODE_PRIVATE);
        currentReaderId = sharedPreferences.getInt("userId", 0);

        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead);
        recyclerViewNotifs = view.findViewById(R.id.recyclerViewNotifs);
        txtEmpty = view.findViewById(R.id.txtEmptyNotif);

        recyclerViewNotifs.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotifAdapter();
        recyclerViewNotifs.setAdapter(adapter);

        btnMarkAllRead.setOnClickListener(v -> markAllRead());

        loadNotifications();

        return view;
    }

    private void loadNotifications() {
        executorService.execute(() -> {
            notifsList = notificationRepository.getNotifications(currentReaderId, 50, false);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (notifsList.isEmpty()) {
                        txtEmpty.setVisibility(View.VISIBLE);
                        recyclerViewNotifs.setVisibility(View.GONE);
                    } else {
                        txtEmpty.setVisibility(View.GONE);
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

    /**
     * Adapter RecyclerView Thông báo Độc giả
     */
    class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.NotifViewHolder> {

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

            // Hiện chấm đỏ nếu chưa đọc
            if (daDoc == 0) {
                holder.viewDot.setVisibility(View.VISIBLE);
            } else {
                holder.viewDot.setVisibility(View.GONE);
            }

            // Click vào hàng -> đánh dấu đã đọc
            holder.itemView.setOnClickListener(v -> {
                if (daDoc == 0) {
                    executorService.execute(() -> {
                        notificationRepository.markNotificationAsRead(maThongBao);
                        requireActivity().runOnUiThread(this::loadNotifications); // Reload thông qua hàm ngoài
                    });
                }
            });

            // Nút xóa thông báo
            holder.btnDelete.setOnClickListener(v -> {
                executorService.execute(() -> {
                    notificationRepository.deleteNotification(maThongBao);
                    requireActivity().runOnUiThread(this::loadNotifications);
                });
            });
        }

        private void loadNotifications() {
            ReaderNotificationsFragment.this.loadNotifications();
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
