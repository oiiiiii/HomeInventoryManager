package com.example.homeinventorymanager.bean;

import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

@Entity(tableName = "item_image") // 你的表名，可保持不变
public class ItemImage {
    // 自身主键（可选，若不需要可删除，不影响核心功能）
    @PrimaryKey(autoGenerate = true)
    private long id;

    // 物品关联ID：字段类型为 long
    @ColumnInfo(name = "item_id")
    private long itemId; // 已修改为 long

    // 图片路径
    @ColumnInfo(name = "image_path")
    private String imagePath;

    // 无参构造器（Room 必需，不可缺少）
    public ItemImage() {}

    // ===================== 核心修正：同步 getter/setter 类型为 long =====================
    /**
     * 获取物品关联ID
     * 修正：返回值类型从 int 改为 long，与字段类型一致
     */
    public long getItemId() { // 原：public int getItemId()
        return itemId;
    }

    /**
     * 设置物品关联ID
     * 修正：参数类型从 int 改为 long，与字段类型一致
     */
    public void setItemId(long itemId) { // 原：public void setItemId(int itemId)
        this.itemId = itemId;
    }

    // 其他字段的 getter/setter（保持不变，若自身主键id也有此警告，同步修改即可）
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public long getId() { // 若id字段是long，getter返回值也必须是long
        return id;
    }

    public void setId(long id) { // 若id字段是long，setter参数也必须是long
        this.id = id;
    }
}