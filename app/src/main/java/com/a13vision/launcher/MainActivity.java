package com.a13vision.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ScrollView;
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
    private static final String[] SETTINGS_MENU_LABELS = {
            "Réseau & Wi-Fi", "Applications", "Affichage", "Son", "À propos"
    };
    private static final String[] SETTINGS_MENU_DESCRIPTIONS = {
            "Gérer la connexion Wi-Fi et l’accès au réseau de la box.",
            "Consulter les applications installées et leurs autorisations.",
            "Accéder aux réglages d’image et de résolution de la box.",
            "Accéder aux réglages de volume et de sortie audio.",
            "Voir les informations Android et le modèle de l’appareil."
    };
    private static final String[] SETTINGS_MENU_ACTIONS = {
            Settings.ACTION_WIFI_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS,
            Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_SOUND_SETTINGS,
            Settings.ACTION_DEVICE_INFO_SETTINGS
    };
    private static final String[] SETTINGS_MENU_ACTION_LABELS = {
            "Ouvrir les réglages Wi-Fi", "Ouvrir les applications", "Ouvrir l’affichage",
            "Ouvrir les réglages audio", "Ouvrir les informations de la box"
    };

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
    private TextView searchView;
    private TextView settingsView;
    private TextView hintView;
    private TextView lastAppNameView;
    private ImageView lastAppIconView;
    private LinearLayout lastAppCard;
    private ScrollView appScroll;
    private GridLayout appGrid;
    private FrameLayout settingsPanel;
    private LinearLayout settingsCategoryColumn;
    private LinearLayout settingsDetailBody;
    private TextView settingsDetailTitle;
    private TextView settingsDetailDescription;
    private View settingsActionView;
    private final List<TextView> settingsCategoryButtons = new ArrayList<>();
    private int appCellWidthDp;
    private int appTileHeightDp;
    private int selectedSettingsCategory;
    private boolean arrangeMode;
    private boolean focusLastAppOnReturn;
    private boolean focusSettingsActionOnReturn;
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
        refreshAppGrid();
        updateLastAppCard();
        if (root != null) root.post(() -> {
            if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
                if (focusSettingsActionOnReturn && settingsActionView != null) {
                    focusSettingsActionOnReturn = false;
                    settingsActionView.requestFocus();
                } else if (!settingsPanel.hasFocus() && !settingsCategoryButtons.isEmpty()) {
                    settingsCategoryButtons.get(selectedSettingsCategory).requestFocus();
                }
            } else if (focusLastAppOnReturn && lastAppCard.getVisibility() == View.VISIBLE) {
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
        if (settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (down) closeSettingsMenu();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) return true;
            return super.dispatchKeyEvent(event);
        }
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
        wallpaper.setImageResource(R.drawable.sunset_horizon);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x30040B1A, 0x20030A18, 0x6B020712}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        root.addView(buildHeader(), new FrameLayout.LayoutParams(-1, dp(94), Gravity.TOP));

        lastAppCard = buildLastAppCard();
        FrameLayout.LayoutParams lastAppParams = new FrameLayout.LayoutParams(dp(310), dp(68),
                Gravity.TOP | Gravity.START);
        lastAppParams.leftMargin = dp(56);
        lastAppParams.topMargin = dp(104);
        root.addView(lastAppCard, lastAppParams);

        appGrid = buildAppGrid();
        appScroll = new ScrollView(this);
        appScroll.setFillViewport(false);
        appScroll.setVerticalScrollBarEnabled(false);
        appScroll.setClipToPadding(false);
        appScroll.setFocusable(false);
        appScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP);
        scrollParams.topMargin = dp(184);
        scrollParams.bottomMargin = dp(88);
        scrollParams.leftMargin = dp(24);
        scrollParams.rightMargin = dp(24);
        root.addView(appScroll, scrollParams);
        FrameLayout gridHolder = new FrameLayout(this);
        appScroll.addView(gridHolder, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        gridHolder.addView(appGrid, gridParams);

        hintView = text("MENU / OK : organiser", 12, 0xF2FFFFFF);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(dp(18), 0, dp(18), 0);
        hintView.setBackground(round(0x3DFFFFFF, dp(24), 0x55FFFFFF, dp(1)));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-2, dp(42),
                Gravity.BOTTOM | Gravity.END);
        hintParams.rightMargin = dp(48);
        hintParams.bottomMargin = dp(38);
        root.addView(hintView, hintParams);

        settingsPanel = buildSettingsMenu();
        root.addView(settingsPanel, new FrameLayout.LayoutParams(-1, -1));

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
        wifiView.setId(View.generateViewId());
        wifiView.setGravity(Gravity.CENTER);
        wifiView.setPadding(dp(10), 0, dp(20), 0);
        wifiView.setBackground(round(0x66303D55, dp(18), 0x55DDEEFF, dp(1)));
        wifiView.setFocusable(true);
        wifiView.setContentDescription("Open Wi-Fi settings");
        wifiView.setOnClickListener(v -> openWifiSettings());
        wifiView.setOnFocusChangeListener((v, focused) -> v.setBackground(round(
                focused ? 0x88466C92 : 0x66303D55, dp(18),
                focused ? 0xFFE5F5FF : 0x55DDEEFF, focused ? dp(2) : dp(1))));
        header.addView(wifiView, new LinearLayout.LayoutParams(-2, dp(44)));

        searchView = headerButton("⌕", "Recherche");
        searchView.setId(View.generateViewId());
        searchView.setOnClickListener(v -> showSearch());
        header.addView(searchView, new LinearLayout.LayoutParams(dp(48), dp(48)));

        settingsView = headerButton("⚙", "Paramètres");
        settingsView.setId(View.generateViewId());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.leftMargin = dp(8);
        header.addView(settingsView, settingsParams);
        settingsView.setOnClickListener(v -> openSettingsMenu());

        wifiView.setNextFocusRightId(searchView.getId());
        searchView.setNextFocusLeftId(wifiView.getId());
        searchView.setNextFocusRightId(settingsView.getId());
        settingsView.setNextFocusLeftId(searchView.getId());
        return header;
    }

    private LinearLayout buildLastAppCard() {
        LinearLayout card = new LinearLayout(this);
        card.setId(View.generateViewId());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.setBackground(round(0x77303D55, dp(18), 0x88DDEEFF, dp(1)));
        card.setFocusable(true);
        card.setContentDescription("Last app used");

        lastAppIconView = new ImageView(this);
        lastAppIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconParams.rightMargin = dp(13);
        card.addView(lastAppIconView, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("DERNIÈRE APPLICATION", 10, 0xCFFFFFFF);
        heading.setLetterSpacing(0.08f);
        lastAppNameView = text("", 15, Color.WHITE);
        lastAppNameView.setSingleLine(true);
        lastAppNameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(heading);
        labels.addView(lastAppNameView);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView play = text("▶  REPRENDRE", 11, Color.WHITE);
        play.setGravity(Gravity.CENTER);
        card.addView(play, new LinearLayout.LayoutParams(-2, dp(42)));
        card.setOnFocusChangeListener((v, focused) -> v.setBackground(round(
                focused ? 0x88466C92 : 0x77303D55, dp(18),
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
            updateAppFocusLinks();
            return;
        }
        AppEntry appToLaunch = lastApp;
        lastAppCard.setVisibility(View.VISIBLE);
        lastAppCard.setContentDescription("Reprendre " + lastApp.label);
        lastAppIconView.setImageDrawable(lastApp.icon);
        lastAppNameView.setText(lastApp.label);
        lastAppCard.setOnClickListener(v -> launch(appToLaunch));
        updateAppFocusLinks();
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

    private void refreshAppGrid() {
        if (appGrid == null) return;
        List<AppEntry> refreshed = findLaunchableApps();
        boolean changed = refreshed.size() != launchableApps.size();
        if (!changed) {
            for (int i = 0; i < refreshed.size(); i++) {
                AppEntry oldApp = launchableApps.get(i);
                AppEntry newApp = refreshed.get(i);
                if (!oldApp.packageName.equals(newApp.packageName)
                        || !oldApp.label.equals(newApp.label)) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return;

        launchableApps.clear();
        launchableApps.addAll(refreshed);
        appGrid.removeAllViews();
        if (launchableApps.isEmpty()) {
            TextView empty = text("Aucune application de lancement détectée", 18, Color.WHITE);
            empty.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(0), GridLayout.spec(0));
            params.width = dp(appCellWidthDp * 4);
            params.height = dp(70);
            appGrid.addView(empty, params);
        } else {
            populateAppGrid(appGrid, appCellWidthDp);
        }
    }

    private void populateAppGrid(GridLayout grid, int cellWidthDp) {
        grid.removeAllViews();
        for (int i = 0; i < launchableApps.size(); i++) {
            View tile = createAppTile(launchableApps.get(i), cellWidthDp);
            if (i < 6 && wifiView != null) tile.setNextFocusUpId(wifiView.getId());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 6, GridLayout.CENTER),
                    GridLayout.spec(i % 6, GridLayout.CENTER));
            params.width = dp(cellWidthDp);
            params.height = dp(appTileHeightDp);
            params.setMargins(dp(6), dp(8), dp(6), dp(8));
            grid.addView(tile, params);
        }
        if (grid.getChildCount() > 0 && wifiView != null) {
            updateAppFocusLinks();
        }
    }

    private void updateAppFocusLinks() {
        if (appGrid == null || appGrid.getChildCount() == 0 || wifiView == null) return;
        View firstApp = appGrid.getChildAt(0);
        int firstAppId = firstApp.getId();
        if (!firstApp.isFocusable() || firstAppId == View.NO_ID) {
            wifiView.setNextFocusDownId(wifiView.getId());
            if (searchView != null) searchView.setNextFocusDownId(searchView.getId());
            if (settingsView != null) settingsView.setNextFocusDownId(settingsView.getId());
            return;
        }
        boolean showLastApp = lastAppCard != null && lastAppCard.getVisibility() == View.VISIBLE;
        int headerDownTarget = showLastApp ? lastAppCard.getId() : firstAppId;
        wifiView.setNextFocusDownId(headerDownTarget);
        if (searchView != null) searchView.setNextFocusDownId(headerDownTarget);
        if (settingsView != null) settingsView.setNextFocusDownId(headerDownTarget);
        if (showLastApp) {
            lastAppCard.setNextFocusUpId(wifiView.getId());
            lastAppCard.setNextFocusDownId(firstAppId);
        }
        for (int i = 0; i < Math.min(6, appGrid.getChildCount()); i++) {
            View tile = appGrid.getChildAt(i);
            tile.setNextFocusUpId(showLastApp ? lastAppCard.getId() : wifiView.getId());
        }
    }

    private View createAppTile(AppEntry app, int cellWidthDp) {
        LinearLayout tile = new LinearLayout(this);
        tile.setId(View.generateViewId());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(true);
        tile.setContentDescription(app.label);
        tile.setPadding(dp(3), dp(2), dp(3), dp(2));

        int cardHeightDp = appTileHeightDp - 32;
        int radius = dp(22);
        int backgroundColor = dominantIconColor(app.icon);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(gradientRound(blendColor(backgroundColor, Color.WHITE, 0.13f),
                blendColor(backgroundColor, Color.BLACK, 0.28f), radius, 0x66FFFFFF, dp(1)));
        card.setClipToOutline(true);
        card.setElevation(dp(3));

        Drawable adaptiveBackground = adaptiveIconBackground(app.icon);
        if (adaptiveBackground != null) {
            ImageView background = new ImageView(this);
            background.setImageDrawable(adaptiveBackground);
            background.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(background, new FrameLayout.LayoutParams(-1, -1));
        }

        View sheen = new View(this);
        GradientDrawable sheenDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x32FFFFFF, 0x0EFFFFFF, 0x00000000});
        sheenDrawable.setCornerRadius(radius);
        sheen.setBackground(sheenDrawable);
        card.addView(sheen, new FrameLayout.LayoutParams(-1, -1));

        int iconSize = Math.min(dp(94), dp(cardHeightDp - 8));
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

        TextView caption = text(app.label, 15, 0xFFFFFFFF);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        caption.setShadowLayer(dp(3), 0, dp(1), 0xB3000000);
        caption.setPadding(dp(2), dp(6), dp(2), 0);
        tile.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        tile.setOnFocusChangeListener((v, focused) -> {
            outline.setBackground(round(0x00000000, radius,
                    focused ? 0xFFE5F5FF : 0x66FFFFFF, focused ? dp(2) : dp(1)));
            card.animate().scaleX(focused ? 1.055f : 1f).scaleY(focused ? 1.055f : 1f)
                    .setDuration(170).start();
            card.setElevation(focused ? dp(18) : dp(3));
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

    private FrameLayout buildSettingsMenu() {
        FrameLayout panel = new FrameLayout(this);
        panel.setVisibility(View.GONE);
        panel.setFocusable(true);
        panel.setFocusableInTouchMode(true);
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF10A1422, 0xF50A1220, 0xF9080E18}));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(56), dp(38), dp(56), dp(38));
        panel.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Réglages", 30, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sidebar.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView subtitle = text("RÉGLAGES DE LA BOX", 11, 0xBFFFFFFF);
        subtitle.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, dp(24));
        subtitleParams.bottomMargin = dp(14);
        sidebar.addView(subtitle, subtitleParams);

        settingsCategoryColumn = new LinearLayout(this);
        settingsCategoryColumn.setOrientation(LinearLayout.VERTICAL);
        sidebar.addView(settingsCategoryColumn, new LinearLayout.LayoutParams(-1, -2));
        settingsCategoryButtons.clear();
        for (int i = 0; i < SETTINGS_MENU_LABELS.length; i++) {
            final int category = i;
            TextView item = text(SETTINGS_MENU_LABELS[i], 17, 0xFFE8EAF0);
            item.setId(View.generateViewId());
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(18), 0, dp(12), 0);
            item.setFocusable(true);
            item.setBackground(round(0x44313D4C, dp(16), 0x00000000, 0));
            item.setContentDescription("Réglage : " + SETTINGS_MENU_LABELS[i]);
            item.setOnFocusChangeListener((v, focused) -> {
                if (focused) selectSettingsCategory(category);
                updateSettingsCategoryStyles();
            });
            item.setOnClickListener(v -> selectSettingsCategory(category));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, dp(52));
            itemParams.bottomMargin = dp(4);
            settingsCategoryColumn.addView(item, itemParams);
            settingsCategoryButtons.add(item);
        }
        LinearLayout.LayoutParams sidebarParams = new LinearLayout.LayoutParams(dp(278), -1);
        sidebarParams.rightMargin = dp(30);
        content.addView(sidebar, sidebarParams);

        LinearLayout detailPane = new LinearLayout(this);
        detailPane.setOrientation(LinearLayout.VERTICAL);
        detailPane.setGravity(Gravity.CENTER_VERTICAL);
        detailPane.setPadding(dp(38), dp(34), dp(38), dp(34));
        detailPane.setBackground(gradientRound(0xE5233042, 0xE5121B29,
                dp(28), 0x55FFFFFF, dp(1)));
        content.addView(detailPane, new LinearLayout.LayoutParams(0, -1, 1));

        TextView sectionLabel = text("APPAREIL", 11, 0xFFBAC7D6);
        sectionLabel.setLetterSpacing(0.16f);
        detailPane.addView(sectionLabel, new LinearLayout.LayoutParams(-1, dp(26)));
        settingsDetailTitle = text("", 28, Color.WHITE);
        settingsDetailTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams detailTitleParams = new LinearLayout.LayoutParams(-1, -2);
        detailTitleParams.topMargin = dp(4);
        detailPane.addView(settingsDetailTitle, detailTitleParams);
        settingsDetailDescription = text("", 16, 0xFFD5DAE2);
        settingsDetailDescription.setGravity(Gravity.TOP | Gravity.START);
        settingsDetailDescription.setLineSpacing(dp(4), 1.0f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(14);
        descriptionParams.bottomMargin = dp(30);
        detailPane.addView(settingsDetailDescription, descriptionParams);

        settingsDetailBody = new LinearLayout(this);
        settingsDetailBody.setOrientation(LinearLayout.VERTICAL);
        detailPane.addView(settingsDetailBody, new LinearLayout.LayoutParams(-1, -2));

        TextView footer = text("FLÈCHES : NAVIGUER     OK : OUVRIR     RETOUR : FERMER",
                11, 0xCFFFFFFF);
        footer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams footerParams = new FrameLayout.LayoutParams(-1, dp(36),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        footerParams.bottomMargin = dp(16);
        panel.addView(footer, footerParams);

        for (int i = 0; i < settingsCategoryButtons.size(); i++) {
            TextView item = settingsCategoryButtons.get(i);
            item.setNextFocusUpId(settingsCategoryButtons.get(Math.max(0, i - 1)).getId());
            item.setNextFocusDownId(settingsCategoryButtons.get(
                    Math.min(settingsCategoryButtons.size() - 1, i + 1)).getId());
            item.setNextFocusLeftId(item.getId());
        }
        selectedSettingsCategory = 0;
        selectSettingsCategory(0);
        return panel;
    }

    private void openSettingsMenu() {
        if (settingsPanel == null) return;
        settingsPanel.setVisibility(View.VISIBLE);
        selectSettingsCategory(selectedSettingsCategory);
        settingsCategoryButtons.get(selectedSettingsCategory).requestFocus();
    }

    private void closeSettingsMenu() {
        if (settingsPanel == null) return;
        settingsPanel.setVisibility(View.GONE);
        focusSettingsActionOnReturn = false;
        if (settingsView != null) settingsView.requestFocus();
    }

    private void selectSettingsCategory(int category) {
        if (category < 0 || category >= SETTINGS_MENU_LABELS.length
                || settingsDetailBody == null) return;
        selectedSettingsCategory = category;
        settingsDetailTitle.setText(SETTINGS_MENU_LABELS[category]);
        settingsDetailDescription.setText(SETTINGS_MENU_DESCRIPTIONS[category]);
        settingsDetailBody.removeAllViews();

        TextView action = text(SETTINGS_MENU_ACTION_LABELS[category] + "   ›", 18, Color.WHITE);
        action.setId(View.generateViewId());
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(20), 0, dp(20), 0);
        action.setFocusable(true);
        action.setContentDescription(SETTINGS_MENU_ACTION_LABELS[category]);
        action.setBackground(gradientRound(0xFF536D87, 0xFF34485F,
                dp(18), 0x77FFFFFF, dp(1)));
        action.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(gradientRound(
                    focused ? 0xFF6985A0 : 0xFF536D87,
                    focused ? 0xFF465D75 : 0xFF34485F,
                    dp(18), focused ? 0xFFF0F5FB : 0x77FFFFFF,
                    focused ? dp(2) : dp(1)));
            v.setElevation(focused ? dp(12) : dp(2));
            v.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f)
                    .setDuration(150).start();
        });
        action.setOnClickListener(v -> openSelectedSettingPage());
        settingsDetailBody.addView(action, new LinearLayout.LayoutParams(-1, dp(64)));
        settingsActionView = action;

        for (TextView item : settingsCategoryButtons) {
            item.setNextFocusRightId(action.getId());
        }
        action.setNextFocusLeftId(settingsCategoryButtons.get(category).getId());
        action.setNextFocusRightId(action.getId());
        action.setNextFocusUpId(action.getId());
        action.setNextFocusDownId(action.getId());
        updateSettingsCategoryStyles();
    }

    private void updateSettingsCategoryStyles() {
        for (int i = 0; i < settingsCategoryButtons.size(); i++) {
            TextView item = settingsCategoryButtons.get(i);
            boolean active = i == selectedSettingsCategory;
            boolean focused = item.hasFocus();
            item.setTextColor(active || focused ? Color.WHITE : 0xFFE8EAF0);
            item.setTypeface(Typeface.create(active || focused
                    ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            item.setBackground(round(active || focused ? 0x77677F98 : 0x44313D4C,
                    dp(16), active || focused ? 0xCCEDF4FC : 0x00000000,
                    active || focused ? dp(1) : 0));
        }
    }

    private void openSelectedSettingPage() {
        try {
            focusSettingsActionOnReturn = true;
            startActivity(new Intent(SETTINGS_MENU_ACTIONS[selectedSettingsCategory]));
        } catch (Exception e) {
            focusSettingsActionOnReturn = false;
            Toast.makeText(this, "Ces réglages ne sont pas accessibles sur cette box.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Les paramètres ne sont pas accessibles.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            openSettings();
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

    private GradientDrawable gradientRound(int startColor, int endColor, int radius,
                                           int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int blendColor(int color, int target, float amount) {
        float inverse = 1.0f - amount;
        return Color.rgb(
                Math.round(Color.red(color) * inverse + Color.red(target) * amount),
                Math.round(Color.green(color) * inverse + Color.green(target) * amount),
                Math.round(Color.blue(color) * inverse + Color.blue(target) * amount));
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
