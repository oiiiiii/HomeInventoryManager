package com.example.homeinventorymanager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.homeinventorymanager.databinding.FragmentItemQueryBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品查询 Fragment（已修复：异步操作、空指针、主线程阻塞、适配隐患）
 * 支持多条件筛选（名称+分类+位置）、异步查询/删除、编辑跳转、有效期提醒
 */
public class ItemQueryFragment extends Fragment {

    // 视图绑定对象
    private FragmentItemQueryBinding binding;
    // 动态数据列表（移除原有固定数组）
    private List<Category> categoryList;
    private List<StorageLocation> storageLocationList;
    // 动态适配器
    private ArrayAdapter<String> categoryAdapter;
    private ArrayAdapter<String> locationAdapter;
    // 查询结果列表
    private List<Item> queryResultList;
    // 列表适配器
    private ItemListAdapter itemListAdapter;

    // 兜底默认数据（无自定义数据时使用）
    private String[] defaultCategories = {"全部", "食品", "日用品", "家电", "服饰", "其他"};
    private String[] defaultLocations = {"全部", "冰箱", "厨房橱柜", "卫生间", "卧室衣柜", "客厅书架", "阳台", "其他"};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 初始化视图绑定
        binding = FragmentItemQueryBinding.inflate(inflater, container, false);

        // 1. 初始化动态数据列表
        initDynamicDataLists();

        // 2. 初始化筛选下拉列表（动态加载，异步优化）
        initSpinnerData();

        // 3. 初始化结果列表适配器
        initListViewAdapter();

        // 4. 绑定查询按钮点击事件
        bindQueryButtonClickListener();

        // 5. 绑定 ListView 长按事件（异步删除功能）
        bindListViewLongClickListener();

        // 6. 绑定 ListView 点击事件（编辑功能）
        bindListViewClickListener();

