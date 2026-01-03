package com.example.homeinventorymanager.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.homeinventorymanager.bean.ItemImage;

import java.util.List;

/**
 * 物品图片Dao接口（定义数据库操作）
 */
@Dao
public interface ItemImageDao {
    /**
     * 根据物品ID查询对应的所有图片路径
     * @param itemId 物品ID
     * @return 图片路径列表
     */
    // 核心修正：查询语句中使用数据库实际列名 `image_path`，而非 `imagePath`
    @Query("SELECT image_path FROM item_image WHERE item_id = :itemId")
    List<String> queryImagePathsByItemId(long itemId);

    /**
     * 插入单张物品图片关联记录
     * @param itemImage 物品图片对象
     */
    @Insert
    void insertItemImage(ItemImage itemImage);

    /**
     * 批量插入物品图片关联记录
     * @param itemImageList 物品图片对象列表
     */
    @Insert
    void insertItemImageList(List<ItemImage> itemImageList);

    /**
     * 根据物品ID删除所有关联的图片记录
     * @param itemId 物品ID
     */
    @Query("DELETE FROM item_image WHERE item_id = :itemId")
    void deleteItemImageByItemId(int itemId);
}