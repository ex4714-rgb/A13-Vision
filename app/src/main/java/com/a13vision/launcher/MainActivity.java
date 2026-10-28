package com.a13vision.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** A lightweight Android TV home screen that displays only apps found on the device. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockUpdater = new Runnable() {
        @Override public void run() {
            updateHeader();
            handler.postDelayed(this, 30_000L);
        }
    };

    private FrameLayout root;
    private TextView clockView;
    private TextView dateView;
    private TextView wifiView;
    private TextView hintView;
    private LinearLayout appRow;
    private HorizontalScrollView appScroller;
    private boolean arrangeMode;
    private final List<AppEntry> launchableApps = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();
        setContentView(buildHome());
        updateHeader();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        updateHeader();
        if (root != null) root.post(this::focusNetflixOrFirstApp);
        handler.removeCallbacks(clockUpdater);
        handler.post(clockUpdater);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockUpdater);
        super.onPause();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (down) {
                if (arrangeMode) exitArrangeMode(); else enterArrangeMode();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (down && arrangeMode) exitArrangeMode();
            return true;
        }
        if (arrangeMode) {
            if (down) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) moveFocusedApp(-1);
                else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) moveFocusedApp(1);
                else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    setFocusedAppAsStartup();
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private FrameLayout buildHome() {
        root = new FrameLayout(this);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setImageResource(R.drawable.desert_sunset);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x75070A16, 0x18070A16, 0x9C090811}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        root.addView(buildHeader(), new FrameLayout.LayoutParams(-1, dp(94), Gravity.TOP));

        appScroller = buildAppRow();
        root.addView(appScroller, new FrameLayout.LayoutParams(-1, dp(270), Gravity.CENTER));

        hintView = text("MENU ou OK maintenu : réorganiser  ·  Flèches : naviguer", 13, 0xBFFFFFFF);
        hintView.setGravity(Gravity.CENTER);
        hintView.setLetterSpacing(0.03f);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, dp(30),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.bottomMargin = dp(28);
        root.addView(hintView, hintParams);

        root.setFocusableInTouchMode(true);
        root.post(this::focusNetflixOrFirstApp);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(56), dp(18), dp(56), dp(6));

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        LinearLayout dateTime = new LinearLayout(this);
        dateTime.setOrientation(LinearLayout.VERTICAL);
        dateTime.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        clockView = text("--:--", 19, Color.WHITE);
        clockView.setGravity(Gravity.END);
        dateView = text("", 11, 0xDFFFFFFF);
        dateView.setGravity(Gravity.END);
        dateTime.addView(clockView);
        dateTime.addView(dateView);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        dateParams.rightMargin = dp(28);
        header.addView(dateTime, dateParams);

        wifiView = text("◔  Wi-Fi", 13, 0xFFE9E5E2);
        wifiView.setGravity(Gravity.CENTER);
        wifiView.setPadding(dp(10), 0, dp(20), 0);
        header.addView(wifiView, new LinearLayout.LayoutParams(-2, dp(44)));

        TextView search = headerButton("⌕", "Recherche");
        search.setOnClickListener(v -> showSearch());
        header.addView(search, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView settings = headerButton("⚙", "Paramètres");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.leftMargin = dp(8);
        header.addView(settings, settingsParams);
        settings.setOnClickListener(v -> openSettings());
        return header;
    }

    private HorizontalScrollView buildAppRow() {
        launchableApps.clear();
        launchableApps.addAll(findLaunchableApps());

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setFillViewport(true);
        scroller.setFocusable(false);

        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(112);
        int cellWidthDp = Math.min(168, Math.max(116, screenWidthDp / 7));
        int rowWidth = Math.max(availableWidth,
                launchableApps.size() * dp(cellWidthDp + 16) + dp(16));

        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER);
        appRow.setMinimumWidth(availableWidth);
        appRow.setPadding(dp(8), dp(10), dp(8), dp(10));

        if (launchableApps.isEmpty()) {
            TextView empty = text("Aucune application de lancement détectée", 18, Color.WHITE);
            empty.setGravity(Gravity.CENTER);
            appRow.addView(empty, new LinearLayout.LayoutParams(rowWidth, dp(80)));
        } else {
            for (AppEntry app : launchableApps) {
                View tile = createAppTile(app, cellWidthDp);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(cellWidthDp), dp(224));
                params.leftMargin = dp(8);
                params.rightMargin = dp(8);
                appRow.addView(tile, params);
            }
        }
        scroller.addView(appRow, new HorizontalScrollView.LayoutParams(rowWidth, -2));
        return scroller;
    }

    private View createAppTile(AppEntry app, int cellWidthDp) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setContentDescription(app.label);
        tile.setPadding(dp(7), dp(8), dp(7), dp(8));
        tile.setBackground(round(0x00FFFFFF, dp(24), 0, 0));
        tile.setOnFocusChangeListener((v, focused) -> {
            if (focused) {
                v.setBackground(round(0x37FFFFFF, dp(24), 0xFFF3E8DA, dp(2)));
                v.setScaleX(1.045f);
                v.setScaleY(1.045f);
                v.setElevation(dp(12));
            } else {
                v.setBackground(round(0x00FFFFFF, dp(24), 0, 0));
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setElevation(0);
            }
        });

        int iconSize = Math.min(dp(112), dp(cellWidthDp - 20));
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(round(0x45101524, dp(22), 0x44FFFFFF, dp(1)));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset = dp(12);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(-1, -1);
        iconParams.setMargins(inset, inset, inset, inset);
        iconBox.addView(icon, iconParams);
        tile.addView(iconBox, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView caption = text(app.label, 14, 0xFFF8F6F4);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setPadding(dp(3), dp(12), dp(3), 0);
        tile.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        tile.setOnClickListener(v -> launch(app));
        tile.setOnLongClickListener(v -> {
            enterArrangeMode();
            return true;
        });
        return tile;
    }

    private void focusNetflixOrFirstApp() {
        if (appRow == null || appRow.getChildCount() == 0) return;
        int focusIndex = 0;
        String startupPackage = getPreferences(MODE_PRIVATE).getString(
                "startup_package", "com.netflix.mediaclient");
        for (int i = 0; i < launchableApps.size(); i++) {
            if (startupPackage.equals(launchableApps.get(i).packageName)) {
                focusIndex = i;
                break;
            }
        }
        View tile = appRow.getChildAt(focusIndex);
        if (tile != null) tile.requestFocus();
    }

    private List<AppEntry> findLaunchableApps() {
        List<AppEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addActivities(result, seen, Intent.CATEGORY_LEANBACK_LAUNCHER);
        addActivities(result, seen, Intent.CATEGORY_LAUNCHER);
        Collator collator = Collator.getInstance(Locale.FRANCE);
        result.sort((a, b) -> collator.compare(a.label, b.label));
        String savedOrder = getPreferences(MODE_PRIVATE).getString("app_order", "");
        if (savedOrder.isEmpty()) return result;
        Map<String, AppEntry> byPackage = new HashMap<>();
        for (AppEntry app : result) byPackage.put(app.packageName, app);
        List<AppEntry> ordered = new ArrayList<>();
        for (String packageName : savedOrder.split("\\|")) {
            AppEntry app = byPackage.remove(packageName);
            if (app != null) ordered.add(app);
        }
        // Newly installed apps keep their alphabetical order after the saved entries.
        List<AppEntry> newEntries = new ArrayList<>(byPackage.values());
        newEntries.sort((a, b) -> collator.compare(a.label, b.label));
        ordered.addAll(newEntries);
        return ordered;
    }

    private void enterArrangeMode() {
        if (arrangeMode || launchableApps.isEmpty()) return;
        View focused = getCurrentFocus();
        if (focused == null || appRow.indexOfChild(focused) < 0) focusNetflixOrFirstApp();
        arrangeMode = true;
        hintView.setText("←/→ déplacer  ·  OK choisir le focus de départ  ·  Retour terminer");
        Toast.makeText(this, "Mode réorganisation", Toast.LENGTH_SHORT).show();
    }

    private void exitArrangeMode() {
        arrangeMode = false;
        hintView.setText("MENU ou OK maintenu : réorganiser  ·  Flèches : naviguer");
    }

    private void moveFocusedApp(int direction) {
        if (appRow == null) return;
        View focused = getCurrentFocus();
        int from = focused == null ? -1 : appRow.indexOfChild(focused);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= launchableApps.size()) return;
        AppEntry moving = launchableApps.remove(from);
        launchableApps.add(to, moving);
        View tile = appRow.getChildAt(from);
        appRow.removeViewAt(from);
        appRow.addView(tile, to);
        saveAppOrder();
        tile.requestFocus();
        if (appScroller != null) {
            tile.post(() -> {
                int left = tile.getLeft();
                int right = tile.getRight();
                int visibleLeft = appScroller.getScrollX();
                int visibleRight = visibleLeft + appScroller.getWidth();
                if (left < visibleLeft) appScroller.smoothScrollTo(Math.max(0, left - dp(16)), 0);
                else if (right > visibleRight) {
                    appScroller.smoothScrollTo(right - appScroller.getWidth() + dp(16), 0);
                }
            });
        }
    }

    private void saveAppOrder() {
        StringBuilder order = new StringBuilder();
        for (AppEntry app : launchableApps) {
            if (order.length() > 0) order.append('|');
            order.append(app.packageName);
        }
        getPreferences(MODE_PRIVATE).edit().putString("app_order", order.toString()).apply();
    }

    private void setFocusedAppAsStartup() {
        View focused = getCurrentFocus();
        int index = focused == null || appRow == null ? -1 : appRow.indexOfChild(focused);
        if (index < 0 || index >= launchableApps.size()) return;
        AppEntry app = launchableApps.get(index);
        getPreferences(MODE_PRIVATE).edit().putString("startup_package", app.packageName).apply();
        hintView.setText(app.label + " sera sélectionnée au prochain démarrage");
    }

    private void addActivities(List<AppEntry> result, Set<String> seen, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        for (android.content.pm.ResolveInfo info : getPackageManager().queryIntentActivities(query, 0)) {
            if (info.activityInfo == null || info.activityInfo.packageName.equals(getPackageName())) continue;
            String packageName = info.activityInfo.packageName;
            if (!seen.add(packageName)) continue;
            try {
                CharSequence name = info.loadLabel(getPackageManager());
                Drawable icon = info.loadIcon(getPackageManager());
                Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                launchIntent.setComponent(new ComponentName(packageName, info.activityInfo.name));
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                result.add(new AppEntry(packageName, name == null ? packageName : name.toString(), icon, launchIntent));
            } catch (Exception ignored) {
                // A broken app icon should not stop other installed apps from appearing.
            }
        }
    }

    private void launch(AppEntry app) {
        try {
            startActivity(app.launchIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d’ouvrir " + app.label + ".", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateHeader() {
        Locale french = Locale.FRANCE;
        String time = new SimpleDateFormat("HH:mm", french).format(new Date());
        String date = new SimpleDateFormat("EEEE d MMMM", french).format(new Date());
        if (clockView != null) clockView.setText(time);
        if (dateView != null) dateView.setText(date.substring(0, 1).toUpperCase(french) + date.substring(1));
        if (wifiView != null) wifiView.setText(isWifiConnected() ? "●  Wi-Fi" : "○  Wi-Fi");
    }

    private boolean isWifiConnected() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void showSearch() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFFB9BAC2);
        input.setHint("Que veux-tu chercher ?");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(18), dp(10), dp(18), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("Recherche Web")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Rechercher", (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) return;
                    Intent browser = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
                    try {
                        startActivity(browser);
                    } catch (Exception e) {
                        Toast.makeText(this, "Aucun navigateur disponible.", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Les paramètres ne sont pas accessibles.", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView headerButton(String symbol, String description) {
        TextView button = text(symbol, 25, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setContentDescription(description);
        button.setBackground(round(0x17FFFFFF, dp(24), 0, 0));
        button.setOnFocusChangeListener((v, focused) -> v.setBackground(round(
                focused ? 0x39FFFFFF : 0x17FFFFFF, dp(24), focused ? 0xFFEFE4D7 : 0,
                focused ? dp(2) : 0)));
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;
        final Intent launchIntent;

        AppEntry(String packageName, String label, Drawable icon, Intent launchIntent) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.launchIntent = launchIntent;
        }
    }
}
