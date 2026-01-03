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
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    // 视图绑定对象
    private FragmentItemAddBinding binding;

    // 动态数据列表
    private List<Category> categoryList;
    private List<SubCategory> subCategoryList;
    private List<StorageLocation> storageLocationList;

    // 适配器
    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> subCategoryAdapter;
    private ArrayAdapter<String> locationAdapter;

    // 图片相关常量与变量
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQUEST_CAMERA_PERMISSION = 1002;
    private static final int REQUEST_PICK_IMAGE = 1003;
    private static final int REQUEST_TAKE_PHOTO = 1004;
    private String currentImagePath;
    private List<String> imagePathList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 初始化视图绑定
        binding = FragmentItemAddBinding.inflate(inflater, container, false);

        // 初始化各项功能
        initSpinnerData();
        bindValidDateClickListener();
        bindSaveButtonClickListener();
        bindAddPhotoButtonClickListener();

        return binding.getRoot();
    }

    /**
     * 初始化下拉列表数据与适配器
     */
    private void initSpinnerData() {
        categoryList = new ArrayList<>();
        subCategoryList = new ArrayList<>();
        storageLocationList = new ArrayList<>();

        // 分类适配器
        categoryAdapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spCategory.setAdapter(categoryAdapter);

        // 子分类适配器
        subCategoryAdapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        subCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spSubCategory.setAdapter(subCategoryAdapter);

        // 位置适配器
        locationAdapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spLocation.setAdapter(locationAdapter);

        // 分类联动子分类
        binding.spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (categoryList.isEmpty()) {
                    return;
                }
                Category selectedCategory = categoryList.get(position);
                refreshSubCategoryByParentId(selectedCategory.getId());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 异步加载数据
        loadCustomDataFromDbAsync();
    }

    /**
     * 异步加载分类、子分类、位置数据
     */
    private void loadCustomDataFromDbAsync() {
        new DbAsyncTask<Void, Void, Void>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Void>() {
                    @Override
                    public void onDbOperationCompleted(Void aVoid) {
                        refreshCategorySpinner();
                        refreshLocationSpinner();

                        if (!categoryList.isEmpty()) {
                            refreshSubCategoryByParentId(categoryList.get(0).getId());
                        }
                    }
                }) {
            @Override
            protected Void doInBackground(Void... voids) {
                AppDatabase db = AppDatabase.getInstance(mContext);

                // 加载分类
                List<Category> dbCategories = db.categoryDao().queryAllCategories();
                if (dbCategories != null && !dbCategories.isEmpty()) {
                    categoryList.addAll(dbCategories);
                } else {
                    String[] defaultCategories = {"食品", "日用品", "家电", "服饰", "其他"};
                    for (String name : defaultCategories) {
                        categoryList.add(new Category(name));
                    }
                }

                // 加载子分类
                List<SubCategory> dbSubCategories = db.subCategoryDao().queryAllSubCategories();
                if (dbSubCategories != null && !dbSubCategories.isEmpty()) {
                    subCategoryList.addAll(dbSubCategories);
                } else {
                    if (!categoryList.isEmpty()) {
                        String[] defaultSubCategories = {"零食", "生鲜", "调味品", "清洁用品", "洗漱用品", "其他"};
                        Category defaultParent = categoryList.get(0);
                        for (String name : defaultSubCategories) {
                            subCategoryList.add(new SubCategory(name, defaultParent.getId(), defaultParent.getCategoryName()));
                        }
                    }
                }

                // 加载存放位置
                List<StorageLocation> dbLocations = db.storageLocationDao().queryAllStorageLocations();
                if (dbLocations != null && !dbLocations.isEmpty()) {
                    storageLocationList.addAll(dbLocations);
                } else {
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
     * 刷新分类下拉列表
     */
    private void refreshCategorySpinner() {
        categoryAdapter.clear();
        for (Category c : categoryList) {
            categoryAdapter.add(c.getCategoryName());
        }
        categoryAdapter.notifyDataSetChanged();
    }

    /**
     * 根据父分类ID刷新子分类
     */
    private void refreshSubCategoryByParentId(int parentCategoryId) {
        subCategoryAdapter.clear();
        for (SubCategory sc : subCategoryList) {
            if (sc.getParentCategoryId() == parentCategoryId) {
                subCategoryAdapter.add(sc.getSubCategoryName());
            }
        }
        if (subCategoryAdapter.getCount() == 0) {
            subCategoryAdapter.add("暂无子分类");
        }
        subCategoryAdapter.notifyDataSetChanged();
    }

    /**
     * 刷新存放位置下拉列表
     */
    private void refreshLocationSpinner() {
        locationAdapter.clear();
        for (StorageLocation l : storageLocationList) {
            locationAdapter.add(l.getLocationName());
        }
        locationAdapter.notifyDataSetChanged();
    }

    /**
     * 绑定有效期选择事件
     */
    private void bindValidDateClickListener() {
        binding.etValidDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireActivity(),
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String validDate = selectedYear + "-" +
                                String.format("%02d", (selectedMonth + 1)) + "-" +
                                String.format("%02d", selectedDay);
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
     * 绑定保存按钮事件
     */
    private void bindSaveButtonClickListener() {
        binding.btnSaveItem.setLongClickable(true);

        // 点击保存
        binding.btnSaveItem.setOnClickListener(v -> {
            String itemName = binding.etItemName.getText() != null ? binding.etItemName.getText().toString().trim() : "";
            String category = binding.spCategory.getSelectedItem() != null ? binding.spCategory.getSelectedItem().toString() : "";
            String subCategory = binding.spSubCategory.getSelectedItem() != null ? binding.spSubCategory.getSelectedItem().toString() : "";
            String itemCount = binding.etItemCount.getText() != null ? binding.etItemCount.getText().toString().trim() : "";
            String location = binding.spLocation.getSelectedItem() != null ? binding.spLocation.getSelectedItem().toString() : "";
            String validDate = binding.etValidDate.getText() != null ? binding.etValidDate.getText().toString().trim() : "";
            String itemDesc = binding.etItemDesc.getText() != null ? binding.etItemDesc.getText().toString().trim() : "";

            // 非空校验
            if (itemName.isEmpty()) {
                Toast.makeText(requireActivity(), "物品名称不能为空！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建物品对象
            Item newItem = new Item();
            newItem.setItemName(itemName);
            newItem.setCategory(category);
            newItem.setSubCategory(subCategory);
            newItem.setItemCount(itemCount);
            newItem.setLocation(location);
            newItem.setValidDate(validDate);
            newItem.setDescription(itemDesc);
            if (currentImagePath != null && !currentImagePath.isEmpty()) {
                newItem.setImagePath(currentImagePath);
            }

            // 异步保存物品
            new DbAsyncTask<Item, Void, Long>(requireActivity().getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<Long>() {
                        @Override
                        public void onDbOperationCompleted(Long itemId) {
                            if (itemId != null && itemId > 0) {
                                Toast.makeText(requireActivity(), "物品新增成功！", Toast.LENGTH_SHORT).show();
                                saveItemImages(itemId);
                                clearInputFields();
                            } else {
                                Toast.makeText(requireActivity(), "物品新增失败！", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }) {
                @Override
                protected Long doInBackground(Item... items) {
                    try {
                        if (items == null || items.length == 0) {
                            return -1L;
                        }
                        Item item = items[0];
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        long itemId = db.itemDao().insertItem(item);
                        item.setId(itemId); // 此时Item的id为long类型，无类型错误
                        return itemId;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return -1L;
                    }
                }
            }.execute(newItem);
        });

        // 长按查看所有物品
        binding.btnSaveItem.setOnLongClickListener(v -> {
            Context context = requireActivity();

            new DbAsyncTask<Void, Void, List<Item>>(context.getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<List<Item>>() {
                        @Override
                        public void onDbOperationCompleted(List<Item> allItems) {
                            if (allItems == null || allItems.isEmpty()) {
                                Toast.makeText(context, "暂无已录入物品！", Toast.LENGTH_LONG).show();
                                return;
                            }

                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < allItems.size(); i++) {
                                Item item = allItems.get(i);
                                if (item == null) continue;
                                sb.append("【物品").append(i + 1).append("】\n");
                                sb.append("名称：").append(item.getItemName() == null ? "未设置" : item.getItemName()).append("\n");
                                sb.append("分类：").append(item.getCategory() == null ? "未设置" : item.getCategory())
                                        .append("-").append(item.getSubCategory() == null ? "未设置" : item.getSubCategory()).append("\n");
                                sb.append("数量：").append(item.getItemCount() == null ? "未设置" : item.getItemCount()).append("\n");
                                sb.append("位置：").append(item.getLocation() == null ? "未设置" : item.getLocation()).append("\n");
                                sb.append("有效期：").append(item.getValidDate() == null || item.getValidDate().isEmpty() ? "未设置" : item.getValidDate()).append("\n\n");
                            }

                            new AlertDialog.Builder(context)
                                    .setTitle("已录入物品列表")
                                    .setMessage(sb.toString())
                                    .setPositiveButton("确定", null)
                                    .show();
                        }
                    }) {
                @Override
                protected List<Item> doInBackground(Void... voids) {
                    AppDatabase db = AppDatabase.getInstance(mContext);
                    return db.itemDao().queryAllItems();
                }
            }.execute();
            return true;
        });
    }

    /**
     * 绑定添加图片按钮事件
     */
    private void bindAddPhotoButtonClickListener() {
        // 与布局中btnAddPhoto完全匹配，无找不到符号错误
        binding.btnAddPhoto.setOnClickListener(v -> {
            new AlertDialog.Builder(requireActivity())
                    .setTitle("选择图片来源")
                    .setItems(new String[]{"相机拍摄", "从相册选择"}, (dialog, which) -> {
                        if (which == 0) {
                            checkCameraPermission();
                        } else {
                            checkStoragePermission();
                        }
                    }).show();
        });
    }

    /**
     * 检查相机权限
     */
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            dispatchTakePictureIntent();
        }
    }

    /**
     * 检查存储权限
     */
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        } else {
            pickImageFromGallery();
        }
    }

    /**
     * 启动相机拍摄
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(requireActivity(), "无法创建图片文件！", Toast.LENGTH_SHORT).show();
                return;
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(requireActivity(),
                        "com.example.homeinventorymanager.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    /**
     * 创建图片文件
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentImagePath = image.getAbsolutePath();
        return image;
    }

    /**
     * 从相册选择图片
     */
    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(requireActivity(), "需要相机权限才能拍摄！", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageFromGallery();
            } else {
                Toast.makeText(requireActivity(), "需要存储权限才能选择图片！", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 处理相机/相册返回结果
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == requireActivity().RESULT_OK) {
            if (requestCode == REQUEST_TAKE_PHOTO) {
                if (currentImagePath != null) {
                    imagePathList.add(currentImagePath);
                    addImageToPreview(currentImagePath);
                }
            } else if (requestCode == REQUEST_PICK_IMAGE && data != null) {
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    String imagePath = getPathFromUri(selectedImageUri);
                    if (imagePath != null) {
                        imagePathList.add(imagePath);
                        addImageToPreview(imagePath);
                    }
                }
            }
        }
    }

    /**
     * 添加图片到预览容器（与布局中llImageContainer完全匹配）
     */
    private void addImageToPreview(String imagePath) {
        ImageView imageView = new ImageView(requireActivity());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                300, 300);
        params.setMargins(8, 8, 8, 8);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        // 加载图片
        Glide.with(this)
                .load(new File(imagePath))
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imageView);

        // 无找不到符号错误，与布局id匹配
        binding.llImageContainer.addView(imageView);
    }

    /**
     * Uri转文件路径
     */
    private String getPathFromUri(Uri uri) {
        String path = null;
        if (uri.getScheme().equals("content")) {
            String[] projection = {MediaStore.Images.Media.DATA};
            try (android.database.Cursor cursor = requireActivity().getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    path = cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (uri.getScheme().equals("file")) {
            path = uri.getPath();
        }
        return path;
    }

    /**
     * 保存图片到数据库
     */
    private void saveItemImages(Long itemId) {
        if (itemId == null || itemId <= 0 || imagePathList.isEmpty()) return;

        new DbAsyncTask<Void, Void, Void>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Void>() {
                    @Override
                    public void onDbOperationCompleted(Void aVoid) {
                        Toast.makeText(requireActivity(), "图片保存成功！", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            protected Void doInBackground(Void... voids) {
                AppDatabase db = AppDatabase.getInstance(mContext);
                for (String path : imagePathList) {
                    ItemImage itemImage = new ItemImage();
                    itemImage.setItemId(itemId);
                    itemImage.setImagePath(path);
                    db.itemImageDao().insertItemImage(itemImage);
                }
                return null;
            }
        }.execute();
    }

    /**
     * 清空输入内容
     */
    private void clearInputFields() {
        binding.etItemName.setText("");
        binding.etItemCount.setText("");
        binding.etValidDate.setText("");
        binding.etItemDesc.setText("");
        binding.llImageContainer.removeAllViews();
        binding.imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        imagePathList.clear();
        currentImagePath = null;
        binding.spCategory.setSelection(0);
        binding.spSubCategory.setSelection(0);
        binding.spLocation.setSelection(0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // 释放资源
    }
}