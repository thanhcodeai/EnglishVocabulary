package com.example.englishvocabulary.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.content.ContextCompat;
import com.example.englishvocabulary.R;

/** Static progress has no visibility animation and stays readable through theme/font changes. */
public final class LessonProgressView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int value, total;

  public LessonProgressView(Context context, int value, int total) {
    super(context);
    this.total = Math.max(1, total);
    this.value = Math.max(0, Math.min(value, this.total));
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float h = getHeight(), w = getWidth();
    paint.setColor(ContextCompat.getColor(getContext(), R.color.ui_line));
    canvas.drawRoundRect(0, 0, w, h, h / 2, h / 2, paint);
    if (value > 0) {
      paint.setColor(ContextCompat.getColor(getContext(), R.color.ui_green));
      canvas.drawRoundRect(0, 0, w * value / total, h, h / 2, h / 2, paint);
    }
  }

  @Override
  public CharSequence getAccessibilityClassName() {
    return android.widget.ProgressBar.class.getName();
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setRangeInfo(
        AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, total, value));
  }
}
