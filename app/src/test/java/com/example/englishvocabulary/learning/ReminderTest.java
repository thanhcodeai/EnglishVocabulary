package com.example.englishvocabulary.learning;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import com.example.englishvocabulary.learning.Records.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReminderTest {
  @Test
  public void changeTimeReplacesAlarmAndLogoutCancelsIt() {
    Context context = RuntimeEnvironment.getApplication();
    AppState s = new AppState();
    s.user = new User();
    s.user.userId = 1;
    s.settings = new Settings();
    s.settings.reminder = true;
    AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    ReminderScheduler.sync(context, s);
    assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    s.settings.hour = 8;
    ReminderScheduler.sync(context, s);
    assertEquals(1, Shadows.shadowOf(alarms).getScheduledAlarms().size());
    s.settings.reminder = false;
    ReminderScheduler.sync(context, s);
    assertTrue(Shadows.shadowOf(alarms).getScheduledAlarms().isEmpty());
    s.settings.reminder = true;
    ReminderScheduler.sync(context, s);
    s.user = null;
    ReminderScheduler.sync(context, s);
    assertTrue(Shadows.shadowOf(alarms).getScheduledAlarms().isEmpty());
  }

  @Test
  public void deniedNotificationsDoNotCreateAlarm() {
    Context context = RuntimeEnvironment.getApplication();
    Shadows.shadowOf(context.getSystemService(NotificationManager.class))
        .setNotificationsEnabled(false);
    AppState s = new AppState();
    s.user = new User();
    s.settings = new Settings();
    s.settings.reminder = true;
    assertFalse(ReminderScheduler.permitted(context));
    ReminderScheduler.sync(context, s);
    assertTrue(
        Shadows.shadowOf((AlarmManager) context.getSystemService(Context.ALARM_SERVICE))
            .getScheduledAlarms()
            .isEmpty());
  }
}
