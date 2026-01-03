package com.example.homeinventorymanager;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 子分类实体类（关联父分类，如食品下的零食、化妆品下的口红）
 */
@Entity(tableName = "sub_category")
public class SubCategory {
    @PrimaryKey(autoGenerate = true)
    private int id; // 主键ID

    private String subCategoryName; // 子分类名称（如零食、口红）
    private int parentCategoryId; // 关联的父分类ID（核心：实现隶属关系）
    private String parentCategoryName; // 关联的父分类名称（便于展示，冗余字段）

    // 无参构造函数（Room 必需，Room 会使用该构造函数实例化对象）
    public SubCategory() {
    }

    // 带参构造函数（手动创建子分类对象时使用，添加 @Ignore 让 Room 忽略该构造，消除警告）
    @Ignore
    public SubCategory(String subCategoryName, int parentCategoryId, String parentCategoryName) {
        this.subCategoryName = subCategoryName;
        this.parentCategoryId = parentCategoryId;
        this.parentCategoryName = parentCategoryName;
    }

    // getter & setter 方法（保留不变）
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSubCategoryName() {
        return subCategoryName;
    }

    public void setSubCategoryName(String subCategoryName) {
        this.subCategoryName = subCategoryName;
    }

    public int getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(int parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public String getParentCategoryName() {
        return parentCategoryName;
    }

    public void setParentCategoryName(String parentCategoryName) {
        this.parentCategoryName = parentCategoryName;
    }
}