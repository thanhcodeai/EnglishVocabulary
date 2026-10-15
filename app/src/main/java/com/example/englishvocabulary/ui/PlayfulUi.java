package com.example.englishvocabulary.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import com.example.englishvocabulary.R;
import com.google.android.material.button.MaterialButton;

/** Shared visual vocabulary: rounded type, tactile controls and readable, themed surfaces. */
public final class PlayfulUi {
  private final Context context;
  private final Typeface regular, bold;

  public PlayfulUi(Context context) {
    this.context = context;
    regular = ResourcesCompat.getFont(context, R.font.nunito_regular);
    bold = ResourcesCompat.getFont(context, R.font.nunito_bold);
  }

  public int dp(int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  public int color(int value) {
    return ContextCompat.getColor(context, value);
  }

  public Typeface bold() {
    return bold;
  }

  public TextView label(CharSequence text, int size, boolean heading) {
    TextView v = new TextView(context);
    v.setText(text);
    v.setTextSize(size);
    v.setTypeface(heading ? bold : regular);
    v.setTextColor(color(heading ? R.color.ui_ink : R.color.ui_muted));
    v.setLineSpacing(dp(2), 1f);
    return v;
  }

  public LinearLayout column() {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.VERTICAL);
    return box;
  }

  public LinearLayout panel(int background) {
    LinearLayout box = column();
    box.setBackgroundResource(background);
    box.setPadding(dp(18), dp(18), dp(18), dp(20));
    return box;
  }

  public LinearLayout.LayoutParams spaced() {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(10);
    p.bottomMargin = dp(6);
    return p;
  }

  public void button(MaterialButton button, boolean primary) {
    button.setBackgroundTintList(null);
    button.setBackgroundResource(primary ? R.drawable.button_action : R.drawable.button_outline);
    button.setStateListAnimator(null);
    button.setElevation(0);
    button.setTypeface(bold);
    button.setTextSize(15);
    button.setAllCaps(false);
    button.setLetterSpacing(.01f);
    button.setMinHeight(dp(60));
    button.setPadding(dp(18), dp(10), dp(18), dp(14));
    button.setTextColor(
        new ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {color(R.color.ui_muted), primary ? 0xFF214600 : color(R.color.ui_ink)}));
  }

  public View intro(int title, int caption, int icon) {
    LinearLayout box = new LinearLayout(context);
    box.setOrientation(LinearLayout.HORIZONTAL);
    box.setGravity(Gravity.CENTER_VERTICAL);
    box.setBackgroundResource(R.drawable.bg_soft);
    box.setPadding(dp(18), dp(18), dp(14), dp(18));
    LinearLayout copy = column();
    TextView heading = label(context.getString(title), 23, true);
    heading.setTextColor(color(R.color.ui_on_brand_soft));
    copy.addView(heading);
    TextView subtitle = label(context.getString(caption), 14, false);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(6);
    copy.addView(subtitle, p);
    box.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    ImageView illustration = new ImageView(context);
    illustration.setImageResource(icon);
    if (icon != R.drawable.companion_fox)
      illustration.setImageTintList(ColorStateList.valueOf(color(R.color.ui_brand)));
    illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    int size = icon == R.drawable.companion_fox ? 96 : 48;
    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(size), dp(size));
    imageParams.setMarginStart(dp(12));
    box.addView(illustration, imageParams);
    return box;
  }

  public View info(CharSequence title, CharSequence value, int background) {
    LinearLayout box = panel(background);
    box.addView(label(title, 13, false));
    TextView content = label(value, 18, true);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(6);
    box.addView(content, p);
    return box;
  }
}
