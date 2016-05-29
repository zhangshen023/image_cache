package com.swu.shen_pc.cachedemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;

import com.jakewharton.disklrucache.DiskLruCache;
import com.swu.shen_pc.cachedemo.utils.CacheUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener {

    private ArrayList<String> list;
    private ListView listView;
    private ImageView imageView;
    private ListViewAdapter adapter;
    private int start_index;
    private int end_index;
    private boolean isInit = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //首先创建存储缓存文件的地方
        File directory = CacheUtils.getDiskCacheDir(this, "bitmap");
        if (!directory.exists()) {
            //目录不存在的话
            directory.mkdirs();
        }
        //获取app的版本信息
        int appVersion = CacheUtils.getAppVersion(this);
        //此处的参数1表示每个key对应于一个缓存文件，1024*1024*100表示缓存大小为100M
        DiskLruCache diskCache = null;
        try {
            diskCache = DiskLruCache.open(directory, appVersion, 1, 1024 * 1024 * 100);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //创建用于传递给ListViewAdapter的数据
        list = new ArrayList<String>();
        int index = 0;
        for (int i = 0; i < 50; i++) {
            index = i % CacheUtils.images.length;
            list.add(CacheUtils.images[index]);
        }
        listView = (ListView) findViewById(R.id.listView);
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.item, null, false);
        imageView = (ImageView) view.findViewById(R.id.imageView);
        //创建Adapter对象
        adapter = new ListViewAdapter(this, list, diskCache, listView, imageView);
        listView.setOnScrollListener(this);
        listView.setAdapter(adapter);

    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
            //表示停止滑动，这时候就可以加载图片
            String url = "";
            String key = "";
            for (int i = start_index; i < end_index; i++) {
                url = list.get(i);
                key = CacheUtils.md5(url);
                adapter.loadImage(url, key, i);
            }
        } else {
            adapter.cancelTask();
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        start_index = firstVisibleItem;
        end_index = start_index + visibleItemCount;
        if (isInit == true && visibleItemCount > 0) {
            String url = "";
            String key = "";
            for (int i = start_index; i < end_index; i++) {
                url = list.get(i);
                key = CacheUtils.md5(url);
                adapter.loadImage(url, key, i);
            }
            isInit = false;
        }
    }
}
