package com.example.homeinventorymanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品修改Fragment（复用编辑逻辑：列表展示 + 点击跳转ItemEditActivity）
 */
public class ItemModifyFragment extends Fragment {

    private ListView lvModifyList;
    private List<Item> itemList; // 所有物品列表
    private ItemModifyAdapter itemAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 加载布局
        View view = inflater.inflate(R.layout.fragment_item_modify, container, false);

        // 初始化控件
        lvModifyList = view.findViewById(R.id.lv_modify_list);

        // 初始化数据列表和适配器
        itemList = new ArrayList<>();
        itemAdapter = new ItemModifyAdapter();
        lvModifyList.setAdapter(itemAdapter);

        // 异步加载所有物品
        loadAllItemsAsync();

        // 绑定列表点击事件（跳转编辑页面）
        bindListViewClickListener();

        return view;
    }

    /**
     * 异步加载所有物品（避免主线程阻塞）
     */
    private void loadAllItemsAsync() {
        new DbAsyncTask<Void, Void, List<Item>>(requireActivity().getApplicationContext(),
                new DbAsyncTask.OnDbOperationListener<List<Item>>() {
                    @Override
                    public void onDbOperationCompleted(List<Item> result) {
                        // 刷新列表
                        itemList.clear();
                        if (result != null && !result.isEmpty()) {
                            itemList.addAll(result);
                        }
                        itemAdapter.notifyDataSetChanged();
                    }
                }) {
            @Override
            protected List<Item> doInBackground(Void... voids) {
                // 子线程查询所有物品
                AppDatabase db = AppDatabase.getInstance(mContext);
                return db.itemDao().queryAllItems(); // 确保ItemDao中有该查询方法
            }
        }.execute();
    }

    /**
     * 绑定列表点击事件，跳转ItemEditActivity
     */
    private void bindListViewClickListener() {
        lvModifyList.setOnItemClickListener((parent, view, position, id) -> {
            // 空安全判断
            if (position < 0 || position >= itemList.size()) {
                Toast.makeText(requireActivity(), "获取物品失败！", Toast.LENGTH_SHORT).show();
                return;
            }
            Item selectedItem = itemList.get(position);
            if (selectedItem == null) {
                Toast.makeText(requireActivity(), "获取物品失败！", Toast.LENGTH_SHORT).show();
                return;
            }

            // 跳转编辑页面，传递物品ID（和ItemQueryFragment一致）
            Intent intent = new Intent(requireActivity(), ItemEditActivity.class);
            intent.putExtra("ITEM_ID", selectedItem.getId());
            startActivity(intent);
        });
    }

    /**
     * 自定义适配器：展示物品名称
     */
    private class ItemModifyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return itemList.size();
        }

        @Override
        public Object getItem(int position) {
            return itemList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(
                        android.R.layout.simple_list_item_1, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.tvItemName = convertView.findViewById(android.R.id.text1);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // 填充数据
            Item item = itemList.get(position);
            String itemName = item.getItemName() == null ? "未命名物品" : item.getItemName();
            viewHolder.tvItemName.setText(itemName);

            return convertView;
        }

        class ViewHolder {
            TextView tvItemName;
        }
    }

    /**
     * 页面恢复时刷新列表（确保返回后数据最新）
     */
    @Override
    public void onResume() {
        super.onResume();
        loadAllItemsAsync();
    }
}