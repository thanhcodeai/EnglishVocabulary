
package com.example.englishvocabulary.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.englishvocabulary.data.dao.VocabularyDao;
import com.example.englishvocabulary.data.entity.Vocabulary;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VocabularyRepository {

    private final VocabularyDao vocabularyDao;

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public VocabularyRepository(VocabularyDao vocabularyDao) {
        this.vocabularyDao = vocabularyDao;
    }

    // Lấy toàn bộ từ vựng
    public LiveData<List<Vocabulary>> getAllVocabulary() {
        return vocabularyDao.getAllVocabulary();
    }

    // Lấy từ vựng theo ID
    public LiveData<Vocabulary> getVocabularyById(int id) {
        return vocabularyDao.getVocabularyById(id);
    }

    // Tìm kiếm từ vựng
    public LiveData<List<Vocabulary>> searchVocabulary(String keyword) {
        return vocabularyDao.searchVocabulary(
                keyword == null ? "" : keyword.trim()
        );
    }

    // Lấy danh sách từ yêu thích
    public LiveData<List<Vocabulary>> getFavoriteVocabulary() {
        return vocabularyDao.getFavoriteVocabulary();
    }

    // Lấy từ vựng theo chủ đề
    public LiveData<List<Vocabulary>> getVocabularyByTopic(int topicId) {
        return vocabularyDao.getVocabularyByTopic(topicId);
    }

    // Thêm từ vựng
    public void insertVocabulary(
            Vocabulary vocabulary,
            Callback callback
    ) {
        executorService.execute(() -> {
            try {
                String error = validateVocabulary(vocabulary);

                if (error != null) {
                    notifyResult(callback, false, error);
                    return;
                }

                String englishWord = vocabulary.getEnglishWord().trim();

                vocabulary.setEnglishWord(englishWord);
                vocabulary.setVietnameseMeaning(
                        vocabulary.getVietnameseMeaning().trim()
                );

                if (vocabularyDao.countByEnglishWord(englishWord) > 0) {
                    notifyResult(
                            callback,
                            false,
                            "Từ tiếng Anh này đã tồn tại."
                    );
                    return;
                }

                vocabularyDao.insertVocabulary(vocabulary);

                notifyResult(
                        callback,
                        true,
                        "Thêm từ vựng thành công."
                );

            } catch (Exception e) {
                notifyResult(
                        callback,
                        false,
                        "Không thể thêm từ vựng."
                );
            }
        });
    }

    // Cập nhật từ vựng
    public void updateVocabulary(
            Vocabulary vocabulary,
            Callback callback
    ) {
        executorService.execute(() -> {
            try {
                String error = validateVocabulary(vocabulary);

                if (error != null) {
                    notifyResult(callback, false, error);
                    return;
                }

                String englishWord = vocabulary.getEnglishWord().trim();

                vocabulary.setEnglishWord(englishWord);
                vocabulary.setVietnameseMeaning(
                        vocabulary.getVietnameseMeaning().trim()
                );

                if (vocabularyDao.countDuplicateWord(
                        englishWord,
                        vocabulary.getVocabularyId()
                ) > 0) {
                    notifyResult(
                            callback,
                            false,
                            "Từ tiếng Anh này đã tồn tại."
                    );
                    return;
                }

                int rowsUpdated =
                        vocabularyDao.updateVocabulary(vocabulary);

                if (rowsUpdated > 0) {
                    notifyResult(
                            callback,
                            true,
                            "Cập nhật từ vựng thành công."
                    );
                } else {
                    notifyResult(
                            callback,
                            false,
                            "Không tìm thấy từ vựng cần cập nhật."
                    );
                }

            } catch (Exception e) {
                notifyResult(
                        callback,
                        false,
                        "Không thể cập nhật từ vựng."
                );
            }
        });
    }

    // Xóa từ vựng
    public void deleteVocabulary(
            Vocabulary vocabulary,
            Callback callback
    ) {
        executorService.execute(() -> {
            try {
                int rowsDeleted =
                        vocabularyDao.deleteVocabulary(vocabulary);

                if (rowsDeleted > 0) {
                    notifyResult(
                            callback,
                            true,
                            "Xóa từ vựng thành công."
                    );
                } else {
                    notifyResult(
                            callback,
                            false,
                            "Không tìm thấy từ vựng cần xóa."
                    );
                }

            } catch (Exception e) {
                notifyResult(
                        callback,
                        false,
                        "Không thể xóa từ vựng."
                );
            }
        });
    }

    // Kiểm tra dữ liệu bắt buộc
    private String validateVocabulary(Vocabulary vocabulary) {
        if (vocabulary == null) {
            return "Dữ liệu từ vựng không hợp lệ.";
        }

        if (vocabulary.getEnglishWord() == null
                || vocabulary.getEnglishWord().trim().isEmpty()) {
            return "Vui lòng nhập từ tiếng Anh.";
        }

        if (vocabulary.getVietnameseMeaning() == null
                || vocabulary.getVietnameseMeaning().trim().isEmpty()) {
            return "Vui lòng nhập nghĩa tiếng Việt.";
        }

        return null;
    }

    // Trả kết quả về luồng giao diện
    private void notifyResult(
            Callback callback,
            boolean success,
            String message
    ) {
        if (callback != null) {
            mainHandler.post(
                    () -> callback.onComplete(success, message)
            );
        }
    }
}
