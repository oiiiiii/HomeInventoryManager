package com.example.homeinventorymanager.bean;

import androidx.room.Entity;
import androidx.room.Ignore; // 新增：导入Ignore注解
import androidx.room.PrimaryKey;

/**
 * 物品图片关联实体类（数据库表：item_image）
 * 存储物品ID与图片路径的一对一/一对多关联关系
 */
@Entity(tableName = "item_image") // 数据库表名
public class ItemImage {
    // 主键，自增唯一ID
    @PrimaryKey(autoGenerate = true)
    private int imageId;

    // 关联的物品ID（与Item表的itemId对应）
    private int itemId;

    // 图片本地存储路径
    private String imagePath;

    // 无参构造函数（Room必需，默认使用该构造函数，无需添加注解）
    public ItemImage() {
    }

    // 新增：添加@Ignore注解，告知Room忽略该有参构造函数，消除警告
    @Ignore
    // 有参构造函数（仅用于业务代码快速创建对象，不参与Room数据库操作）
    public ItemImage(int itemId, String imagePath) {
        this.itemId = itemId;
        this.imagePath = imagePath;
    }

    // Getter和Setter方法（Room必需，用于数据库读写）
    public int getImageId() {
        return imageId;
    }

    public void setImageId(int imageId) {
        this.imageId = imageId;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}