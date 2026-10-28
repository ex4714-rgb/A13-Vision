package com.a13vision.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
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
    private TextView lastAppNameView;
    private ImageView lastAppIconView;
    private LinearLayout lastAppCard;
    private GridLayout appGrid;
    private int appCellWidthDp;
    private int appTileHeightDp;
    private boolean arrangeMode;
    private boolean focusLastAppOnReturn;
    private final List<AppEntry> launchableApps = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();
        setContentView(buildHome());
        updateHeader();
        updateLastAppCard();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        updateHeader();
        updateLastAppCard();
        if (root != null) root.post(() -> {
            if (focusLastAppOnReturn && lastAppCard.getVisibility() == View.VISIBLE) {
                focusLastAppOnReturn = false;
                lastAppCard.requestFocus();
            } else {
                focusLastAppOnReturn = false;
                focusFirstOrChosenApp();
            }
        });
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
        if (!arrangeMode && down
                && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && event.isLongPress()) {
            enterArrangeMode();
            return true;
        }
        if (arrangeMode) {
            if (down) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) moveFocusedApp(-1);
                else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) moveFocusedApp(1);
                else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) moveFocusedApp(-6);
                else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) moveFocusedApp(6);
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
        wallpaper.setImageResource(R.drawable.ringed_world);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x48040B1A, 0x2A030A18, 0x62020712}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        root.addView(buildHeader(), new FrameLayout.LayoutParams(-1, dp(94), Gravity.TOP));

        lastAppCard = buildLastAppCard();
        FrameLayout.LayoutParams lastAppParams = new FrameLayout.LayoutParams(dp(260), dp(58),
                Gravity.TOP | Gravity.START);
        lastAppParams.leftMargin = dp(56);
        lastAppParams.topMargin = dp(100);

        appGrid = buildAppGrid();
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        gridParams.topMargin = dp(34);
        root.addView(appGrid, gridParams);
        root.addView(lastAppCard, lastAppParams);

        hintView = text("MENU / OK : organiser", 12, 0xF2FFFFFF);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(dp(18), 0, dp(18), 0);
        hintView.setBackground(round(0x3DFFFFFF, dp(24), 0x55FFFFFF, dp(1)));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-2, dp(42),
                Gravity.BOTTOM | Gravity.END);
        hintParams.rightMargin = dp(48);
        hintParams.bottomMargin = dp(38);
        root.addView(hintView, hintParams);

        root.setFocusableInTouchMode(true);
        root.post(this::focusFirstOrChosenApp);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(56), dp(18), dp(56), dp(6));

        LinearLayout dateTime = new LinearLayout(this);
        dateTime.setOrientation(LinearLayout.VERTICAL);
        dateTime.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        clockView = text("--:--", 22, Color.WHITE);
        dateView = text("", 12, 0xDFFFFFFF);
        dateTime.addView(clockView);
        dateTime.addView(dateView);
        header.addView(dateTime, new LinearLayout.LayoutParams(-2, -2));

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        wifiView = text("◔  Wi-Fi", 13, 0xFFE9E5E2);
        wifiView.setGravity(Gravity.CENTER);
        wifiView.setPadding(dp(10), 0, dp(20), 0);
        wifiView.setBackground(round(0x66303D55, dp(18), 0x55DDEEFF, dp(1)));
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

    private LinearLayout buildLastAppCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(11), dp(6), dp(12), dp(6));
        card.setBackground(round(0x66303D55, dp(15), 0x66DDEEFF, dp(1)));
        card.setFocusable(true);
        card.setContentDescription("Last app used");

        lastAppIconView = new ImageView(this);
        lastAppIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconParams.rightMargin = dp(11);
        card.addView(lastAppIconView, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("LAST APP USED", 9, 0xCFFFFFFF);
        heading.setLetterSpacing(0.08f);
        lastAppNameView = text("", 13, Color.WHITE);
        lastAppNameView.setSingleLine(true);
        lastAppNameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(heading);
        labels.addView(lastAppNameView);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView play = text("▶  REPRENDRE", 9, Color.WHITE);
        play.setGravity(Gravity.CENTER);
        card.addView(play, new LinearLayout.LayoutParams(-2, dp(36)));
        card.setOnFocusChangeListener((v, focused) -> v.setBackground(round(
                focused ? 0x88466C92 : 0x66303D55, dp(15),
                focused ? 0xFFE5F5FF : 0x66DDEEFF, focused ? dp(2) : dp(1))));
        return card;
    }

    private void updateLastAppCard() {
        if (lastAppCard == null) return;
        String packageName = getPreferences(MODE_PRIVATE).getString("last_app_package", null);
        AppEntry lastApp = null;
        if (packageName != null) {
            for (AppEntry app : launchableApps) {
                if (packageName.equals(app.packageName)) {
                    lastApp = app;
                    break;
                }
            }
        }
        if (lastApp == null) {
            lastAppCard.setVisibility(View.GONE);
            return;
        }
        AppEntry appToLaunch = lastApp;
        lastAppCard.setVisibility(View.VISIBLE);
        lastAppCard.setContentDescription("Reprendre " + lastApp.label);
        lastAppIconView.setImageDrawable(lastApp.icon);
        lastAppNameView.setText(lastApp.label);
        lastAppCard.setOnClickListener(v -> launch(appToLaunch));
    }

    private GridLayout buildAppGrid() {
        launchableApps.clear();
        launchableApps.addAll(findLaunchableApps());

        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        appCellWidthDp = Math.min(148, Math.max(84, (int) (screenWidthDp / 7.2f)));
        appTileHeightDp = Math.min(124, Math.max(92, (int) (appCellWidthDp * 0.94f)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        grid.setFocusable(false);

        if (launchableApps.isEmpty()) {
            TextView empty = text("Aucune application de lancement détectée", 18, Color.WHITE);
            empty.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(0), GridLayout.spec(0));
            params.width = dp(appCellWidthDp * 4);
            params.height = dp(70);
            grid.addView(empty, params);
        } else {
            populateAppGrid(grid, appCellWidthDp);
        }
        return grid;
    }

    private void populateAppGrid(GridLayout grid, int cellWidthDp) {
        grid.removeAllViews();
        for (int i = 0; i < launchableApps.size(); i++) {
            View tile = createAppTile(launchableApps.get(i), cellWidthDp);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 6, GridLayout.CENTER),
                    GridLayout.spec(i % 6, GridLayout.CENTER));
            params.width = dp(cellWidthDp);
            params.height = dp(appTileHeightDp);
            params.setMargins(dp(6), dp(8), dp(6), dp(8));
            grid.addView(tile, params);
        }
    }

    private View createAppTile(AppEntry app, int cellWidthDp) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setContentDescription(app.label);
        tile.setPadding(dp(3), dp(2), dp(3), dp(2));

        int cardHeightDp = appTileHeightDp - 27;
        int radius = dp(13);
        int backgroundColor = dominantIconColor(app.icon);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(round(backgroundColor, radius, 0, 0));
        card.setClipToOutline(true);

        Drawable adaptiveBackground = adaptiveIconBackground(app.icon);
        if (adaptiveBackground != null) {
            ImageView background = new ImageView(this);
            background.setImageDrawable(adaptiveBackground);
            background.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(background, new FrameLayout.LayoutParams(-1, -1));
        }

        int iconSize = Math.min(dp(72), dp(cardHeightDp - 14));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(adaptiveIconForeground(app.icon));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize,
                Gravity.CENTER);
        card.addView(icon, iconParams);

        View outline = new View(this);
        outline.setBackground(round(0x00000000, radius, 0x66FFFFFF, dp(1)));
        card.addView(outline, new FrameLayout.LayoutParams(-1, -1));
        tile.addView(card, new LinearLayout.LayoutParams(-1, dp(cardHeightDp)));

        TextView caption = text(app.label, 11, 0xF5FFFFFF);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setPadding(dp(2), dp(5), dp(2), 0);
        tile.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        tile.setOnFocusChangeListener((v, focused) -> {
            outline.setBackground(round(0x00000000, radius,
                    focused ? 0xFFE5F5FF : 0x66FFFFFF, focused ? dp(2) : dp(1)));
            card.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                    .setDuration(170).start();
            card.setElevation(focused ? dp(12) : 0);
            caption.setTextColor(focused ? Color.WHITE : 0xD9FFFFFF);
            caption.setTypeface(null, focused ? Typeface.BOLD : Typeface.NORMAL);
        });
        tile.setOnClickListener(v -> launch(app));
        tile.setOnLongClickListener(v -> {
            enterArrangeMode();
            return true;
        });
        return tile;
    }

    private Drawable adaptiveIconBackground(Drawable icon) {
        if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
            return copyDrawable(((AdaptiveIconDrawable) icon).getBackground());
        }
        return null;
    }

    private Drawable adaptiveIconForeground(Drawable icon) {
        if (Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable) {
            Drawable foreground = ((AdaptiveIconDrawable) icon).getForeground();
            if (foreground != null) return copyDrawable(foreground);
        }
        return copyDrawable(icon);
    }

    private Drawable copyDrawable(Drawable drawable) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        Drawable copy = state == null ? drawable : state.newDrawable(getResources());
        return copy.mutate();
    }

    private int dominantIconColor(Drawable icon) {
        Bitmap sample = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sample);
        Drawable drawable = copyDrawable(icon);
        drawable.setBounds(0, 0, sample.getWidth(), sample.getHeight());
        drawable.draw(canvas);

        Map<Integer, Integer> colors = new HashMap<>();
        int opaquePixels = 0;
        for (int y = 0; y < sample.getHeight(); y++) {
            for (int x = 0; x < sample.getWidth(); x++) {
                int pixel = sample.getPixel(x, y);
                if (Color.alpha(pixel) < 150) continue;
                opaquePixels++;
                int grouped = Color.rgb((Color.red(pixel) / 24) * 24,
                        (Color.green(pixel) / 24) * 24, (Color.blue(pixel) / 24) * 24);
                Integer count = colors.get(grouped);
                colors.put(grouped, count == null ? 1 : count + 1);
            }
        }
        sample.recycle();

        if (opaquePixels < 200) return 0xFF20354D;
        int chosen = 0xFF385572;
        int mostCommon = 0;
        for (Map.Entry<Integer, Integer> entry : colors.entrySet()) {
            if (entry.getValue() > mostCommon) {
                chosen = entry.getKey();
                mostCommon = entry.getValue();
            }
        }
        return chosen;
    }

    private void focusFirstOrChosenApp() {
        if (appGrid == null || appGrid.getChildCount() == 0) return;
        int focusIndex = 0;
        String startupPackage = getPreferences(MODE_PRIVATE).getString("startup_package", null);
        // Ignore Netflix if it was saved by the old default-focus behavior.
        if (startupPackage != null && !startupPackage.equals("com.netflix.mediaclient")) {
            for (int i = 0; i < launchableApps.size(); i++) {
                if (startupPackage.equals(launchableApps.get(i).packageName)) {
                    focusIndex = i;
                    break;
                }
            }
        }
        View tile = appGrid.getChildAt(focusIndex);
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
        if (focused == null || appGrid.indexOfChild(focused) < 0) focusFirstOrChosenApp();
        arrangeMode = true;
        hintView.setText("Flèches : déplacer  ·  OK choisir le focus  ·  Retour terminer");
        Toast.makeText(this, "Mode réorganisation", Toast.LENGTH_SHORT).show();
    }

    private void exitArrangeMode() {
        arrangeMode = false;
        hintView.setText("MENU / OK : organiser");
    }

    private void moveFocusedApp(int direction) {
        if (appGrid == null) return;
        View focused = getCurrentFocus();
        int from = focused == null ? -1 : appGrid.indexOfChild(focused);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= launchableApps.size()) return;
        if ((direction == -1 || direction == 1) && from / 6 != to / 6) return;
        if ((direction == -6 || direction == 6) && from % 6 != to % 6) return;
        Collections.swap(launchableApps, from, to);
        saveAppOrder();
        populateAppGrid(appGrid, appCellWidthDp);
        View tile = appGrid.getChildAt(to);
        tile.requestFocus();
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
        int index = focused == null || appGrid == null ? -1 : appGrid.indexOfChild(focused);
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
            CharSequence appName = info.loadLabel(getPackageManager());
            String label = appName == null ? packageName : appName.toString();
            int flags = info.activityInfo.applicationInfo.flags;
            boolean systemApp = (flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            boolean tvLauncherApp = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
            if (isUtilityApp(packageName, label)) continue;
            // Keep TV apps supplied with the box, while hiding generic system tools such as
            // Camera and Chrome. User-installed apps remain available in the row.
            if (systemApp && !tvLauncherApp && !isMediaApp(packageName, label)) continue;
            if (!seen.add(packageName)) continue;
            try {
                Drawable icon = info.loadIcon(getPackageManager());
                Intent launchIntent = new Intent(Intent.ACTION_MAIN);
                launchIntent.setComponent(new ComponentName(packageName, info.activityInfo.name));
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                result.add(new AppEntry(packageName, label, icon, launchIntent));
            } catch (Exception ignored) {
                // A broken app icon should not stop other installed apps from appearing.
            }
        }
    }

    private boolean isUtilityApp(String packageName, String label) {
        String name = label.toLowerCase(Locale.ROOT).trim();
        String pkg = packageName.toLowerCase(Locale.ROOT);
        return name.equals("camera") || name.contains("camera") || name.contains("caméra")
                || name.contains("appareil photo") || name.equals("settings")
                || name.contains("settings") || name.contains("paramètres")
                || name.equals("chrome") || name.contains("clean up") || name.equals("cleanup")
                || name.equals("nettoyage") || pkg.contains("camera") || pkg.contains("settings");
    }

    private boolean isMediaApp(String packageName, String label) {
        String identity = (packageName + " " + label).toLowerCase(Locale.ROOT);
        return identity.contains("netflix") || identity.contains("youtube")
                || identity.contains("prime video") || identity.contains("amazonvideo")
                || identity.contains("disney") || identity.contains("iptv")
                || identity.contains("atlas") || identity.contains("dino")
                || identity.contains("onyx") || identity.contains("rplayer")
                || identity.contains("stream") || identity.contains("plex")
                || identity.contains("kodi") || identity.contains("vlc");
    }

    private void launch(AppEntry app) {
        try {
            focusLastAppOnReturn = true;
            startActivity(app.launchIntent);
            getPreferences(MODE_PRIVATE).edit().putString("last_app_package", app.packageName).apply();
        } catch (Exception e) {
            focusLastAppOnReturn = false;
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
        button.setBackground(round(0x66303D55, dp(24), 0x55DDEEFF, dp(1)));
        button.setOnFocusChangeListener((v, focused) -> v.setBackground(round(
                focused ? 0x88466C92 : 0x66303D55, dp(24),
                focused ? 0xFFE5F5FF : 0x55DDEEFF, focused ? dp(2) : dp(1))));
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
