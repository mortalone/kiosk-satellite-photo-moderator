// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.photomoderator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class ModeratorDialogView extends FrameLayout {
    public interface Listener {
        void onCloseRequested();
        void onReportNsfw(PhotoItem photo);
        void onReportStranger(PhotoItem photo);
        void onBlacklist(PhotoItem photo);
        void onPostponeScreensaver();
        void onPhotoChanged(PhotoItem photo);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final int autoDismissSeconds;

    private final List<PhotoItem> photos = new ArrayList<>();
    private int currentIndex = 0;

    private LinearLayout cardLayout;
    private TextView titleText;
    private TextView subtitleText;
    private ImageView thumbImageView;
    private ProgressBar thumbProgressBar;
    private TextView fileNameText;
    private TextView takenAtText;
    private TextView shownAgoText;
    private TextView subjectsText;
    private TextView indexCounterText;
    private TextView statusBannerText;
    private View prevButton;
    private View nextButton;
    private View nsfwButton;
    private View strangerButton;
    private View blacklistButton;

    private final Runnable autoDismissRunnable = new Runnable() {
        @Override
        public void run() {
            if (listener != null) listener.onCloseRequested();
        }
    };

    public ModeratorDialogView(Context context, int autoDismissSeconds, Listener listener) {
        super(context);
        this.listener = listener;
        this.autoDismissSeconds = Math.max(10, autoDismissSeconds);

        setBackgroundColor(0x99000000); // 60% dimmed backdrop
        setClickable(true);
        setFocusable(true);

        buildUi(context);
        resetAutoDismiss();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void resetAutoDismiss() {
        main.removeCallbacks(autoDismissRunnable);
        main.postDelayed(autoDismissRunnable, autoDismissSeconds * 1000L);
        if (listener != null) listener.onPostponeScreensaver();
    }

    private GradientDrawable createCardBg(int bgColor, int radiusDp, int strokeColor, float strokeWidthDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0 && strokeWidthDp > 0) {
            bg.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return bg;
    }

    private void buildUi(Context ctx) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        boolean isLandscape = screenW > screenH;

        // Outer centering container
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                Math.min(dp(560), screenW - dp(28)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );

        cardLayout = new LinearLayout(ctx);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setBackground(createCardBg(0xFF161C24, 16, 0xFF313D4F, 1.5f));
        cardLayout.setPadding(dp(18), dp(16), dp(18), dp(18));
        cardLayout.setClickable(true);
        cardLayout.setOnTouchListener((v, event) -> {
            resetAutoDismiss();
            return true; // Consume touches inside card
        });

        // 1. Header (Title + Close Button)
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleText = new TextView(ctx);
        titleText.setText("🛡️ Foto Moderation");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(18f);
        titleText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleText, titleLp);

        TextView closeBtn = new TextView(ctx);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFF94A3B8);
        closeBtn.setTextSize(20f);
        closeBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        closeBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
        closeBtn.setBackground(createCardBg(0x22FFFFFF, 8, 0, 0));
        closeBtn.setOnClickListener(v -> {
            if (listener != null) listener.onCloseRequested();
        });
        header.addView(closeBtn);
        cardLayout.addView(header);

        // Subtitle
        subtitleText = new TextView(ctx);
        subtitleText.setText("Henter senest viste fotos...");
        subtitleText.setTextColor(0xFF94A3B8);
        subtitleText.setTextSize(12.5f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        subLp.bottomMargin = dp(14);
        cardLayout.addView(subtitleText, subLp);

        // 2. Photo Info & Thumbnail Section
        LinearLayout photoRow = new LinearLayout(ctx);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);
        photoRow.setBackground(createCardBg(0xFF10141A, 10, 0xFF232D3B, 1f));
        photoRow.setPadding(dp(10), dp(10), dp(10), dp(10));

        FrameLayout thumbContainer = new FrameLayout(ctx);
        int thumbSize = dp(110);
        thumbContainer.setLayoutParams(new LinearLayout.LayoutParams(thumbSize, thumbSize));
        thumbContainer.setBackground(createCardBg(0xFF0A0D12, 8, 0, 0));

        thumbImageView = new ImageView(ctx);
        thumbImageView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        thumbImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbContainer.addView(thumbImageView);

        thumbProgressBar = new ProgressBar(ctx);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER);
        thumbContainer.addView(thumbProgressBar, pbLp);
        photoRow.addView(thumbContainer);

        LinearLayout infoCol = new LinearLayout(ctx);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(dp(12), dp(0), dp(4), dp(0));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        fileNameText = new TextView(ctx);
        fileNameText.setTextColor(Color.WHITE);
        fileNameText.setTextSize(14f);
        fileNameText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        fileNameText.setSingleLine(true);
        fileNameText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        infoCol.addView(fileNameText);

        takenAtText = new TextView(ctx);
        takenAtText.setTextColor(0xFF94A3B8);
        takenAtText.setTextSize(12f);
        infoCol.addView(takenAtText);

        shownAgoText = new TextView(ctx);
        shownAgoText.setTextColor(0xFF94A3B8);
        shownAgoText.setTextSize(12f);
        infoCol.addView(shownAgoText);

        subjectsText = new TextView(ctx);
        subjectsText.setTextColor(0xFF60A5FA); // Sky blue
        subjectsText.setTextSize(12.5f);
        subjectsText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams subjLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subjLp.topMargin = dp(4);
        infoCol.addView(subjectsText, subjLp);

        photoRow.addView(infoCol, infoLp);
        cardLayout.addView(photoRow);

        // 3. Navigation Controls (Prev / Counter / Next)
        LinearLayout navRow = new LinearLayout(ctx);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navLp.topMargin = dp(10);
        navLp.bottomMargin = dp(12);

        prevButton = createButton(ctx, "◀ Forrige", 0xFF1E293B, Color.WHITE, dp(9));
        prevButton.setOnClickListener(v -> {
            resetAutoDismiss();
            stepToEarlierPhoto();
        });
        navRow.addView(prevButton, new LinearLayout.LayoutParams(dp(110), dp(38)));

        indexCounterText = new TextView(ctx);
        indexCounterText.setTextColor(0xFFCBD5E1);
        indexCounterText.setTextSize(13f);
        indexCounterText.setGravity(Gravity.CENTER);
        indexCounterText.setText("0 / 0");
        navRow.addView(indexCounterText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        nextButton = createButton(ctx, "Næste ▶", 0xFF1E293B, Color.WHITE, dp(9));
        nextButton.setOnClickListener(v -> {
            resetAutoDismiss();
            stepToLaterPhoto();
        });
        navRow.addView(nextButton, new LinearLayout.LayoutParams(dp(110), dp(38)));
        cardLayout.addView(navRow, navLp);

        // 4. Action Buttons
        LinearLayout actionsLayout = new LinearLayout(ctx);
        actionsLayout.setOrientation(LinearLayout.VERTICAL);

        // Nudity Button
        nsfwButton = createActionButton(ctx, "🔞  Markér som nøgenhed (fjern omgående)", 0xFFB91C1C, Color.WHITE);
        nsfwButton.setOnClickListener(v -> {
            resetAutoDismiss();
            PhotoItem cur = getCurrentPhoto();
            if (cur != null && listener != null) {
                showStatusBanner("Fjerner billede pga. nøgenhed...", 0xFFF59E0B);
                listener.onReportNsfw(cur);
            }
        });
        actionsLayout.addView(nsfwButton, createActionLp());

        // Stranger / Wrong person Button
        strangerButton = createActionButton(ctx, "👤  Fremmed / ikke denne person", 0xFFD97706, Color.WHITE);
        strangerButton.setOnClickListener(v -> {
            resetAutoDismiss();
            PhotoItem cur = getCurrentPhoto();
            if (cur != null && listener != null) {
                showStatusBanner("Fjerner person fra dette foto...", 0xFFF59E0B);
                listener.onReportStranger(cur);
            }
        });
        actionsLayout.addView(strangerButton, createActionLp());

        // Blacklist Button
        blacklistButton = createActionButton(ctx, "🚫  Vis aldrig mere (Blacklist)", 0xFF374151, 0xFFE5E7EB);
        blacklistButton.setOnClickListener(v -> {
            resetAutoDismiss();
            PhotoItem cur = getCurrentPhoto();
            if (cur != null && listener != null) {
                showStatusBanner("Blacklister billede permanent...", 0xFFF59E0B);
                listener.onBlacklist(cur);
            }
        });
        actionsLayout.addView(blacklistButton, createActionLp());

        cardLayout.addView(actionsLayout);

        // 5. Status Banner
        statusBannerText = new TextView(ctx);
        statusBannerText.setVisibility(View.GONE);
        statusBannerText.setGravity(Gravity.CENTER);
        statusBannerText.setPadding(dp(8), dp(8), dp(8), dp(8));
        statusBannerText.setTextSize(13f);
        statusBannerText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(10);
        cardLayout.addView(statusBannerText, statusLp);

        // 6. Resume / Close Button
        View resumeBtn = createButton(ctx, "▶ Fortsæt pauseskærm", 0xFF1E293B, 0xFF93C5FD, dp(10));
        resumeBtn.setOnClickListener(v -> {
            if (listener != null) listener.onCloseRequested();
        });
        LinearLayout.LayoutParams resumeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        resumeLp.topMargin = dp(12);
        cardLayout.addView(resumeBtn, resumeLp);

        addView(cardLayout, cardParams);
    }

    private LinearLayout.LayoutParams createActionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(6);
        return lp;
    }

    private View createActionButton(Context ctx, String text, int bgColor, int textColor) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(createCardBg(bgColor, 8, 0x44FFFFFF, 1f));
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    private View createButton(Context ctx, String text, int bgColor, int textColor, int radiusDp) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(createCardBg(bgColor, radiusDp, 0x33FFFFFF, 1f));
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    public void setPhotos(List<PhotoItem> list) {
        photos.clear();
        if (list != null) photos.addAll(list);
        currentIndex = 0;
        updateDisplay();
        if (listener != null) {
            listener.onPhotoChanged(getCurrentPhoto());
        }
    }

    public PhotoItem getCurrentPhoto() {
        if (photos.isEmpty() || currentIndex < 0 || currentIndex >= photos.size()) return null;
        return photos.get(currentIndex);
    }

    /**
     * "◀ Forrige" button: Step back in time to photos displayed earlier in history.
     * Index 0 is the current photo on screen; earlier photos have index 1, 2, ...
     */
    public void stepToEarlierPhoto() {
        if (photos.isEmpty()) return;
        if (currentIndex < photos.size() - 1) {
            currentIndex++;
            updateDisplay();
            if (listener != null) {
                listener.onPhotoChanged(getCurrentPhoto());
            }
        }
    }

    /**
     * "Næste ▶" button: Step forward in time back towards the active photo on screen.
     * Decrements index towards 0.
     */
    public void stepToLaterPhoto() {
        if (photos.isEmpty()) return;
        if (currentIndex > 0) {
            currentIndex--;
            updateDisplay();
            if (listener != null) {
                listener.onPhotoChanged(getCurrentPhoto());
            }
        }
    }

    public void stepPhoto(int delta) {
        if (delta > 0) {
            stepToEarlierPhoto();
        } else if (delta < 0) {
            stepToLaterPhoto();
        }
    }

    public void setThumbnailBitmap(Bitmap bmp) {
        thumbProgressBar.setVisibility(View.GONE);
        if (bmp != null) {
            thumbImageView.setImageBitmap(bmp);
        } else {
            thumbImageView.setImageDrawable(null);
        }
    }

    public void showStatusBanner(String message, int color) {
        statusBannerText.setText(message);
        statusBannerText.setTextColor(color);
        statusBannerText.setBackground(createCardBg(0x33000000 | (color & 0x00FFFFFF), 6, color, 1f));
        statusBannerText.setVisibility(View.VISIBLE);
    }

    public void onActionSuccess(String message) {
        showStatusBanner("✓ " + message, 0xFF10B981); // Emerald green
        // Disable action buttons for this photo since it is now rejected
        nsfwButton.setEnabled(false);
        nsfwButton.setAlpha(0.4f);
        strangerButton.setEnabled(false);
        strangerButton.setAlpha(0.4f);
        blacklistButton.setEnabled(false);
        blacklistButton.setAlpha(0.4f);
    }

    public void onActionError(String error) {
        showStatusBanner("✕ Fejl: " + error, 0xFFEF4444); // Red
    }

    private void updateDisplay() {
        statusBannerText.setVisibility(View.GONE);
        nsfwButton.setEnabled(true);
        nsfwButton.setAlpha(1.0f);
        strangerButton.setEnabled(true);
        strangerButton.setAlpha(1.0f);
        blacklistButton.setEnabled(true);
        blacklistButton.setAlpha(1.0f);

        if (photos.isEmpty()) {
            subtitleText.setText("Ingen nyligt viste fotos fundet.");
            fileNameText.setText("—");
            takenAtText.setText("—");
            shownAgoText.setText("—");
            subjectsText.setText("—");
            indexCounterText.setText("0 / 0");
            prevButton.setEnabled(false);
            prevButton.setAlpha(0.35f);
            nextButton.setEnabled(false);
            nextButton.setAlpha(0.35f);
            thumbImageView.setImageDrawable(null);
            thumbProgressBar.setVisibility(View.GONE);
            return;
        }

        PhotoItem photo = photos.get(currentIndex);

        // Header & info depending on whether this is the current active photo or earlier history
        if (currentIndex == 0) {
            indexCounterText.setText("🟢 Aktuelt (1 af " + photos.size() + ")");
            subtitleText.setText("Billedet der vises på pauseskærmen lige nu:");
            if (photo.shownAgoSeconds <= 5) {
                shownAgoText.setText("⏱️ Vises lige nu");
            } else {
                shownAgoText.setText("⏱️ Vist: " + formatAgo(photo.shownAgoSeconds));
            }
        } else {
            indexCounterText.setText("◀ " + currentIndex + " forrige (" + (currentIndex + 1) + " af " + photos.size() + ")");
            subtitleText.setText("Tidligere vist billede (" + currentIndex + " foto tilbage):");
            shownAgoText.setText("⏱️ Vist: " + formatAgo(photo.shownAgoSeconds));
        }

        fileNameText.setText(photo.fileName);
        takenAtText.setText("📅 Taget: " + (photo.takenAt.isEmpty() ? "Ukendt" : photo.takenAt));
        subjectsText.setText("👤 " + photo.subjectsString());

        // "◀ Forrige" goes back into history (increments index). Enabled if not at oldest photo.
        boolean canGoEarlier = currentIndex < photos.size() - 1;
        prevButton.setEnabled(canGoEarlier);
        prevButton.setAlpha(canGoEarlier ? 1.0f : 0.35f);

        // "Næste ▶" goes forward towards current photo (decrements index). Enabled if not at index 0.
        boolean canGoLater = currentIndex > 0;
        nextButton.setEnabled(canGoLater);
        nextButton.setAlpha(canGoLater ? 1.0f : 0.35f);

        thumbImageView.setImageDrawable(null);
        thumbProgressBar.setVisibility(View.VISIBLE);
    }

    private String formatAgo(int seconds) {
        if (seconds <= 0) return "Lige nu";
        if (seconds < 60) return seconds + " sek. siden";
        int mins = seconds / 60;
        if (mins < 60) return mins + " min. siden";
        int hours = mins / 60;
        return hours + " t. siden";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Tapping dimmed background outside card closes the dialog
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (listener != null) listener.onCloseRequested();
            return true;
        }
        return true;
    }
}
