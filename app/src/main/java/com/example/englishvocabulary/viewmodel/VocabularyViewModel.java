
package com.example.englishvocabulary.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.englishvocabulary.data.database.AppDatabase;
import com.example.englishvocabulary.data.entity.Vocabulary;
import com.example.englishvocabulary.repository.VocabularyRepository;

import java.util.List;

public class VocabularyViewModel extends AndroidViewModel {

    private final VocabularyRepository repository;

    private final LiveData<List<Vocabulary>> allVocabulary;
    private final LiveData<List<Vocabulary>> favoriteVocabulary;

    private final MutableLiveData<String> searchKeyword =
            new MutableLiveData<>("");

    private final LiveData<List<Vocabulary>> searchResults;

    public VocabularyViewModel(@NonNull Application application) {
        super(application);

        AppDatabase database = AppDatabase.getInstance(application);

        repository = new VocabularyRepository(
                database.vocabularyDao()
        );

        allVocabulary = repository.getAllVocabulary();

        favoriteVocabulary = repository.getFavoriteVocabulary();

        searchResults = Transformations.switchMap(
                searchKeyword,
                keyword -> repository.searchVocabulary(keyword)
        );
    }

    public LiveData<List<Vocabulary>> getAllVocabulary() {
        return allVocabulary;
    }

    public LiveData<List<Vocabulary>> getFavoriteVocabulary() {
        return favoriteVocabulary;
    }

    public LiveData<List<Vocabulary>> getSearchResults() {
        return searchResults;
    }

    public void searchVocabulary(String keyword) {
        searchKeyword.setValue(keyword == null ? "" : keyword.trim());
    }

    public LiveData<Vocabulary> getVocabularyById(int id) {
        return repository.getVocabularyById(id);
    }

    public void insertVocabulary(
            Vocabulary vocabulary,
            VocabularyRepository.Callback callback
    ) {
        repository.insertVocabulary(vocabulary, callback);
    }

    public void updateVocabulary(
            Vocabulary vocabulary,
            VocabularyRepository.Callback callback
    ) {
        repository.updateVocabulary(vocabulary, callback);
    }

    public void deleteVocabulary(
            Vocabulary vocabulary,
            VocabularyRepository.Callback callback
    ) {
        repository.deleteVocabulary(vocabulary, callback);
    }
}
