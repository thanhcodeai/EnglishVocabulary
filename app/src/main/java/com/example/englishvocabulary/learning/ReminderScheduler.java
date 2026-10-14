package com.example.englishvocabulary.learning;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.*;
import androidx.core.content.ContextCompat;
import com.example.englishvocabulary.MainActivity;
import com.example.englishvocabulary.R;
import java.time.*;

/** One inexact alarm for the currently signed-in account; never asks for exact-alarm access. */
public final class ReminderScheduler {
  static final String CHANNEL = "learning_reminders";

  private ReminderScheduler() {}

  public static boolean permitted(Context c) {
    return NotificationManagerCompat.from(c).areNotificationsEnabled()
        && (Build.VERSION.SDK_INT < 33
            || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED);
  }

  private static PendingIntent pending(Context c) {
    return PendingIntent.getBroadcast(
        c,
        14,
        new Intent(c, ReminderReceiver.class).setAction("com.example.englishvocabulary.REMIND"),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public static void sync(Context c, AppState state) {
    AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    alarm.cancel(pending(c));
    if (state.user == null || !state.settings.reminder || !permitted(c)) {
      NotificationManagerCompat.from(c).cancel(14);
      return;
    }
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel =
          new NotificationChannel(
              CHANNEL,
              c.getString(R.string.notification_channel),
              NotificationManager.IMPORTANCE_DEFAULT);
      c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    ZonedDateTime now = ZonedDateTime.now();
    ZonedDateTime next =
        now.withHour(state.settings.hour)
            .withMinute(state.settings.minute)
            .withSecond(0)
            .withNano(0);
    if (!next.isAfter(now)) next = next.plusDays(1);
    // Repeating inexact alarm is replaced after each receipt/timezone change to follow local time.
    alarm.setAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(c));
  }

  static void show(Context c, AppState state) {
    if (state.user == null || !state.settings.reminder || !permitted(c)) return;
    android.content.res.Configuration config =
        new android.content.res.Configuration(c.getResources().getConfiguration());
    config.setLocale(java.util.Locale.forLanguageTag(state.settings.language));
    Context localized = c.createConfigurationContext(config);
    Intent intent =
        new Intent(c, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("notificationHome", true);
    PendingIntent open =
        PendingIntent.getActivity(
            c, 14, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification notification =
        new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_learning)
            .setContentTitle(localized.getString(R.string.notification_title))
            .setContentText(localized.getString(R.string.notification_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build();
    try {
      NotificationManagerCompat.from(c).notify(14, notification);
    } catch (SecurityException ignored) {
      /* Permission may be revoked between the check and notify. */
    }
  }
}
