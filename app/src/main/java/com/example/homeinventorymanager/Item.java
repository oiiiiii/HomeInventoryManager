package com.example.homeinventorymanager;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "item") // 与你的表名保持一致
public class Item {
    @PrimaryKey(autoGenerate = true)
    private int id; // 自增主键
    private String itemName; // 物品名称
    private String category; // 分类
    private String subCategory; // 子分类
    private String location; // 存放位置
    private String validDate; // 有效期
    private String description; // 描述
    // ========== 新增：物品数量字段（解决 setItemCount 报错核心） ==========
    private String itemCount; // 物品剩余数量
    // ========== 新增：图片本地路径字段 ==========
    private String imagePath; // 存储图片的本地绝对路径，默认为null

    // 无参构造（Room 必需，保留不变，不添加@Ignore）
    public Item() {}

    // 带参构造（添加@Ignore注解，消除Room多构造方法警告，补充 itemCount 参数）
    @Ignore
    public Item(String itemName, String category, String subCategory, String location, String validDate, String description, String imagePath, String itemCount) {
        this.itemName = itemName;
        this.category = category;
        this.subCategory = subCategory;
        this.location = location;
        this.validDate = validDate;
        this.description = description;
        this.imagePath = imagePath;
        this.itemCount = itemCount; // 给数量字段赋值
    }

    // ========== 新增 imagePath 的 getter/setter 方法 ==========
    public String getImagePath() {
        return imagePath == null ? "" : imagePath; // 空值兜底
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    // ========== 新增 itemCount 的 getter/setter 方法（解决报错核心） ==========
    public String getItemCount() {
        return itemCount == null ? "" : itemCount; // 空值兜底，避免空指针
    }

    public void setItemCount(String itemCount) {
        this.itemCount = itemCount; // 对应 ItemAddFragment 中的调用方法
    }

    // 你的原有字段 getter/setter 方法（保留不变，完整保留）
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getItemName() {
        return itemName == null ? "" : itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getCategory() {
        return category == null ? "" : category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory == null ? "" : subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public String getLocation() {
        return location == null ? "" : location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getValidDate() {
        return validDate == null ? "" : validDate;
    }

    public void setValidDate(String validDate) {
        this.validDate = validDate;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}