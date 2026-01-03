package com.example.homeinventorymanager;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemDao {
    // 原有方法全部保留，无需修改（Insert/Update会自动适配新增的imagePath字段）
    @Insert
    long insertItem(Item item);

    @Query("SELECT * FROM item ORDER BY id DESC")
    List<Item> queryAllItems();

    @Query("SELECT * FROM item WHERE " +
            "(:nameKey IS '' OR itemName LIKE '%' || :nameKey || '%') " +
            "AND (:category IS '全部' OR category = :category) " +
            "AND (:location IS '全部' OR location = :location) " +
            "ORDER BY id DESC")
    List<Item> queryItemsByCondition(String nameKey, String category, String location);

    @Query("DELETE FROM item")
    void deleteAllItems();

    @Delete
    void deleteItem(Item item);

    @Update
    void updateItem(Item item);

    @Query("SELECT * FROM item WHERE id = :itemId")
    Item queryItemById(int itemId);
}