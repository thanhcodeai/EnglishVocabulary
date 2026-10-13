
package com.example.englishvocabulary.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.example.englishvocabulary.data.entity.QuizResult;

import java.util.List;

@Dao
public interface QuizResultDao {

    // Lấy toàn bộ lịch sử trắc nghiệm, mới nhất trước
    @Query("SELECT * FROM quiz_results ORDER BY completedAt DESC")
    LiveData<List<QuizResult>> getAllQuizResults();

    // Lấy kết quả theo ID
    @Query("SELECT * FROM quiz_results WHERE resultId = :resultId LIMIT 1")
    LiveData<QuizResult> getQuizResultById(int resultId);

    // Thêm kết quả khi người dùng hoàn thành bài trắc nghiệm
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertQuizResult(QuizResult quizResult);

    // Xóa một kết quả
    @Delete
    int deleteQuizResult(QuizResult quizResult);

    // Đếm số bài trắc nghiệm đã hoàn thành
    @Query("SELECT COUNT(*) FROM quiz_results")
    int countQuizResults();

    // Lấy điểm trung bình
    @Query("SELECT AVG(scorePercent) FROM quiz_results")
    LiveData<Double> getAverageScore();

    // Lấy kết quả trắc nghiệm gần nhất
    @Query("SELECT * FROM quiz_results ORDER BY completedAt DESC LIMIT 1")
    LiveData<QuizResult> getLatestQuizResult();
}
