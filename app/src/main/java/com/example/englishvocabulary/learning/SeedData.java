package com.example.englishvocabulary.learning;

import android.content.Context;
import com.example.englishvocabulary.data.database.AppDatabase;
import com.example.englishvocabulary.data.entity.*;

/** Demo dataset owned by the app; inserts once, in a single transaction. */
final class SeedData {
  static void ensure(Context context, AppDatabase db) {
    if (context.getSharedPreferences("seed", 0).getBoolean("v2", false)) return;
    db.runInTransaction(
        () -> {
          if (!db.learningDao().words().isEmpty())
            return; // Never overwrite an existing user's vocabulary.
          String[] names = {"Daily life", "Travel", "Food", "Technology", "Education", "Nature"};
          String[][][] words = {
            {
              {"morning", "buổi sáng"},
              {"evening", "buổi tối"},
              {"family", "gia đình"},
              {"friend", "bạn bè"},
              {"house", "ngôi nhà"},
              {"work", "công việc"},
              {"sleep", "ngủ"},
              {"read", "đọc"},
              {"happy", "vui vẻ"},
              {"busy", "bận rộn"}
            },
            {
              {"airport", "sân bay"},
              {"ticket", "vé"},
              {"passport", "hộ chiếu"},
              {"hotel", "khách sạn"},
              {"journey", "hành trình"},
              {"map", "bản đồ"},
              {"luggage", "hành lý"},
              {"train", "tàu hỏa"},
              {"beach", "bãi biển"},
              {"bridge", "cây cầu"}
            },
            {
              {"apple", "quả táo"},
              {"bread", "bánh mì"},
              {"rice", "cơm"},
              {"water", "nước"},
              {"milk", "sữa"},
              {"coffee", "cà phê"},
              {"vegetable", "rau củ"},
              {"fish", "cá"},
              {"breakfast", "bữa sáng"},
              {"delicious", "ngon"}
            },
            {
              {"computer", "máy tính"},
              {"keyboard", "bàn phím"},
              {"screen", "màn hình"},
              {"software", "phần mềm"},
              {"network", "mạng"},
              {"database", "cơ sở dữ liệu"},
              {"password", "mật khẩu"},
              {"device", "thiết bị"},
              {"technology", "công nghệ"},
              {"application", "ứng dụng"}
            },
            {
              {"school", "trường học"},
              {"teacher", "giáo viên"},
              {"student", "học sinh"},
              {"lesson", "bài học"},
              {"book", "sách"},
              {"library", "thư viện"},
              {"question", "câu hỏi"},
              {"answer", "câu trả lời"},
              {"practice", "luyện tập"},
              {"knowledge", "kiến thức"}
            },
            {
              {"tree", "cây"},
              {"flower", "hoa"},
              {"river", "sông"},
              {"mountain", "núi"},
              {"forest", "rừng"},
              {"rain", "mưa"},
              {"sun", "mặt trời"},
              {"moon", "mặt trăng"},
              {"wind", "gió"},
              {"ocean", "đại dương"}
            }
          };
          long now = System.currentTimeMillis();
          for (int i = 0; i < names.length; i++) {
            int topic = (int) db.topicDao().insertTopic(new Topic(names[i], names[i], now));
            for (String[] w : words[i])
              db.vocabularyDao()
                  .insertVocabulary(
                      new Vocabulary(w[0], w[1], null, null, null, topic, false, now, now));
          }
        });
    context.getSharedPreferences("seed", 0).edit().putBoolean("v2", true).commit();
  }
}
