package com.example.qltv;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment {

    private TextView txtTotalBooks, txtTotalBorrows, txtOverdue, txtFines;
    private LinearLayout layoutCategoryStatsContainer, layoutTopBooksContainer;

    private BorrowRepository borrowRepository;
    private ExecutorService executorService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);

        borrowRepository = new BorrowRepository(requireContext());
        executorService = Executors.newSingleThreadExecutor();

        txtTotalBooks = view.findViewById(R.id.txtStatTotalBooks);
        txtTotalBorrows = view.findViewById(R.id.txtStatTotalBorrows);
        txtOverdue = view.findViewById(R.id.txtStatOverdue);
        txtFines = view.findViewById(R.id.txtStatFines);
        
        layoutCategoryStatsContainer = view.findViewById(R.id.layoutCategoryStatsContainer);
        layoutTopBooksContainer = view.findViewById(R.id.layoutTopBooksContainer);

        loadStatistics();

        return view;
    }

    private void loadStatistics() {
        executorService.execute(() -> {
            Map<String, Object> stats = borrowRepository.getLibraryStatistics();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    // 1. Gán số liệu Widget
                    int totalBooks = stats.containsKey("total_books") ? (int) stats.get("total_books") : 0;
                    int totalBorrows = stats.containsKey("total_borrows") ? (int) stats.get("total_borrows") : 0;
                    int overdueCount = stats.containsKey("overdue_count") ? (int) stats.get("overdue_count") : 0;
                    double totalFines = stats.containsKey("total_fines") ? (double) stats.get("total_fines") : 0.0;

                    txtTotalBooks.setText(String.valueOf(totalBooks));
                    txtTotalBorrows.setText(String.valueOf(totalBorrows));
                    txtOverdue.setText(String.valueOf(overdueCount));
                    txtFines.setText(String.format(Locale.getDefault(), "%,.0f VND", totalFines));

                    // 2. Điền tỉ lệ thể loại
                    layoutCategoryStatsContainer.removeAllViews();
                    List<Map<String, Object>> categories = (List<Map<String, Object>>) stats.get("by_category");
                    if (categories != null) {
                        for (Map<String, Object> cat : categories) {
                            String name = (String) cat.get("category_name");
                            int count = (int) cat.get("count");
                            
                            // Tạo view con
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

                    // 3. Điền Top 10 cuốn sách
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

                            // Đổi màu top 3
                            if (rank == 1) {
                                txtRank.setTextColor(0xFFFFD700); // Gold
                            } else if (rank == 2) {
                                txtRank.setTextColor(0xFFC0C0C0); // Silver
                            } else if (rank == 3) {
                                txtRank.setTextColor(0xFFCD7F32); // Bronze
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
}
