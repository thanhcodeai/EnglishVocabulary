
package com.example.englishvocabulary.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.OnConflictStrategy;

import com.example.englishvocabulary.data.entity.Vocabulary;

import java.util.List;

@Dao
public interface VocabularyDao {

    // Lấy toàn bộ từ vựng
    @Query("SELECT * FROM vocabularies ORDER BY vocabularyId DESC")
    LiveData<List<Vocabulary>> getAllVocabulary();

    // Lấy từ vựng theo ID
    @Query("SELECT * FROM vocabularies WHERE vocabularyId = :id LIMIT 1")
    LiveData<Vocabulary> getVocabularyById(int id);

    // Tìm kiếm theo từ tiếng Anh hoặc nghĩa tiếng Việt
    @Query("SELECT * FROM vocabularies " +
            "WHERE englishWord LIKE '%' || :keyword || '%' COLLATE NOCASE " +
            "OR vietnameseMeaning LIKE '%' || :keyword || '%' COLLATE NOCASE " +
            "ORDER BY englishWord ASC")
    LiveData<List<Vocabulary>> searchVocabulary(String keyword);

    // Lấy danh sách từ yêu thích
    @Query("SELECT * FROM vocabularies WHERE isFavorite = 1 ORDER BY englishWord ASC")
    LiveData<List<Vocabulary>> getFavoriteVocabulary();

    // Lấy từ vựng thuộc một chủ đề
    @Query("SELECT * FROM vocabularies WHERE topicId = :topicId ORDER BY englishWord ASC")
    LiveData<List<Vocabulary>> getVocabularyByTopic(int topicId);

    // Kiểm tra từ tiếng Anh đã tồn tại, không phân biệt hoa thường
    @Query("SELECT COUNT(*) FROM vocabularies " +
            "WHERE englishWord = :englishWord COLLATE NOCASE")
    int countByEnglishWord(String englishWord);

    // Kiểm tra trùng khi cập nhật, loại trừ chính bản ghi đang sửa
    @Query("SELECT COUNT(*) FROM vocabularies " +
            "WHERE englishWord = :englishWord COLLATE NOCASE " +
            "AND vocabularyId != :excludeId")
    int countDuplicateWord(String englishWord, int excludeId);

    // Thêm từ vựng
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertVocabulary(Vocabulary vocabulary);

    // Cập nhật từ vựng
    @Update
    int updateVocabulary(Vocabulary vocabulary);

    // Xóa từ vựng
    @Delete
    int deleteVocabulary(Vocabulary vocabulary);

    // Đếm tổng số từ vựng đủ dữ liệu để tạo bài trắc nghiệm
    @Query("SELECT COUNT(*) FROM vocabularies " +
            "WHERE englishWord IS NOT NULL AND TRIM(englishWord) != '' " +
            "AND vietnameseMeaning IS NOT NULL AND TRIM(vietnameseMeaning) != ''")
    int countVocabularyForQuiz();
}
