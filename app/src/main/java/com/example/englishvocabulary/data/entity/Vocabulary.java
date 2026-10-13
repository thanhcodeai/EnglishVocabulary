
package com.example.englishvocabulary.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity(
        tableName = "vocabularies",
        foreignKeys = @ForeignKey(
                entity = Topic.class,
                parentColumns = "topicId",
                childColumns = "topicId",
                onDelete = ForeignKey.SET_NULL
        ),
        indices = {
                @Index("topicId")
        }
)
public class Vocabulary {

    @PrimaryKey(autoGenerate = true)
    private int vocabularyId;

    @NonNull
    @ColumnInfo(name = "englishWord")
    private String englishWord;

    @NonNull
    @ColumnInfo(name = "vietnameseMeaning")
    private String vietnameseMeaning;

    @ColumnInfo(name = "pronunciation")
    private String pronunciation;

    @ColumnInfo(name = "wordType")
    private String wordType;

    @ColumnInfo(name = "exampleSentence")
    private String exampleSentence;

    @ColumnInfo(name = "topicId")
    private Integer topicId;

    @ColumnInfo(name = "isFavorite", defaultValue = "0")
    private boolean isFavorite = false;

    @ColumnInfo(name = "createdAt")
    private long createdAt;

    @ColumnInfo(name = "updatedAt")
    private long updatedAt;

    public Vocabulary(
            String englishWord,
            String vietnameseMeaning,
            String pronunciation,
            String wordType,
            String exampleSentence,
            Integer topicId,
            boolean isFavorite,
            long createdAt,
            long updatedAt
    ) {
        this.englishWord = englishWord;
        this.vietnameseMeaning = vietnameseMeaning;
        this.pronunciation = pronunciation;
        this.wordType = wordType;
        this.exampleSentence = exampleSentence;
        this.topicId = topicId;
        this.isFavorite = isFavorite;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getVocabularyId() {
        return vocabularyId;
    }

    public void setVocabularyId(int vocabularyId) {
        this.vocabularyId = vocabularyId;
    }

    public String getEnglishWord() {
        return englishWord;
    }

    public void setEnglishWord(String englishWord) {
        this.englishWord = englishWord;
    }

    public String getVietnameseMeaning() {
        return vietnameseMeaning;
    }

    public void setVietnameseMeaning(String vietnameseMeaning) {
        this.vietnameseMeaning = vietnameseMeaning;
    }

    public String getPronunciation() {
        return pronunciation;
    }

    public void setPronunciation(String pronunciation) {
        this.pronunciation = pronunciation;
    }

    public String getWordType() {
        return wordType;
    }

    public void setWordType(String wordType) {
        this.wordType = wordType;
    }

    public String getExampleSentence() {
        return exampleSentence;
    }

    public void setExampleSentence(String exampleSentence) {
        this.exampleSentence = exampleSentence;
    }

    public Integer getTopicId() {
        return topicId;
    }

    public void setTopicId(Integer topicId) {
        this.topicId = topicId;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