        return binding.getRoot();
    }

    /**
     * 初始化动态数据列表
     */
    private void initDynamicDataLists() {
        categoryList = new ArrayList<>();
        storageLocationList = new ArrayList<>();
        queryResultList = new ArrayList<>();
    }

    /**
     * 初始化分类、位置筛选的下拉列表（核心：异步加载自定义数据，避免主线程阻塞）
     */
    private void initSpinnerData() {
        // 1. 分类筛选 Spinner 适配器（动态）
        categoryAdapter = new ArrayAdapter<>(
                requireActivity(), // 安全获取上下文，替代 getActivity()
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spQueryCategory.setAdapter(categoryAdapter);

        // 2. 位置筛选 Spinner 适配器（动态）
        locationAdapter = new ArrayAdapter<>(
                requireActivity(),
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
        );
        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spQueryLocation.setAdapter(locationAdapter);

        // 3. 异步加载自定义筛选数据（核心修复：避免主线程阻塞）
        loadCustomFilterDataFromDbAsync();
    }

    /**
     * 异步加载自定义筛选数据（分类/位置）
     */
    private void loadCustomFilterDataFromDbAsync() {
        new DbAsyncTask<Void, Void, Void>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<Void>() {
                    @Override
                    public void onDbOperationCompleted(Void aVoid) {
                        // 主线程刷新Spinner适配器（UI操作必须在主线程）
                        refreshFilterSpinnerData();
                    }
                }) {
            @Override
            protected Void doInBackground(Void... voids) {
                // 子线程执行数据库查询，不阻塞主线程
                AppDatabase db = AppDatabase.getInstance(mContext);

                // 1. 加载分类数据
                categoryList.clear();
                List<Category> dbCategories = db.categoryDao().queryAllCategories();
                if (dbCategories != null && !dbCategories.isEmpty()) {
                    categoryList.addAll(dbCategories);
                }

                // 2. 加载位置数据
                storageLocationList.clear();
                List<StorageLocation> dbLocations = db.storageLocationDao().queryAllStorageLocations();
                if (dbLocations != null && !dbLocations.isEmpty()) {
                    storageLocationList.addAll(dbLocations);
                }
                return null;
            }
        }.execute();
    }

    /**
     * 刷新筛选下拉列表数据（主线程执行）
     */
    private void refreshFilterSpinnerData() {
        // 1. 刷新分类筛选列表（添加"全部"选项）
        categoryAdapter.clear();
        categoryAdapter.add("全部"); // 查询专用：全部分类
        if (!categoryList.isEmpty()) {
            for (Category c : categoryList) {
                categoryAdapter.add(c.getCategoryName());
            }
        } else {
            // 兜底默认分类
            for (String name : defaultCategories) {
                categoryAdapter.add(name);
            }
        }
        categoryAdapter.notifyDataSetChanged();

        // 2. 刷新位置筛选列表（添加"全部"选项）
        locationAdapter.clear();
        locationAdapter.add("全部"); // 查询专用：全部位置
        if (!storageLocationList.isEmpty()) {
            for (StorageLocation l : storageLocationList) {
                locationAdapter.add(l.getLocationName());
            }
        } else {
            // 兜底默认位置
            for (String name : defaultLocations) {
                locationAdapter.add(name);
            }
        }
        locationAdapter.notifyDataSetChanged();
    }

    /**
     * 初始化 ListView 适配器
     */
    private void initListViewAdapter() {
        // 自定义适配器
        itemListAdapter = new ItemListAdapter();
        binding.lvQueryResult.setAdapter(itemListAdapter);
    }

    /**
     * 绑定查询按钮点击事件，执行多条件异步查询
     */
    private void bindQueryButtonClickListener() {
        binding.btnQuery.setOnClickListener(v -> {
            // 空安全获取输入数据和选中值
            String nameKey = binding.etQueryName.getText() != null ? binding.etQueryName.getText().toString().trim() : "";
            String selectedCategory = binding.spQueryCategory.getSelectedItem() != null ? binding.spQueryCategory.getSelectedItem().toString() : "全部";
            String selectedLocation = binding.spQueryLocation.getSelectedItem() != null ? binding.spQueryLocation.getSelectedItem().toString() : "全部";

            // 封装查询参数
            String[] queryParams = {nameKey, selectedCategory, selectedLocation};

            // 异步执行多条件查询
            new DbAsyncTask<String[], Void, List<Item>>(requireActivity().getApplicationContext(),
                    new DbAsyncTask.OnDbOperationListener<List<Item>>() {
                        @Override
                        public void onDbOperationCompleted(List<Item> result) {
                            // 主线程更新查询结果列表和UI
                            queryResultList.clear();
                            if (result != null && !result.isEmpty()) {
                                queryResultList.addAll(result);
                            }
                            itemListAdapter.notifyDataSetChanged();
                            // 提示查询结果数量
                            Toast.makeText(requireActivity(), "查询到 " + queryResultList.size() + " 条物品数据", Toast.LENGTH_SHORT).show();
                        }
                    }) {
                @Override
                protected List<Item> doInBackground(String[]... params) {
                    // 子线程执行数据库多条件查询
                    try {
                        if (params == null || params.length == 0) {
                            return new ArrayList<>();
                        }
                        String[] args = params[0];
                        String nameKey = args[0];
                        String category = args[1];
                        String location = args[2];
                        AppDatabase db = AppDatabase.getInstance(mContext);
                        return db.itemDao().queryItemsByCondition(nameKey, category, location);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return new ArrayList<>();
                    }
                }
            }.execute(queryParams);
        });
    }

    /**
     * 绑定 ListView 长按事件，实现物品异步删除
     */
    private void bindListViewLongClickListener() {
        binding.lvQueryResult.setOnItemLongClickListener((parent, view, position, id) -> {
            // 空安全判断：避免位置越界或物品对象为空
            if (position < 0 || position >= queryResultList.size()) {
                Toast.makeText(requireActivity(), "获取物品数据失败！", Toast.LENGTH_SHORT).show();
                return true;
            }
            Item selectedItem = queryResultList.get(position);
            if (selectedItem == null) {
                Toast.makeText(requireActivity(), "获取物品数据失败！", Toast.LENGTH_SHORT).show();
                return true; // 消费长按事件
            }

            // 弹出删除确认框，防止误操作
            new AlertDialog.Builder(requireActivity())
                    .setTitle("删除确认")
                    .setMessage("确定要删除【" + selectedItem.getItemName() + "】吗？删除后无法恢复！")
                    .setPositiveButton("确定删除", (dialog, which) -> {
                        // 异步执行删除操作
                        new DbAsyncTask<Item, Void, Boolean>(requireActivity().getApplicationContext(),
                                new DbAsyncTask.OnDbOperationListener<Boolean>() {
                                    @Override
                                    public void onDbOperationCompleted(Boolean isSuccess) {
                                        // 主线程更新列表和提示结果
                                        if (isSuccess) {
                                            // 再次校验位置，避免列表已刷新导致的异常
                                            if (position < queryResultList.size()) {
                                                queryResultList.remove(position);
                                                itemListAdapter.notifyDataSetChanged();
                                            }
                                            Toast.makeText(requireActivity(), "已删除【" + selectedItem.getItemName() + "】", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(requireActivity(), "删除失败！", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }) {
                            @Override
                            protected Boolean doInBackground(Item... items) {
                                // 子线程执行数据库删除
                                try {
                                    if (items == null || items.length == 0) {
                                        return false;
                                    }
                                    Item item = items[0];
                                    AppDatabase db = AppDatabase.getInstance(mContext);
                                    db.itemDao().deleteItem(item);
                                    return true;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return false;
                                }
                            }
                        }.execute(selectedItem);
                    })
                    .setNegativeButton("取消", null)
                    .show();

            return true; // 消费长按事件，避免触发点击事件
        });
    }

    /**
     * 绑定 ListView 点击事件，跳转编辑页面
     */
    private void bindListViewClickListener() {
        binding.lvQueryResult.setOnItemClickListener((parent, view, position, id) -> {
            // 空安全判断
            if (position < 0 || position >= queryResultList.size()) {
                Toast.makeText(requireActivity(), "获取物品数据失败！", Toast.LENGTH_SHORT).show();
                return;
            }
            Item selectedItem = queryResultList.get(position);
            if (selectedItem == null) {
                Toast.makeText(requireActivity(), "获取物品数据失败！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 跳转编辑页面，传递物品ID
            Intent intent = new Intent(requireActivity(), ItemEditActivity.class);
            intent.putExtra("ITEM_ID", selectedItem.getId());
            startActivity(intent);
        });
    }

    /**
     * 自定义 ListView 适配器（修复：空安全、上下文兼容、性能优化）
     */
    private class ItemListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return queryResultList.size();
        }

        @Override
        public Object getItem(int position) {
            return position < queryResultList.size() ? queryResultList.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            // 复用视图，提升ListView性能
            if (convertView == null) {
                // 使用parent.getContext()，更安全的上下文获取方式
                convertView = LayoutInflater.from(parent.getContext()).inflate(
                        android.R.layout.simple_list_item_2, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.tvTitle = convertView.findViewById(android.R.id.text1);
                viewHolder.tvSubtitle = convertView.findViewById(android.R.id.text2);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // 空安全填充数据
            if (position < 0 || position >= queryResultList.size()) {
                return convertView;
            }
            Item item = queryResultList.get(position);
            if (item == null) {
                return convertView;
            }

            // 获取物品数据，空值兜底
            String itemName = item.getItemName() == null ? "" : item.getItemName();
            String validDate = item.getValidDate() == null ? "" : item.getValidDate();
            String category = item.getCategory() == null ? "" : item.getCategory();
            String location = item.getLocation() == null ? "" : item.getLocation();

            // 拼接副标题
            String subtitle = category + " - " + location +
                    " - 有效期：" + (validDate.isEmpty() ? "未设置" : validDate);

            // 设置文本内容
            viewHolder.tvTitle.setText(itemName);
            viewHolder.tvSubtitle.setText(subtitle);

            // 有效期状态颜色标记（兼容上下文，避免空指针）
            Context context = convertView.getContext();
            if (context == null) {
                return convertView;
            }

            // 系统颜色常量，无需资源获取，兼容性更好
            int defaultBlack = android.graphics.Color.BLACK;
            int warningRed = android.graphics.Color.RED;

            // 有效期判断（空值兜底，避免DateUtil报错）
            if (!validDate.isEmpty()) {
                if (DateUtil.isExpired(validDate)) {
                    // 已过期：红色+提示
                    viewHolder.tvTitle.setTextColor(warningRed);
                    viewHolder.tvSubtitle.setTextColor(warningRed);
                    viewHolder.tvSubtitle.setText(subtitle + " 【已过期】");
                } else if (DateUtil.isWillExpireIn7Days(validDate)) {
                    // 7天内即将过期：红色+提示
                    viewHolder.tvTitle.setTextColor(warningRed);
                    viewHolder.tvSubtitle.setTextColor(warningRed);
                    viewHolder.tvSubtitle.setText(subtitle + " 【7天内即将过期】");
                } else {
                    // 正常状态：默认黑色
                    viewHolder.tvTitle.setTextColor(defaultBlack);
                    viewHolder.tvSubtitle.setTextColor(defaultBlack);
                }
            } else {
                // 无有效期：默认黑色
                viewHolder.tvTitle.setTextColor(defaultBlack);
                viewHolder.tvSubtitle.setTextColor(defaultBlack);
            }

            return convertView;
        }

        /**
         * 视图持有者，避免重复findViewById
         */
        class ViewHolder {
            TextView tvTitle;
            TextView tvSubtitle;
        }
    }

    /**
     * 页面恢复时自动刷新查询结果
     */
    @Override
    public void onResume() {
        super.onResume();
        // 空安全判断，避免视图已销毁导致的异常
        if (binding != null && binding.btnQuery != null) {
            binding.btnQuery.performClick(); // 模拟点击查询按钮，自动刷新
        }
    }

    /**
     * 销毁视图时释放绑定对象，避免内存泄漏
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}