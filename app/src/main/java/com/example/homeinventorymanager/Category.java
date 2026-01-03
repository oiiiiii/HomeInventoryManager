package com.example.homeinventorymanager;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 分类实体类（父分类，如食品、化妆品、日用品）
 */
@Entity(tableName = "category")
public class Category {
    @PrimaryKey(autoGenerate = true)
    private int id; // 主键ID

    private String categoryName; // 分类名称（如食品、化妆品）

    // 无参构造函数（Room 必需，Room 会使用该构造函数实例化对象）
    public Category() {
    }

    // 带参构造函数（手动创建对象时使用，用 @Ignore 让 Room 忽略该构造函数，消除警告）
    @Ignore
    public Category(String categoryName) {
        this.categoryName = categoryName;
    }

    // getter & setter
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}