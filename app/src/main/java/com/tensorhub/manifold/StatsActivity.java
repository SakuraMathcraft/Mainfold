package com.tensorhub.manifold;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.tensorhub.manifold.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

import android.content.Intent;
import android.net.Uri;

public class StatsActivity extends AppCompatActivity {

    private TextView emptyText;
    private MaterialButton clearBtn;
    private MaterialButton exportBtn;
    private LinearLayout cardsContainer;
    private File historyFile;

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Uri uri = result.getData().getData();
                    try {
                        OutputStream out = getContentResolver().openOutputStream(uri);
                        byte[] data = readFileBytes(historyFile);
                        if (out != null && data != null) {
                            out.write(data);
                            out.close();
                            Toast.makeText(this, "已成功导出轨迹", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        emptyText = findViewById(R.id.emptyText);
        clearBtn = findViewById(R.id.clearBtn);
        exportBtn = findViewById(R.id.exportBtn);
        cardsContainer = findViewById(R.id.cardsContainer);
        historyFile = new File(getExternalFilesDir(null), "history.json");

        exportBtn.setOnClickListener(v -> {
            if (!historyFile.exists()) {
                Toast.makeText(this, "无历史记录可导出", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "exported_track_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new java.util.Date()) + ".json");

            exportLauncher.launch(intent);
        });

        clearBtn.setOnClickListener(v -> {
            if (!historyFile.exists()) {
                Toast.makeText(this, "无历史记录可清除", Toast.LENGTH_SHORT).show();
                return;
            }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("确认清除历史记录？")
                    .setMessage("这将删除所有已保存的轨迹数据且无法恢复。")
                    .setPositiveButton("清除", (d, w) -> {
                        if (historyFile.exists() && historyFile.delete()) {
                            cardsContainer.removeAllViews();
                            showEmpty(true);
                            Toast.makeText(this, "历史记录已清除", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "清除失败或无历史记录", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        loadStatistics();
    }

    private void showEmpty(boolean show) {
        emptyText.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void addRow(LinearLayout rows, int iconRes, String label, String value) {
        View row = getLayoutInflater().inflate(R.layout.layout_stats_row, rows, false);
        android.widget.ImageView icon = row.findViewById(R.id.rowIcon);
        TextView tvLabel = row.findViewById(R.id.rowLabel);
        TextView tvValue = row.findViewById(R.id.rowValue);
        icon.setImageResource(iconRes);
        tvLabel.setText(label);
        tvValue.setText(value);
        rows.addView(row);
    }

    private View createCard(String title) {
        View card = getLayoutInflater().inflate(R.layout.layout_stats_card, cardsContainer, false);
        TextView cardTitle = card.findViewById(R.id.cardTitle);
        cardTitle.setText(title);
        return card;
    }

    private void loadStatistics() {
        if (!historyFile.exists()) {
            cardsContainer.removeAllViews();
            showEmpty(true);
            return;
        }

        try {
            FileInputStream in = new FileInputStream(historyFile);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();

            String json = out.toString("UTF-8");
            JSONArray array = new JSONArray(json);

            double totalArea = 0;
            double totalDistance = 0;
            int totalSteps = 0;
            int totalRealSteps = 0;

            showEmpty(false);
            cardsContainer.removeAllViews();

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                double area = obj.optDouble("area", 0);
                double dist = obj.optDouble("distance", 0);
                int steps = obj.optInt("steps", 0);
                int realSteps = obj.optInt("realSteps", -1);
                String time = obj.optString("time", "未知时间");

                totalArea += area;
                totalDistance += dist;
                totalSteps += steps;
                if (realSteps >= 0) totalRealSteps += realSteps;

                View card = createCard("第 " + (i + 1) + " 条轨迹");
                LinearLayout rows = card.findViewById(R.id.rowsContainer);

                addRow(rows, R.drawable.ic_time, "记录时间", time);
                addRow(rows, R.drawable.ic_area, "面积", String.format(Locale.getDefault(), "%.2f㎡", area));
                addRow(rows, R.drawable.ic_distance, "周长", String.format(Locale.getDefault(), "%.2fm", dist));
                addRow(rows, R.drawable.ic_steps, "步长数", String.valueOf(steps));
                addRow(rows, R.drawable.ic_footprint, "真实步数", realSteps >= 0 ? String.valueOf(realSteps) : "暂无");

                MaterialButton action = card.findViewById(R.id.cardAction);
                action.setVisibility(View.VISIBLE);
                action.setText("导出本条轨迹");
                int finalI = i;
                action.setOnClickListener(v -> exportTrack(array, finalI));

                cardsContainer.addView(card);
            }

            // 汇总卡片放在最上面
            View summary = createCard("统计概览");
            LinearLayout summaryRows = summary.findViewById(R.id.rowsContainer);
            addRow(summaryRows, R.drawable.ic_list, "总记录数", String.valueOf(array.length()));
            addRow(summaryRows, R.drawable.ic_steps, "总步长数", String.valueOf(totalSteps));
            addRow(summaryRows, R.drawable.ic_footprint, "总真实步数", String.valueOf(totalRealSteps));
            addRow(summaryRows, R.drawable.ic_distance, "总距离", String.format(Locale.getDefault(), "%.2f 米", totalDistance));
            addRow(summaryRows, R.drawable.ic_area, "总面积", String.format(Locale.getDefault(), "%.2f 平方米", totalArea));
            cardsContainer.addView(summary, 0);

        } catch (Exception e) {
            showEmpty(true);
            Toast.makeText(this, "数据读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportTrack(JSONArray array, int index) {
        try {
            JSONObject record = array.getJSONObject(index);
            JSONArray path = record.optJSONArray("path");

            if (path == null || path.length() == 0) {
                Toast.makeText(this, "当前记录无轨迹数据", Toast.LENGTH_SHORT).show();
                return;
            }

            // 启动文件选择器，允许用户选择文件保存路径
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "export_track_" + (index + 1) + ".json");

            exportLauncher.launch(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readFileBytes(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            fis.close();
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
