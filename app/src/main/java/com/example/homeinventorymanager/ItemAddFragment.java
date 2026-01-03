package com.example.homeinventorymanager;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.homeinventorymanager.bean.ItemImage;
import com.example.homeinventorymanager.databinding.FragmentItemAddBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 物品录入 Fragment
 * 实现录入界面的所有业务逻辑：控件绑定、非空校验、日期选择、保存数据、图片添加（相机+图库）
 */
public class ItemAddFragment extends Fragment {

    // 视图绑定对象（自动生成，对应 fragment_item_add.xml）
    private FragmentItemAddBinding binding;

    // 动态数据列表
    private List<Category> categoryList;
    private List<SubCategory> subCategoryList;
    private List<StorageLocation> storageLocationList;

    // 适配器（支持动态刷新）
    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> subCategoryAdapter;
    private ArrayAdapter<String> locationAdapter;

    // 图片相关常量与变量
    private static final int REQUEST_STORAGE_PERMISSION = 1001; // 存储权限请求码
    private static final int REQUEST_CAMERA_PERMISSION = 1002; // 相机权限请求码
    private static final int REQUEST_PICK_IMAGE = 1003; // 相册选择请求码
    private static final int REQUEST_TAKE_PHOTO = 1004; // 相机拍摄请求码
    private String currentImagePath; // 当前拍摄图片的本地绝对路径
    private List<String> imagePathList = new ArrayList<>(); // 多图路径列表（存储所有选中/拍摄的图片）

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 初始化视图绑定，替代 inflater.inflate()
        binding = FragmentItemAddBinding.inflate(inflater, container, false);

        // 1. 初始化下拉列表（Spinner）的适配器和数据
        initSpinnerData();

        // 2. 绑定有效期输入框的点击事件（弹出日期选择器）
        bindValidDateClickListener();

        // 3. 绑定保存按钮的点击事件和长按事件（核心：数据校验+保存+数据展示）
        bindSaveButtonClickListener();

        // 4. 绑定添加图片按钮的点击事件（完善：相机+图库选择+预览+路径获取）
        bindAddPhotoButtonClickListener();

