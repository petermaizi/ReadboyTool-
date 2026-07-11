package com.readboy.tool;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String AUTHORITY = "com.readboy.parentmanager.AppContentProvider";

    private TextView tvResult;
    private TextInputEditText etPkg;
    private TabLayout tabLayout;
    private MaterialButton btnClearAll, btnClearSystem, btnGrantStorage;
    private LinearLayout providerOps;

    private int selectedTab = 0;

    private static final int STORAGE_PERMISSION_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tvResult);
        etPkg = findViewById(R.id.etPackageName);
        tabLayout = findViewById(R.id.tabLayout);
        btnClearAll = findViewById(R.id.btnClearAll);
        btnClearSystem = findViewById(R.id.btnClearSystem);
        btnGrantStorage = findViewById(R.id.btnGrantStorage);
        providerOps = findViewById(R.id.providerOps);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                onTabChanged();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.btnQuery).setOnClickListener(v -> query());
        findViewById(R.id.btnAdd).setOnClickListener(v -> insert());
        findViewById(R.id.btnDel).setOnClickListener(v -> delete());
        btnClearAll.setOnClickListener(v -> clearAll());
        btnClearSystem.setOnClickListener(v -> clearSystemConfig());
        btnGrantStorage.setOnClickListener(v -> requestStoragePermission());

        onTabChanged();
    }

    private void onTabChanged() {
        if (selectedTab == 3) {
            // 系统配置 Tab：隐藏 Provider 操作，显示存储权限按钮
            providerOps.setVisibility(View.GONE);
            findViewById(R.id.inputLayout).setVisibility(View.GONE);
            btnClearAll.setVisibility(View.GONE);
            findViewById(R.id.btnClearAll).setVisibility(View.GONE);
            btnGrantStorage.setVisibility(View.VISIBLE);
            btnClearSystem.setVisibility(View.VISIBLE);
            tvResult.setText("切换到了 系统配置\n\n点击「① 授予文件访问权限」按钮\n→ 然后点「⚠ 清空系统配置限制」");
        } else {
            providerOps.setVisibility(View.VISIBLE);
            findViewById(R.id.inputLayout).setVisibility(View.VISIBLE);
            btnClearAll.setVisibility(View.VISIBLE);
            btnGrantStorage.setVisibility(View.GONE);
            btnClearSystem.setVisibility(View.GONE);
        }
    }

    // ──── 请求文件访问权限 ────
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 用 MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                log("已有文件访问权限");
                readSystemConfig();
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, STORAGE_PERMISSION_REQUEST);
            }
        } else {
            // Android 10 及以下用 READ/WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_REQUEST);
            } else {
                readSystemConfig();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                log("权限已授予");
                readSystemConfig();
            } else {
                toast("需要文件访问权限才能读取系统配置");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                readSystemConfig();
            } else {
                toast("需要文件访问权限");
            }
        }
    }

    // ──── 读取系统配置 ────
    private void readSystemConfig() {
        log("正在读取系统配置...");
        StringBuilder sb = new StringBuilder();
        sb.append("=== 系统配置备份 ===\n");

        File confdFile = new File(Environment.getExternalStorageDirectory(), "backups/system/.confd");
        if (!confdFile.exists()) {
            confdFile = new File("/storage/emulated/0/backups/system/.confd");
        }
        if (!confdFile.exists()) {
            sb.append("未找到文件: backups/system/.confd\n");
            sb.append("尝试检查目录结构...\n");
            File backupsDir = new File(Environment.getExternalStorageDirectory(), "backups");
            listDir(backupsDir, sb, "");
            tvResult.setText(sb.toString());
            return;
        }

        sb.append("路径: ").append(confdFile.getAbsolutePath()).append("\n");
        sb.append("大小: ").append(confdFile.length()).append(" 字节\n\n");

        // 尝试作为 SQLite 数据库打开
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(confdFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            sb.append("✓ 成功以 SQLite 数据库打开\n\n");

            // 列出所有表
            Cursor tableCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            while (tableCursor.moveToNext()) {
                String tableName = tableCursor.getString(0);
                sb.append("表: ").append(tableName).append("\n");

                // 显示表结构
                Cursor pragmaCursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
                sb.append("  列: ");
                while (pragmaCursor.moveToNext()) {
                    sb.append(pragmaCursor.getString(1)).append("(")
                      .append(pragmaCursor.getString(2)).append(") ");
                }
                pragmaCursor.close();
                sb.append("\n");

                // 显示数据行
                Cursor dataCursor = db.rawQuery("SELECT * FROM " + tableName, null);
                sb.append("  行数: ").append(dataCursor.getCount()).append("\n");
                int row = 0;
                while (dataCursor.moveToNext() && row < 50) {
                    row++;
                    sb.append("  行").append(row).append(": ");
                    for (int i = 0; i < dataCursor.getColumnCount(); i++) {
                        sb.append(dataCursor.getColumnName(i)).append("=")
                          .append(dataCursor.getString(i)).append(" ");
                    }
                    sb.append("\n");
                }
                dataCursor.close();
            }
            tableCursor.close();

            // 搜索包名和白名单相关配置
            sb.append("\n=== 搜索包名/白名单相关 ===\n");
            Cursor searchCursor = db.rawQuery(
                "SELECT * FROM sqlite_master WHERE sql LIKE '%package%' OR sql LIKE '%whitelist%' " +
                "OR sql LIKE '%blacklist%' OR sql LIKE '%install%' OR sql LIKE '%allow%' OR sql LIKE '%deny%' " +
                "OR sql LIKE '%readboy%' OR sql LIKE '%permission%'", null);
            if (searchCursor.getCount() > 0) {
                while (searchCursor.moveToNext()) {
                    sb.append("匹配: ").append(searchCursor.getString(0))
                      .append(" - ").append(searchCursor.getString(3)).append("\n");
                }
            } else {
                sb.append("未找到明显匹配的表结构\n");
            }
            searchCursor.close();

            // 搜索所有数据中的包名相关值
            Cursor tableList = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            while (tableList.moveToNext()) {
                String tName = tableList.getString(0);
                Cursor colCursor = db.rawQuery("PRAGMA table_info(" + tName + ")", null);
                boolean hasValueCol = false;
                while (colCursor.moveToNext()) {
                    String colName = colCursor.getString(1);
                    if (colName.contains("value") || colName.contains("val") ||
                        colName.contains("data") || colName.contains("config")) {
                        hasValueCol = true;
                    }
                }
                colCursor.close();

                if (hasValueCol) {
                    try {
                        Cursor valCursor = db.rawQuery("SELECT * FROM " + tName, null);
                        while (valCursor.moveToNext()) {
                            for (int i = 0; i < valCursor.getColumnCount(); i++) {
                                String val = valCursor.getString(i);
                                if (val != null && (val.contains("com.") || val.contains("package") ||
                                    val.contains("install") || val.contains("whitelist") || val.contains("readboy"))) {
                                    sb.append("[").append(tName).append("] ")
                                      .append(valCursor.getColumnName(i)).append("=").append(val).append("\n");
                                }
                            }
                        }
                        valCursor.close();
                    } catch (Exception ignored) {}
                }
            }
            tableList.close();

        } catch (Exception e) {
            sb.append("✗ SQLite 打开失败: ").append(e.getMessage()).append("\n\n");
            // 尝试作为文本文件读取
            sb.append("尝试按文本文件读取...\n");
            try {
                byte[] content = new byte[(int) Math.min(confdFile.length(), 102400)];
                java.io.FileInputStream fis = new java.io.FileInputStream(confdFile);
                int read = fis.read(content);
                fis.close();
                sb.append("前 ").append(read).append(" 字节内容(无格式):\n");
                sb.append(new String(content, 0, read, "UTF-8").replaceAll("[^\\x20-\\x7E\\u4E00-\\u9FFF\\n\\r\\t]", "."));
            } catch (Exception e2) {
                sb.append("文本读取也失败: ").append(e2.getMessage()).append("\n");
            }
        } finally {
            if (db != null && db.isOpen()) db.close();
        }

        tvResult.setText(sb.toString());
    }

    private void listDir(File dir, StringBuilder sb, String indent) {
        if (dir == null || !dir.exists()) {
            sb.append(indent).append("(目录不存在) ").append(dir).append("\n");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            sb.append(indent).append("(无法列出) ").append(dir).append("\n");
            return;
        }
        for (File f : files) {
            sb.append(indent).append(f.isDirectory() ? "[DIR] " : "[FILE] ")
              .append(f.getName()).append(" (").append(f.length()).append(")\n");
        }
    }

    // ──── 清空系统配置限制 ────
    private void clearSystemConfig() {
        new AlertDialog.Builder(this)
            .setTitle("确认修改系统配置")
            .setMessage("将尝试修改 backups/system/ 下的配置文件\n\n" +
                       "注意：这可能会影响系统稳定性！\n确定继续？")
            .setPositiveButton("确认修改", (d, w) -> doClearSystemConfig())
            .setNegativeButton("取消", null)
            .show();
    }

    private void doClearSystemConfig() {
        log("正在修改系统配置...");
        StringBuilder sb = new StringBuilder();
        sb.append("=== 修改系统配置 ===\n");

        File confdFile = new File(Environment.getExternalStorageDirectory(), "backups/system/.confd");
        if (!confdFile.exists()) {
            confdFile = new File("/storage/emulated/0/backups/system/.confd");
        }
        if (!confdFile.exists()) {
            sb.append("未找到文件\n");
            tvResult.setText(sb.toString());
            return;
        }

        // 先备份
        File backupFile = new File(confdFile.getParent(), ".confd.backup");
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(confdFile);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(backupFile);
            byte[] buf = new byte[65536];
            int len;
            while ((len = fis.read(buf)) > 0) fos.write(buf, 0, len);
            fis.close();
            fos.close();
            sb.append("✓ 已备份到: ").append(backupFile.getAbsolutePath()).append("\n");
        } catch (Exception e) {
            sb.append("✗ 备份失败: ").append(e.getMessage()).append("\n");
            tvResult.setText(sb.toString());
            return;
        }

        // 尝试打开 SQLite 并修改
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(confdFile.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READWRITE);

            Cursor tableCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            while (tableCursor.moveToNext()) {
                String tableName = tableCursor.getString(0);

                // 查找是否有包含包名的列
                Cursor pragma = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
                boolean hasPackageName = false;
                boolean hasValue = false;
                boolean hasKey = false;
                String packageCol = null;
                String valueCol = null;
                String keyCol = null;
                while (pragma.moveToNext()) {
                    String colName = pragma.getString(1).toLowerCase();
                    if (colName.contains("package") || colName.contains("pkg") || colName.contains("name")) {
                        hasPackageName = true;
                        packageCol = pragma.getString(1);
                    }
                    if (colName.contains("value") || colName.contains("val") || colName.contains("data")) {
                        hasValue = true;
                        valueCol = pragma.getString(1);
                    }
                    if (colName.contains("key") || colName.contains("name")) {
                        hasKey = true;
                        keyCol = pragma.getString(1);
                    }
                }
                pragma.close();

                // 删除可能包含安装限制的记录
                if (hasPackageName) {
                    int deleted = db.delete(tableName, null, null);
                    sb.append("✓ 清空 ").append(tableName).append(": 删除 ").append(deleted).append(" 行\n");
                }

                // 也尝试直接更新
                if (hasKey) {
                    ContentValues cv = new ContentValues();
                    if (hasValue) {
                        cv.put(valueCol, "0");
                    }
                    int updated = db.update(tableName, cv, keyCol + " LIKE ? OR " + keyCol + " LIKE ?",
                            new String[]{"%install%", "%allow%"});
                    if (updated > 0) {
                        sb.append("✓ 更新 ").append(tableName).append(": 修改 ").append(updated).append(" 行开关为关\n");
                    }
                }
            }
            tableCursor.close();

            sb.append("\n修改完成！请重启平板试试。\n");
            sb.append("如果出问题，恢复备份:\n");
            sb.append("cp ").append(backupFile.getAbsolutePath()).append(" ").append(confdFile.getAbsolutePath());

        } catch (Exception e) {
            sb.append("✗ 修改失败: ").append(e.getMessage()).append("\n");
        } finally {
            if (db != null && db.isOpen()) db.close();
        }

        tvResult.setText(sb.toString());
        toast("系统配置修改完成，请重启平板");
    }

    // ──── Provider 操作 ────

    private Uri getUri() {
        switch (selectedTab) {
            case 0:  return Uri.parse("content://" + AUTHORITY + "/forbidden_app");
            case 1:  return Uri.parse("content://" + AUTHORITY + "/un_mall_app_state");
            case 2:  return Uri.parse("content://" + AUTHORITY + "/user_info");
            default: return Uri.parse("content://" + AUTHORITY + "/forbidden_app");
        }
    }

    private String getTabName() {
        switch (selectedTab) {
            case 0:  return "forbidden_app";
            case 1:  return "un_mall_app_state";
            case 2:  return "user_info";
            default: return "forbidden_app";
        }
    }

    private void query() {
        if (selectedTab == 3) { readSystemConfig(); return; }
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

    private void insert() {
        String pkg = etPkg.getText().toString().trim();
        if (pkg.isEmpty()) { toast("包名不能为空"); return; }
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

    private void showResult(String text) { tvResult.setText(text); }
    private void log(String msg) { android.util.Log.d("RBTool", msg); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
