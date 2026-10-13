
package com.example.englishvocabulary.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "topics")
public class Topic {

    @PrimaryKey(autoGenerate = true)
    private int topicId;

    @ColumnInfo(name = "topicName")
    private String topicName;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "createdAt")
    private long createdAt;

    public Topic(String topicName, String description, long createdAt) {
        this.topicName = topicName;
        this.description = description;
        this.createdAt = createdAt;
    }

    public int getTopicId() {
        return topicId;
    }

    public void setTopicId(int topicId) {
        this.topicId = topicId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
