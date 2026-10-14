package com.example.englishvocabulary.learning;

import android.content.*;

/** Rehydrates preferences from Room after boot, timezone/time changes, or alarm delivery. */
public final class ReminderReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    PendingResult pending = goAsync();
    Context app = context.getApplicationContext();
    AppRepository.get(app)
        .refresh(
            (state, error, id) -> {
              try {
                if (error == 0) {
                  if ("com.example.englishvocabulary.REMIND".equals(intent.getAction()))
                    ReminderScheduler.show(app, state);
                  ReminderScheduler.sync(app, state);
                }
              } finally {
                pending.finish();
              }
            });
  }
}
