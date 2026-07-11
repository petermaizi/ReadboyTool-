package com.readboy.tool;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {

    private static final String AUTHORITY = "com.readboy.parentmanager.AppContentProvider";

    private TextView tvResult;
    private TextInputEditText etPkg;
    private TabLayout tabLayout;

    private int selectedTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tvResult);
        etPkg = findViewById(R.id.etPackageName);
        tabLayout = findViewById(R.id.tabLayout);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { selectedTab = tab.getPosition(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.btnQuery).setOnClickListener(v -> query());
        findViewById(R.id.btnAdd).setOnClickListener(v -> insert());
        findViewById(R.id.btnDel).setOnClickListener(v -> delete());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> clearAll());
    }

    private Uri getUri() {
        switch (selectedTab) {
            case 0:  return Uri.parse("content://" + AUTHORITY + "/forbidden_app");
            case 1:  return Uri.parse("content://" + AUTHORITY + "/un_mall_app_state");
            default: return Uri.parse("content://" + AUTHORITY + "/user_info");
        }
    }

    private String getTabName() {
        switch (selectedTab) {
            case 0:  return "forbidden_app";
            case 1:  return "un_mall_app_state";
            default: return "user_info";
        }
    }

    // ──── 查询 ────
    private void query() {
        Uri uri = getUri();
        log("→ 查询: " + uri);
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(getTabName()).append(" ===\n");
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) {
                sb.append("null cursor → 无权限或无此 provider\n");
            } else {
                String[] cols = cursor.getColumnNames();
                sb.append("列: ").append(TextUtils.join(", ", cols)).append("\n");
                sb.append("行数: ").append(cursor.getCount()).append("\n");
                int row = 0;
                while (cursor.moveToNext() && row < 100) {
                    row++;
                    sb.append("── 行 ").append(row).append(" ──\n");
                    for (String col : cols) {
                        int idx = cursor.getColumnIndex(col);
                        if (idx >= 0) {
                            String val = cursor.getString(idx);
                            sb.append("  ").append(col).append(" = ").append(val).append("\n");
                        }
                    }
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            sb.append("SecurityException: ").append(e.getMessage()).append("\n");
            sb.append("→ Provider 有签名保护，需要同证书签名\n");
        } catch (Exception e) {
            sb.append("异常: ").append(e.getClass().getSimpleName())
              .append(": ").append(e.getMessage()).append("\n");
        }
        showResult(sb.toString());
    }

    // ──── 插入 ────
    private void insert() {
        String pkg = etPkg.getText().toString().trim();
        if (pkg.isEmpty()) {
            toast("包名不能为空");
            return;
        }
        Uri uri = getUri();
        log("→ 插入: " + uri + "  包名: " + pkg);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 插入结果 ===\n");
        try {
            ContentValues values = new ContentValues();
            values.put("package_name", pkg);
            Uri result = getContentResolver().insert(uri, values);
            sb.append("返回 URI: ").append(result).append("\n");
            if (result != null) sb.append("成功!\n");
        } catch (SecurityException e) {
            sb.append("SecurityException: ").append(e.getMessage()).append("\n");
        } catch (Exception e) {
            sb.append("异常: ").append(e.getClass().getSimpleName())
              .append(": ").append(e.getMessage()).append("\n");
        }
        showResult(sb.toString());
        query();
    }

    // ──── 删除 ────
    private void delete() {
        String pkg = etPkg.getText().toString().trim();
        Uri uri = getUri();
        log("→ 删除: " + uri + "  where=" + (pkg.isEmpty() ? "全部" : pkg));
        StringBuilder sb = new StringBuilder();
        sb.append("=== 删除结果 ===\n");
        try {
            int count;
            if (pkg.isEmpty()) {
                count = getContentResolver().delete(uri, null, null);
            } else {
                count = getContentResolver().delete(uri, "package_name=?", new String[]{pkg});
            }
            sb.append("删除了 ").append(count).append(" 行\n");
        } catch (SecurityException e) {
            sb.append("SecurityException: ").append(e.getMessage()).append("\n");
        } catch (Exception e) {
            sb.append("异常: ").append(e.getClass().getSimpleName())
              .append(": ").append(e.getMessage()).append("\n");
        }
        showResult(sb.toString());
        query();
    }

    // ──── 清空 forbidden_app ────
    private void clearAll() {
        new AlertDialog.Builder(this)
            .setTitle("确认清除")
            .setMessage("将删除 forbidden_app 中所有记录，解除全部安装限制。确定？")
            .setPositiveButton("确认", (d, w) -> doClearAll())
            .setNegativeButton("取消", null)
            .show();
    }

    private void doClearAll() {
        Uri uri = Uri.parse("content://" + AUTHORITY + "/forbidden_app");
        log("→ 清空: " + uri);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 清空结果 ===\n");
        try {
            int count = getContentResolver().delete(uri, null, null);
            sb.append("删除了 ").append(count).append(" 条记录\n");
            if (count > 0) sb.append("安装限制已解除！\n");
        } catch (SecurityException e) {
            sb.append("SecurityException: ").append(e.getMessage()).append("\n");
        } catch (Exception e) {
            sb.append("异常: ").append(e.getClass().getSimpleName())
              .append(": ").append(e.getMessage()).append("\n");
        }
        showResult(sb.toString());
        query();
    }

    private void showResult(String text) {
        tvResult.setText(text);
    }

    private void log(String msg) {
        android.util.Log.d("RBTool", msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