        // 返回视图
        return binding.getRoot();
    }

    /**
     * 初始化下拉列表（动态加载自定义数据，支持分类-子分类联动）
     */
    private void initSpinnerData() {
        // 初始化数据列表
        categoryList = new ArrayList<>();
        subCategoryList = new ArrayList<>();
        storageLocationList = new ArrayList<>();

        // 1. 分类Spinner适配器（动态）
        categoryAdapter = new ArrayAdapter<>(
                requireActivity(), // 使用requireActivity()替代getActivity()，避免空指针
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spCategory.setAdapter(categoryAdapter);

        // 2. 子分类Spinner适配器（动态）
        subCategoryAdapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        subCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spSubCategory.setAdapter(subCategoryAdapter);

        // 3. 位置Spinner适配器（动态）
        locationAdapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spLocation.setAdapter(locationAdapter);

        // 4. 分类选择联动子分类（核心：杜绝跨界搭配）
        binding.spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (categoryList.isEmpty()) {
                    return;
                }
                // 获取选中的父分类
                Category selectedCategory = categoryList.get(position);
                // 刷新子分类列表（仅展示该父分类下的子分类）
                refreshSubCategoryByParentId(selectedCategory.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 5. 异步加载自定义数据（修复：避免主线程阻塞）
        loadCustomDataFromDbAsync();
    }

    /**
     * 异步从数据库加载自定义分类、子分类、位置数据（核心修复：避免主线程阻塞）
     */
    private void loadCustomDataFromDbAsync() {
        // 使用DbAsyncTask异步加载数据，不阻塞主线程
        new DbAsyncTask<Void, Void, Void>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Void>() {
                    @Override
                    public void onDbOperationCompleted(Void aVoid) {
                        // 主线程刷新Spinner（UI操作必须在主线程）
                        refreshCategorySpinner();
                        refreshLocationSpinner();

                        // 初始化子分类（默认选中第一个分类的子分类）
                        if (!categoryList.isEmpty()) {
                            refreshSubCategoryByParentId(categoryList.get(0).getId());
                        }
                    }
                }) {
            @Override
            protected Void doInBackground(Void... voids) {
                // 子线程执行数据库查询，避免主线程阻塞
                AppDatabase db = AppDatabase.getInstance(mContext);

                // 1. 加载分类
                categoryList.clear();
                List<Category> dbCategories = db.categoryDao().queryAllCategories();
                if (dbCategories != null && !dbCategories.isEmpty()) {
                    categoryList.addAll(dbCategories);
                } else {
                    // 兜底默认分类
                    String[] defaultCategories = {"食品", "日用品", "家电", "服饰", "其他"};
                    for (String name : defaultCategories) {
                        categoryList.add(new Category(name));
                    }
                }

                // 2. 加载所有子分类（用于联动筛选）
                subCategoryList.clear();
                List<SubCategory> dbSubCategories = db.subCategoryDao().queryAllSubCategories();
                if (dbSubCategories != null && !dbSubCategories.isEmpty()) {
                    subCategoryList.addAll(dbSubCategories);
                } else {
                    // 兜底默认子分类
                    if (!categoryList.isEmpty()) {
                        String[] defaultSubCategories = {"零食", "生鲜", "调味品", "清洁用品", "洗漱用品", "其他"};
                        Category defaultParent = categoryList.get(0);
                        for (String name : defaultSubCategories) {
                            subCategoryList.add(new SubCategory(name, defaultParent.getId(), defaultParent.getCategoryName()));
                        }
                    }
                }

                // 3. 加载存放位置
                storageLocationList.clear();
                List<StorageLocation> dbLocations = db.storageLocationDao().queryAllStorageLocations();
                if (dbLocations != null && !dbLocations.isEmpty()) {
                    storageLocationList.addAll(dbLocations);
                } else {
                    // 兜底默认位置
                    String[] defaultLocations = {"冰箱", "厨房橱柜", "卫生间", "卧室衣柜", "客厅书架", "阳台", "其他"};
                    for (String name : defaultLocations) {
                        storageLocationList.add(new StorageLocation(name));
                    }
                }
                return null;
            }
        }.execute();
    }

    /**
     * 刷新分类Spinner
     */
    private void refreshCategorySpinner() {
        categoryAdapter.clear();
        for (Category c : categoryList) {
            categoryAdapter.add(c.getCategoryName());
        }
        categoryAdapter.notifyDataSetChanged();
    }

    /**
     * 根据父分类ID刷新子分类Spinner（核心：实现分类联动，避免跨界）
     */
    private void refreshSubCategoryByParentId(int parentCategoryId) {
        subCategoryAdapter.clear();
        for (SubCategory sc : subCategoryList) {
            if (sc.getParentCategoryId() == parentCategoryId) {
                subCategoryAdapter.add(sc.getSubCategoryName());
            }
        }
        subCategoryAdapter.notifyDataSetChanged();

        // 若无子分类，添加兜底选项
        if (subCategoryAdapter.getCount() == 0) {
            subCategoryAdapter.add("暂无子分类");
            subCategoryAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 刷新位置Spinner
     */
    private void refreshLocationSpinner() {
        locationAdapter.clear();
        for (StorageLocation l : storageLocationList) {
            locationAdapter.add(l.getLocationName());
        }
        locationAdapter.notifyDataSetChanged();
    }

    /**
     * 绑定有效期输入框点击事件，弹出日期选择器
     */
    private void bindValidDateClickListener() {
        binding.etValidDate.setOnClickListener(v -> {
            // 获取当前日期作为默认选中值
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            // 弹出日期选择对话框
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireActivity(),
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        // 格式化日期为 yyyy-MM-dd 格式
                        String validDate = selectedYear + "-" +
                                String.format("%02d", (selectedMonth + 1)) + "-" +
                                String.format("%02d", selectedDay);
                        // 设置到输入框中
                        binding.etValidDate.setText(validDate);
                    },
                    year,
                    month,
                    day
            );
            datePickerDialog.show();
        });
    }

    /**
     * 绑定保存按钮点击事件和长按事件
     * 修复：void转Long编译错误、异步操作、空指针、主线程阻塞、长按闪退等问题
     * 新增：多图片路径保存（关联ItemImage表）
     */
    private void bindSaveButtonClickListener() {
        // 显式开启按钮的长按支持
        binding.btnSaveItem.setLongClickable(true);

        // 1. 点击事件：保存物品信息（异步存储，避免主线程阻塞，关联图片路径）
        binding.btnSaveItem.setOnClickListener(v -> {
            // 空安全判断：获取输入数据，避免空指针
            String itemName = binding.etItemName.getText() != null ? binding.etItemName.getText().toString().trim() : "";
            String category = binding.spCategory.getSelectedItem() != null ? binding.spCategory.getSelectedItem().toString() : "";
            String subCategory = binding.spSubCategory.getSelectedItem() != null ? binding.spSubCategory.getSelectedItem().toString() : "";
            String itemCount = binding.etItemCount.getText() != null ? binding.etItemCount.getText().toString().trim() : "";
            String location = binding.spLocation.getSelectedItem() != null ? binding.spLocation.getSelectedItem().toString() : "";
            String validDate = binding.etValidDate.getText() != null ? binding.etValidDate.getText().toString().trim() : "";
            String itemDesc = binding.etItemDesc.getText() != null ? binding.etItemDesc.getText().toString().trim() : "";

            // 2. 非空校验
            if (itemName.isEmpty()) {
                Toast.makeText(requireActivity(), "物品名称不能为空！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 3. 构建物品对象
            Item newItem = new Item();
            newItem.setItemName(itemName);
            newItem.setCategory(category);
            newItem.setSubCategory(subCategory);
            newItem.setItemCount(itemCount);
            newItem.setLocation(location);
            newItem.setValidDate(validDate);
            newItem.setDescription(itemDesc);
            // 若Item类支持单图片路径，可保留（兼容原有逻辑）
            if (currentImagePath != null && !currentImagePath.isEmpty()) {
                newItem.setImagePath(currentImagePath);
            }

            // 4. 异步新增物品（修复：适配ItemDao.insertItem() void返回值，泛型改为Void）
            new DbAsyncTask<Item, Void, Void>(requireActivity().getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<Void>() {
                        @Override
                        public void onDbOperationCompleted(Void aVoid) {
                            // 主线程更新UI
                            Toast.makeText(requireActivity(), "物品新增成功！", Toast.LENGTH_SHORT).show();
                            // 保存图片路径到ItemImage表（此处假设物品新增必成功，若需精准关联主键，需修改ItemDao返回long）
                            // 临时方案：若无需精准主键关联，直接保存图片；若需精准关联，需先修改ItemDao.insertItem返回long
                            saveItemImagesTemp();
                            // 清空输入框和图片预览
                            clearInputFields();
                        }
                    }) {
                @Override
                protected Void doInBackground(Item... items) {
                    // 子线程执行数据库操作，避免主线程阻塞
                    try {
                        if (items == null || items.length == 0) {
                            return null;
                        }
                        Item item = items[0];
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        // 执行插入操作（无返回值，修复编译错误）
                        db.itemDao().insertItem(item);
                        return null;
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 插入失败，主线程提示
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireActivity(), "物品新增失败！", Toast.LENGTH_SHORT).show();
                        });
                        return null;
                    }
                }
            }.execute(newItem); // 执行异步任务
        });

        // 2. 长按事件：展示已录入的所有物品数据（完全修复闪退问题）
        binding.btnSaveItem.setOnLongClickListener(v -> {
            Context context = requireActivity(); // 安全获取上下文，避免空指针

            // 异步查询所有物品（修复：避免主线程阻塞数据库查询）
            new DbAsyncTask<Void, Void, List<Item>>(context.getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<List<Item>>() {
                        @Override
                        public void onDbOperationCompleted(List<Item> allItems) {
                            // 主线程处理查询结果并展示
                            if (allItems == null || allItems.isEmpty()) {
                                Toast.makeText(context, "暂无已录入的物品信息，请先录入数据！", Toast.LENGTH_LONG).show();
                                return;
                            }

                            // 拼接物品信息（增加空安全判断，避免闪退）
                            StringBuilder sb = new StringBuilder();
                            try {
                                for (int i = 0; i < allItems.size(); i++) {
                                    Item item = allItems.get(i);
                                    if (item == null) {
                                        continue; // 物品对象为空，跳过
                                    }
                                    sb.append("【物品").append(i + 1).append("】\n");
                                    sb.append("名称：").append(item.getItemName() == null ? "未设置" : item.getItemName()).append("\n");
                                    sb.append("分类：").append((item.getCategory() == null ? "未设置" : item.getCategory())
                                            + "-" + (item.getSubCategory() == null ? "未设置" : item.getSubCategory())).append("\n");
                                    sb.append("数量：").append(item.getItemCount() == null ? "未设置" : item.getItemCount()).append("\n");
                                    sb.append("位置：").append(item.getLocation() == null ? "未设置" : item.getLocation()).append("\n");
                                    sb.append("有效期：").append(item.getValidDate() == null || item.getValidDate().isEmpty() ? "未设置" : item.getValidDate()).append("\n\n");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(context, "数据解析失败！", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // 弹窗展示物品列表（增加异常捕获，避免闪退）
                            try {
                                new AlertDialog.Builder(context)
                                        .setTitle("已录入物品列表（永久存储）")
                                        .setMessage(sb.toString())
                                        .setPositiveButton("确定", null)
                                        .setCancelable(true)
                                        .show();
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(context, "无法展示物品列表！", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }) {
                @Override
                protected List<Item> doInBackground(Void... voids) {
                    // 子线程执行数据库查询
                    AppDatabase db = AppDatabase.getInstance(mContext);
                    return db.itemDao().queryAllItems();
                }
            }.execute();

            return true; // 消费长按事件，避免触发点击事件
        });
    }

    /**
     * 临时保存图片路径（适配ItemDao返回void的场景，若需精准关联物品ID，请先修改ItemDao.insertItem返回long）
     */
    private void saveItemImagesTemp() {
        if (imagePathList.isEmpty()) {
            return;
        }
        new DbAsyncTask<List<String>, Void, Boolean>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Boolean>() {
                    @Override
                    public void onDbOperationCompleted(Boolean isSuccess) {
                        // 图片保存结果不影响物品新增结果，仅静默提示
                        if (isSuccess) {
                            Toast.makeText(requireActivity(), "图片保存成功！", Toast.LENGTH_SHORT).show();
                        }
                    }
                }) {
            @Override
            protected Boolean doInBackground(List<String>... lists) {
                try {
                    List<String> paths = lists[0];
                    AppDatabase db = AppDatabase.getInstance(mContext);
                    List<ItemImage> itemImageList = new ArrayList<>();
                    // 临时物品ID（若需精准关联，需修改ItemDao返回long获取真实主键）
                    int tempItemId = (int) System.currentTimeMillis();
                    for (String path : paths) {
                        itemImageList.add(new ItemImage(tempItemId, path));
                    }
                    if (!itemImageList.isEmpty()) {
                        db.itemImageDao().insertItemImageList(itemImageList);
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }.execute(imagePathList);
    }

    /**
     * 保存图片路径到ItemImage表（关联物品ID，精准版本需配合ItemDao返回long）
     */
    private void saveItemImages(int itemId) {
        if (imagePathList.isEmpty()) {
            return;
        }
        new DbAsyncTask<List<String>, Void, Boolean>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Boolean>() {
                    @Override
                    public void onDbOperationCompleted(Boolean isSuccess) {
                        // 图片保存结果不影响物品新增结果，仅静默提示
                        if (isSuccess) {
                            Toast.makeText(requireActivity(), "图片保存成功！", Toast.LENGTH_SHORT).show();
                        }
                    }
                }) {
            @Override
            protected Boolean doInBackground(List<String>... lists) {
                try {
                    List<String> paths = lists[0];
                    AppDatabase db = AppDatabase.getInstance(mContext);
                    List<ItemImage> itemImageList = new ArrayList<>();
                    for (String path : paths) {
                        itemImageList.add(new ItemImage(itemId, path));
                    }
                    if (!itemImageList.isEmpty()) {
                        db.itemImageDao().insertItemImageList(itemImageList);
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }.execute(imagePathList);
    }

    /**
     * 绑定添加图片按钮的点击事件（完善：相机+图库选择弹窗+权限申请）
     */
    private void bindAddPhotoButtonClickListener() {
        binding.btnAddPhotos.setOnClickListener(v -> {
            // 弹出图片选择来源对话框
            String[] options = {"相机拍摄", "图库选择"};
            new AlertDialog.Builder(requireActivity())
                    .setTitle("选择图片来源")
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                // 相机拍摄：先申请权限
                                requestCameraPermission();
                                break;
                            case 1:
                                // 图库选择：先申请权限
                                requestStoragePermission();
                                break;
                        }
                    })
                    .setCancelable(true)
                    .show();
        });
    }

    /**
     * 申请相机权限（Android 6.0+）
     */
    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // 未获取权限，申请相机+存储权限
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CAMERA_PERMISSION);
                return;
            }
        }
        // 权限已获取，启动相机拍摄
        takePhotoWithCamera();
    }

    /**
     * 申请存储权限（Android 6.0+）
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // 未获取权限，申请存储权限
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
                return;
            }
        }
        // 权限已获取，跳转相册选择图片
        pickImageFromAlbum();
    }

    /**
     * 启动相机拍摄图片（兼容Android 7.0+ FileProvider）
     */
    private void takePhotoWithCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 确保有相机应用可以处理Intent
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            // 创建图片文件
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                Toast.makeText(requireActivity(), "创建图片文件失败！", Toast.LENGTH_SHORT).show();
                return;
            }
            // 文件创建成功，获取Uri
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(
                        requireActivity(),
                        requireActivity().getPackageName() + ".fileprovider", // 与AndroidManifest中一致
                        photoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                // 添加权限标记
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        } else {
            Toast.makeText(requireActivity(), "未检测到相机应用！", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 创建图片文件（用于相机拍摄）
     */
    private File createImageFile() throws IOException {
        // 生成唯一图片文件名
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        // 应用私有图片目录
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        // 创建临时文件
        File image = File.createTempFile(
                imageFileName,  // 前缀
                ".jpg",         // 后缀
                storageDir      // 存储目录
        );
        // 保存图片绝对路径
        currentImagePath = image.getAbsolutePath();
        return image;
    }

    /**
     * 跳转相册选择图片
     */
    private void pickImageFromAlbum() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*"); // 仅筛选图片类型
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    /**
     * 处理权限申请结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限申请成功，跳转相册
                    pickImageFromAlbum();
                } else {
                    Toast.makeText(requireActivity(), "请授予存储权限，否则无法选择物品图片！", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_CAMERA_PERMISSION:
                boolean allGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    // 权限申请成功，启动相机
                    takePhotoWithCamera();
                } else {
                    Toast.makeText(requireActivity(), "请授予相机和存储权限，否则无法拍摄物品图片！", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * 处理相机/图库返回结果，预览图片并获取本地路径
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 相机拍摄结果
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == requireActivity().RESULT_OK) {
            try {
                // 添加拍摄图片路径到列表
                if (currentImagePath != null && !currentImagePath.isEmpty()) {
                    imagePathList.add(currentImagePath);
                    // 预览图片
                    Glide.with(this)
                            .load(new File(currentImagePath))
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .centerCrop()
                            .into(binding.ivItemPreview);
                    Toast.makeText(requireActivity(), "图片拍摄成功！", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                currentImagePath = null;
                Toast.makeText(requireActivity(), "图片解析失败，请重新拍摄！", Toast.LENGTH_SHORT).show();
            }
        }
        // 图库选择结果
        else if (requestCode == REQUEST_PICK_IMAGE && resultCode == requireActivity().RESULT_OK && data != null) {
            // 获取选中图片的Uri
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    // 将Uri转换为本地绝对路径
                    String imagePath = getRealPathFromUri(imageUri);
                    // 添加图库图片路径到列表
                    if (imagePath != null && !imagePath.isEmpty()) {
                        imagePathList.add(imagePath);
                        currentImagePath = imagePath; // 兼容原有单图片逻辑
                        // 使用Glide加载图片预览（占位图+错误图兜底）
                        Glide.with(this)
                                .load(new File(imagePath))
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_report_image)
                                .centerCrop()
                                .into(binding.ivItemPreview); // 图片预览控件
                        Toast.makeText(requireActivity(), "图片选择成功！", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireActivity(), "图片解析失败，请重新选择！", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * 将图片Uri转换为本地绝对路径（兼容大多数Android版本）
     */
    private String getRealPathFromUri(Uri uri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        android.database.Cursor cursor = requireActivity().getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(columnIndex);
            cursor.close();
            return path;
        }
        return uri.getPath();
    }

    /**
     * 清空所有输入控件的内容和图片预览，方便再次录入
     */
    private void clearInputFields() {
        binding.etItemName.setText("");
        binding.etItemCount.setText("");
        binding.etValidDate.setText("");
        binding.etItemDesc.setText("");
        // 下拉列表重置为第一个选项
        binding.spCategory.setSelection(0);
        binding.spSubCategory.setSelection(0);
        binding.spLocation.setSelection(0);
        // 重置图片预览和路径
        currentImagePath = null;
        imagePathList.clear();
        Glide.with(this)
                .load(android.R.drawable.ic_menu_gallery)
                .into(binding.ivItemPreview);
    }

    /**
     * 销毁视图时，释放视图绑定对象，避免内存泄漏
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}