package com.iebundlepacker;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import org.json.*;
import java.io.*;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends Activity {
    private static final int PICK_BUNDLE = 101;
    private static final int SAVE_FILE = 102;
    private static final int EXPORT_FILE = 103;
    private static final int REPLACE_PNG = 104;
    private LinearLayout list;
    private TextView bundleTitle, status;
    private EditText search;
    private Spinner typeFilter, sortMode;
    private final List<Asset> assets = new ArrayList<>();
    private Uri selectedBundle;
    private String activeFilter = "All types";
    private String activeSort = "Name A–Z";
    private String sessionId;
    private String backendStatusMessage = "Open a bundle to begin.";
    private String pendingPathId, pendingFormat, pendingFileName;
    private PyObject backend;

    static class Asset {
        String name, type, id, size, handle;
        Asset(String n, String t, String i, String s, String h) { name=n; type=t; id=i; size=s; handle=h; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(15,19,24));
        getWindow().setNavigationBarColor(Color.rgb(15,19,24));
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        showEmptyState();
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            backend = Python.getInstance().getModule("iebundle_backend");
            JSONObject backendInfo = callJson("backend_status");
            backendStatusMessage = backendInfo.optString("message", "Python backend initialized.");
            status.setText(backendStatusMessage);
        } catch (Throwable error) {
            backend = null;
            backendStatusMessage = "Python backend failed to start: " + safeMessage(error);
            status.setText(backendStatusMessage);
            android.util.Log.e("IEBundlePacker", "Python initialization failed", error);
            toast("Backend initialization failed: " + safeMessage(error));
        }
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable bg(int color, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private TextView button(String label, Runnable click) {
        TextView b = text(label, 14, Color.rgb(230,235,240), true);
        b.setGravity(Gravity.CENTER); pad(b,14,11,14,11);
        b.setBackground(bg(Color.rgb(20,25,31), Color.rgb(100,110,119), 12));
        b.setOnClickListener(v -> click.run());
        return b;
    }
    private void buildUi() {
        LinearLayout root = vertical(); root.setBackgroundColor(Color.rgb(15,19,24));
        setContentView(root);
        LinearLayout top = horizontal(); pad(top,18,14,14,14);
        TextView brand = text("IEBundlePacker",20,Color.rgb(235,239,244),true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(button("OPEN", this::pickBundle));
        TextView save = button("SAVE AS", () -> saveAs());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2,-2); sp.leftMargin=dp(8); top.addView(save,sp);
        root.addView(top);
        View line = new View(this); line.setBackgroundColor(Color.rgb(43,50,58)); root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));

        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content = vertical(); pad(content,16,16,16,18); scroll.addView(content);
        bundleTitle = text("No bundle opened",16,Color.rgb(231,235,240),true);
        TextView hint = text("Open a Unity AssetBundle to inspect assets, export files, or replace supported assets.",13,Color.rgb(153,163,174),false);
        LinearLayout info = vertical(); pad(info,16,15,16,15); info.setBackground(bg(Color.rgb(17,22,28),Color.rgb(60,68,77),18));
        info.addView(bundleTitle); LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,-2); hp.topMargin=dp(8); info.addView(hint,hp);
        content.addView(info);

        search = new EditText(this); search.setSingleLine(true); search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(135,145,155)); search.setHint("Search asset name or path ID");
        search.setTextSize(14); pad(search,12,5,12,5); search.setBackground(bg(Color.rgb(20,25,31),Color.rgb(54,62,70),12));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1,dp(48)); ep.topMargin=dp(16); content.addView(search,ep);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){ renderAssets(); }
            public void afterTextChanged(android.text.Editable e){}
        });

        LinearLayout filters = horizontal(); filters.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        typeFilter = new Spinner(this);
        String[] types = {"All types","Texture2D","Mesh","Sprite","AssetBundle","Other"};
        typeFilter.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));
        sortMode = new Spinner(this);
        String[] sorts = {"Name A–Z","Name Z–A","Type","Largest first"};
        sortMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,sorts));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0,dp(44),1); fp.topMargin=dp(10);
        filters.addView(typeFilter,fp); LinearLayout.LayoutParams f2 = new LinearLayout.LayoutParams(0,dp(44),1); f2.leftMargin=dp(8); f2.topMargin=dp(10); filters.addView(sortMode,f2);
        content.addView(filters);
        typeFilter.setOnItemSelectedListener(new SimpleSelect(() -> { activeFilter=typeFilter.getSelectedItem().toString(); renderAssets(); }));
        sortMode.setOnItemSelectedListener(new SimpleSelect(() -> { activeSort=sortMode.getSelectedItem().toString(); renderAssets(); }));

        status = text("Open a bundle to begin.",13,Color.rgb(148,159,170),false);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(-1,-2); st.topMargin=dp(14); content.addView(status,st);
        list = vertical(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(8); content.addView(list,lp);
    }

    private class SimpleSelect implements AdapterView.OnItemSelectedListener {
        Runnable r; SimpleSelect(Runnable run){r=run;}
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id){r.run();}
        public void onNothingSelected(AdapterView<?> p){}
    }
    private void showEmptyState() { assets.clear(); renderAssets(); }

    private void pickBundle() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,PICK_BUNDLE);
    }
    private void saveAs() {
        if (sessionId == null) { toast("Open a bundle first."); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE,"modified.bundle");
        startActivityForResult(i,SAVE_FILE);
    }
    private File copyUriToCache(Uri uri, String prefix) throws IOException {
        File out = File.createTempFile(prefix, ".tmp", getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Unable to open selected file");
            byte[] buffer = new byte[64 * 1024]; int n;
            while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
        }
        return out;
    }
    private void runWorker(String label, Runnable work) {
        status.setText(label);
        new Thread(() -> {
            try { work.run(); }
            catch (Throwable e) {
                final String msg = safeMessage(e);
                runOnUiThread(() -> { status.setText("Operation failed: " + msg); toast(msg); });
            }
        }, "iebundle-worker").start();
    }
    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName() : message;
    }

    private JSONObject callJson(String method, Object... args) {
        if (backend == null) {
            throw new IllegalStateException("Python backend is unavailable. Check the startup status message.");
        }
        PyObject result = backend.callAttr(method, args);
        try { return new JSONObject(result.toString()); }
        catch (Exception e) { throw new RuntimeException("Backend returned invalid data: " + result, e); }
    }
    private void openBundleFromUri(Uri uri) {
        runWorker("Reading bundle…", () -> {
            final File input;
            try {
                input = copyUriToCache(uri, "bundle");
            } catch (IOException e) {
                throw new RuntimeException("Could not read selected bundle: " + e.getMessage(), e);
            }
            JSONObject result = callJson("open_bundle", input.getAbsolutePath());
            if (!result.optBoolean("ok")) throw new RuntimeException(result.optString("message", "Could not open bundle"));
            JSONArray rows;
            List<Asset> parsed = new ArrayList<>();
            String newSession;
            try {
                rows = result.getJSONArray("assets");
                for (int n = 0; n < rows.length(); n++) {
                    JSONObject row = rows.getJSONObject(n);
                    long bytes = row.optLong("sizeBytes", 0);
                    parsed.add(new Asset(row.optString("name", "Unknown"), row.optString("type", "Unknown"),
                        row.optString("id", "0"), formatBytes(bytes), row.optString("handle", row.optString("id", "0"))));
                }
                newSession = result.getString("session");
            } catch (Exception parseError) { throw new RuntimeException("Bundle list could not be read: " + parseError.getMessage()); }
            runOnUiThread(() -> {
                sessionId = newSession; selectedBundle = uri; assets.clear(); assets.addAll(parsed);
                String name = uri.getLastPathSegment(); bundleTitle.setText((name == null ? "Unity bundle" : name) + " • " + assets.size() + " assets");
                status.setText("Loaded " + assets.size() + " objects. Select an asset to export or replace supported data.");
                renderAssets();
            });
        });
    }
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_BUNDLE) {
            openBundleFromUri(uri);
        } else if (requestCode == SAVE_FILE) {
            saveBundleToUri(uri);
        } else if (requestCode == EXPORT_FILE) {
            exportAssetToUri(uri);
        } else if (requestCode == REPLACE_PNG) {
            replaceTextureFromUri(uri);
        }
    }
    private void saveBundleToUri(Uri destination) {
        runWorker("Serializing modified bundle…", () -> {
            File output = new File(getCacheDir(), "iebundle_saved_" + System.currentTimeMillis() + ".bundle");
            JSONObject result = callJson("save_bundle", sessionId, output.getAbsolutePath());
            if (!result.optBoolean("ok")) throw new RuntimeException(result.optString("message", "Save failed"));
            try (InputStream in = new FileInputStream(output); OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("Could not write to selected destination");
                byte[] b = new byte[64 * 1024]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } catch (IOException e) {
                throw new RuntimeException("Could not save bundle: " + e.getMessage(), e);
            }
            runOnUiThread(() -> { status.setText("Saved modified bundle."); toast("Bundle saved."); });
        });
    }
    private void exportAssetToUri(Uri destination) {
        final String pathId = pendingPathId, format = pendingFormat;
        runWorker("Exporting asset…", () -> {
            File output = new File(getCacheDir(), "iebundle_export_" + System.currentTimeMillis());
            JSONObject result = callJson("export_asset", sessionId, pathId, output.getAbsolutePath(), format);
            if (!result.optBoolean("ok")) throw new RuntimeException(result.optString("message", "Export failed"));
            try (InputStream in = new FileInputStream(output); OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("Could not write exported file");
                byte[] b = new byte[64 * 1024]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } catch (IOException e) {
                throw new RuntimeException("Could not export asset: " + e.getMessage(), e);
            }
            runOnUiThread(() -> { status.setText("Export complete: " + pendingFileName); toast("Exported " + pendingFileName); });
        });
    }
    private void replaceTextureFromUri(Uri uri) {
        final String pathId = pendingPathId;
        runWorker("Replacing texture…", () -> {
            File png;
            try {
                png = copyUriToCache(uri, "replacement");
            } catch (IOException e) {
                throw new RuntimeException("Could not read replacement PNG: " + e.getMessage(), e);
            }
            JSONObject result = callJson("replace_texture", sessionId, pathId, png.getAbsolutePath());
            if (!result.optBoolean("ok")) throw new RuntimeException(result.optString("message", "Replace failed"));
            runOnUiThread(() -> { status.setText(result.optString("message", "Texture replaced")); toast("Texture replaced in memory. Save As to write changes."); });
        });
    }
    private void requestExport(Asset a, String format, String extension, String mime) {
        pendingPathId = a.handle; pendingFormat = format; pendingFileName = a.name.replaceAll("[^A-Za-z0-9._-]", "_") + "." + extension;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime); i.putExtra(Intent.EXTRA_TITLE, pendingFileName); startActivityForResult(i, EXPORT_FILE);
    }
    private void requestReplace(Asset a) {
        pendingPathId = a.handle;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/png"); startActivityForResult(i, REPLACE_PNG);
    }
    private void renderAssets() {
        if (list == null) return;
        list.removeAllViews();
        String q = search == null ? "" : search.getText().toString().toLowerCase(Locale.ROOT).trim();
        List<Asset> shown = new ArrayList<>();
        for (Asset a:assets) {
            boolean typeOk = activeFilter.equals("All types") || (activeFilter.equals("Other") ?
                !Arrays.asList("Texture2D","Mesh","Sprite","AssetBundle").contains(a.type) : activeFilter.equals(a.type));
            if (typeOk && (q.isEmpty() || a.name.toLowerCase(Locale.ROOT).contains(q) || a.id.contains(q))) shown.add(a);
        }
        if ("Name Z–A".equals(activeSort)) Collections.sort(shown,(a,b)->b.name.compareToIgnoreCase(a.name));
        else if ("Type".equals(activeSort)) Collections.sort(shown,(a,b)->a.type.compareToIgnoreCase(b.type));
        else if ("Largest first".equals(activeSort)) Collections.sort(shown,(a,b)->Long.compare(sizeBytes(b.size),sizeBytes(a.size)));
        else Collections.sort(shown,(a,b)->a.name.compareToIgnoreCase(b.name));
        if (assets.isEmpty()) status.setText(backendStatusMessage);
        else status.setText(shown.size()+" assets shown • "+assets.size()+" total");
        if (shown.isEmpty()) {
            TextView empty=text(assets.isEmpty()?"No assets loaded yet.":"No assets match your search.",14,Color.rgb(143,153,164),false);
            pad(empty,12,20,12,20); list.addView(empty); return;
        }
        for (Asset a:shown) list.addView(assetCard(a));
    }
    private long sizeBytes(String s) {
        try { String x=s.toUpperCase(Locale.ROOT).trim(); double m=1;
            if(x.endsWith("KB")){m=1024;x=x.substring(0,x.length()-2);}
            else if(x.endsWith("MB")){m=1024*1024;x=x.substring(0,x.length()-2);}
            else if(x.endsWith("B")){x=x.substring(0,x.length()-1);}
            return (long)(Double.parseDouble(x.trim())*m);
        } catch(Exception e){return 0;}
    }
    private View assetCard(Asset a) {
        LinearLayout row=horizontal(); pad(row,14,14,14,14);
        row.setBackground(bg(Color.rgb(17,22,28),Color.rgb(48,56,64),18));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dp(10); row.setLayoutParams(rp);
        TextView glyph=text(a.type.equals("Texture2D")?"▨":a.type.equals("Mesh")?"◇":"▤",25,Color.rgb(201,221,233),true);
        glyph.setGravity(Gravity.CENTER); glyph.setBackground(bg(Color.rgb(49,67,78),0,14));
        row.addView(glyph,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout details=vertical(); LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(0,-2,1); dp2.leftMargin=dp(14); row.addView(details,dp2);
        TextView name=text(a.name,16,Color.rgb(232,236,241),true); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); details.addView(name);
        TextView meta=text("id: "+a.id+" • "+a.size,12,Color.rgb(139,149,160),false); meta.setMaxLines(1); meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2); mp.topMargin=dp(4); details.addView(meta,mp);
        TextView tag=text(a.type,11,Color.rgb(203,232,242),true); tag.setGravity(Gravity.CENTER); pad(tag,10,6,10,6); tag.setBackground(bg(Color.rgb(0,82,108),0,24));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.topMargin=dp(8); details.addView(tag,tp);
        row.setOnClickListener(v -> {
            android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(this).setTitle(a.name)
                .setMessage("Type: " + a.type + "\nPath ID: " + a.id + "\nSize: " + a.size);
            if (a.type.equals("Texture2D")) {
                dialog.setPositiveButton("Export PNG", (d,w) -> requestExport(a,"png","png","image/png"));
                dialog.setNeutralButton("Replace PNG", (d,w) -> requestReplace(a));
                dialog.setNegativeButton("Export JSON", (d,w) -> requestExport(a,"json","json","application/json"));
            } else if (a.type.equals("Mesh")) {
                dialog.setPositiveButton("Export OBJ", (d,w) -> requestExport(a,"obj","obj","text/plain"));
                dialog.setNeutralButton("Export JSON", (d,w) -> requestExport(a,"json","json","application/json"));
                dialog.setNegativeButton("Export raw", (d,w) -> requestExport(a,"raw","bin","application/octet-stream"));
            } else {
                dialog.setPositiveButton("Export JSON", (d,w) -> requestExport(a,"json","json","application/json"));
                dialog.setNeutralButton("Export raw", (d,w) -> requestExport(a,"raw","bin","application/octet-stream"));
                dialog.setNegativeButton("Close", null);
            }
            dialog.show();
        });
        return row;
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
                }
