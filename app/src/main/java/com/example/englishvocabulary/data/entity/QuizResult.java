
package com.example.englishvocabulary.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "quiz_results")
public class QuizResult {

    @PrimaryKey(autoGenerate = true)
    private int resultId;

    @ColumnInfo(name = "completedAt")
    private long completedAt;

    @ColumnInfo(name = "totalQuestions")
    private int totalQuestions;

    @ColumnInfo(name = "correctAnswers")
    private int correctAnswers;

    @ColumnInfo(name = "scorePercent")
    private double scorePercent;

    public QuizResult(
            long completedAt,
            int totalQuestions,
            int correctAnswers,
            double scorePercent
    ) {
        this.completedAt = completedAt;
        this.totalQuestions = totalQuestions;
        this.correctAnswers = correctAnswers;
        this.scorePercent = scorePercent;
    }

    public int getResultId() {
        return resultId;
    }

    public void setResultId(int resultId) {
        this.resultId = resultId;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public void setCorrectAnswers(int correctAnswers) {
        this.correctAnswers = correctAnswers;
    }

    public double getScorePercent() {
        return scorePercent;
    }

    public void setScorePercent(double scorePercent) {
        this.scorePercent = scorePercent;
    }
}
