
package com.example.englishvocabulary.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.OnConflictStrategy;

import com.example.englishvocabulary.data.entity.Topic;

import java.util.List;

@Dao
public interface TopicDao {

    // Lấy danh sách tất cả chủ đề
    @Query("SELECT * FROM topics ORDER BY topicName ASC")
    LiveData<List<Topic>> getAllTopics();

    // Lấy chủ đề theo ID
    @Query("SELECT * FROM topics WHERE topicId = :topicId LIMIT 1")
    LiveData<Topic> getTopicById(int topicId);

    // Tìm kiếm chủ đề theo tên
    @Query("SELECT * FROM topics " +
            "WHERE topicName LIKE '%' || :keyword || '%' COLLATE NOCASE " +
            "ORDER BY topicName ASC")
    LiveData<List<Topic>> searchTopics(String keyword);

    // Thêm chủ đề
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insertTopic(Topic topic);

    // Cập nhật chủ đề
    @Update
    int updateTopic(Topic topic);

    // Xóa chủ đề
    // Vocabulary liên quan sẽ được giữ lại và topicId chuyển thành NULL
    @Delete
    int deleteTopic(Topic topic);

    // Đếm số chủ đề
    @Query("SELECT COUNT(*) FROM topics")
    int countTopics();
}
