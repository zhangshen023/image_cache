package com.swu.shen_pc.cachedemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.jakewharton.disklrucache.DiskLruCache;
import com.swu.shen_pc.cachedemo.utils.CacheUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by shen-pc on 5/29/16.
 */
public class ListViewAdapter extends BaseAdapter{

    public List<String> list;
    public DiskLruCache diskCache;
    public LayoutInflater inflater;
    public ListView listView;
    public Set<ImageAsyncTask> tasks;
    public int reqWidth;
    public int reqHeight;

    public ListViewAdapter() {
    }

    public ListViewAdapter(Context context, List<String> list, DiskLruCache diskCache, ListView listView, ImageView imageView) {
        this.list = list;
        this.diskCache = diskCache;
        this.inflater = LayoutInflater.from(context);
        this.listView = listView;
        tasks = new HashSet<ImageAsyncTask>();
        //获得ImageView的宽和高
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        this.reqWidth = params.width;
        this.reqHeight = params.height;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public String getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        ViewHolder holder = null;
        if(convertView == null)
        {
            view = inflater.inflate(R.layout.item, null);
            holder = new ViewHolder();
            holder.imageView = (ImageView)view.findViewById(R.id.imageView);
            holder.textView = (TextView)view.findViewById(R.id.textView);
            view.setTag(holder);//为了复用holder
        }else
        {
            view = convertView;
            holder = (ViewHolder) view.getTag();
        }
        //为ImageView设置标志,防止乱序
        holder.imageView.setTag(position);
        holder.textView.setTag(position+"#");
        return view;
    }

    /**
     * 加载图片
     * @param url
     * @param key
     * @param index
     */
    public void loadImage(String url,String key,final int index)
    {
        //查看DiskLruCache缓存中是否存在对应key值得缓存文件，如果存在的话，则直接从缓存中取出图片即可，如果不存在的话，则需要从网络中加载，加载完成同时写到缓存中
        //读缓存是通过DiskLruCache的Snaphot来实现的
        final ImageView imageView;
        final TextView textView;
        DiskLruCache.Snapshot snapshot = null;
        FileInputStream in = null;
        Bitmap bitmap = null;
        try {
            snapshot = diskCache.get(key);
            if(snapshot != null)
            {
                imageView = (ImageView)listView.findViewWithTag(index);
                textView = (TextView)listView.findViewWithTag(index+"#");
                //非空表示缓存中存在该缓存文件
                //通过Snapshot直接从缓存中取出写入到内存的输入流，随后调用BitmapFactory工厂方法来将其转变成为Bitmap对象显示在ImageView上面，同时将TextView设置为是从缓存中读取的数据
                in = (FileInputStream) snapshot.getInputStream(0);//这里的0指的是key对应的第1个缓存文件，因为在创建DiskLruCache的时候，第三个参数我们会用来输入一个key对应几个缓存文件，之前我们创建的DiskLruCache的第三个参数输入的是1
                //对流中的图片进行压缩处理操作
                bitmap = decodeSampleBitmapFromStream(in, reqWidth, reqHeight);
                if(imageView != null)
                    imageView.setImageBitmap(bitmap);
                if(textView != null)
                    textView.setText("从缓存中获取的");
            }else
            {
                //否则的话需要开启线程，从网络中获取图片，获取成功后返回该图片，并且将其设置为ImageView显示的图片，同时将TextView的值设置成是从网络中获取的
                //这里我们使用的是AsyncTask，因为可以很方便的获取到我们要的图片，当然也可以通过Handler的方式来获取图片
                ImageAsyncTask task = new ImageAsyncTask(listView,diskCache,index);
                task.setOnImageLoadListener(new OnImageLoadListener() {

                    @Override
                    public void onSuccessLoad(Bitmap bitmap) {
                        System.out.println("已经使用的缓存大小:  "+((float)diskCache.size())/(1024*1024)+" M");
                        System.out.println("加载图片成功.......");
                    }

                    @Override
                    public void onFailureLoad() {
                        System.out.println("加载图片失败.......");
                    }
                });
                tasks.add(task);//将任务加入到线程池中
                task.execute(url);//执行加载图片的线程
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 暂停所有任务(为了防止在滑动的时候仍然有线程处于请求状态)
     */
    public void cancelTask()
    {
        if(tasks != null)
        {
            for(ImageAsyncTask task: tasks)
                task.cancel(false);//暂停任务
        }
    }

    /**
     * 对图片进行压缩处理
     * @param in
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static Bitmap decodeSampleBitmapFromStream(FileInputStream in,int reqWidth,int reqHeight)
    {
        //设置BitmapFactory.Options的inJustDecodeBounds属性为true表示禁止为bitmap分配内存
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        byte[] data = inputStreamToByteArray(in);
        //这次调用的目的是获取到原始图片的宽、高，但是这次操作是没有写内存操作的
        Bitmap beforeBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        //设置这次加载图片需要加载到内存中
        options.inJustDecodeBounds = false;
        Bitmap afterBitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        return afterBitmap;
    }

    /**
     * 计算出压缩比
     * @param options
     * @param reqWith
     * @param reqHeight
     * @return
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        //通过参数options来获取真实图片的宽、高
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;//初始值是没有压缩的
        if(width > reqWidth || height > reqHeight)
        {
            //计算出原始宽与现有宽，原始高与现有高的比率
            int widthRatio = Math.round((float)width/(float)reqWidth);
            int heightRatio = Math.round((float)height/(float)reqHeight);
            //选出两个比率中的较小值，这样的话能够保证图片显示完全
            inSampleSize = widthRatio < heightRatio ? widthRatio:heightRatio;
        }
        return inSampleSize;
    }

    /**
     * 将InputStream转换为Byte数组
     * @param in
     * @return
     */
    public static byte[] inputStreamToByteArray(InputStream in)
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        try {
            while((len = in.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            try {
                in.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return outputStream.toByteArray();
    }
    static class ViewHolder
    {
        ImageView imageView;
        TextView textView;
    }
    //这里为了能够获知ImageAsyncTask网络加载图片成功与否，我们定义了一个接口OnImageLoadListener，里面有两个方法onSuccessLoad
    //与onFailureLoad，并且通过setOnImageLoadListener来将其绑定到指定的ImageAsyncTask中
    class ImageAsyncTask extends AsyncTask<String, Void, Bitmap>
    {
        public OnImageLoadListener listener;
        public DiskLruCache diskCache;
        public int index;
        public ListView listView;

        public void setOnImageLoadListener(OnImageLoadListener listener)
        {
            this.listener = listener;
        }

        public ImageAsyncTask(ListView listView,DiskLruCache diskCache,int index)
        {
            this.listView = listView;
            this.diskCache = diskCache;
            this.index = index;
        }
        @Override
        protected Bitmap doInBackground(String... params) {
            String url = params[0];
            String key = CacheUtils.md5(url);
            DiskLruCache.Editor editor;
            DiskLruCache.Snapshot snapshot;
            OutputStream out;
            FileInputStream in;
            Bitmap bitmap = null;
            try {
                editor = diskCache.edit(key);
                out = editor.newOutputStream(0);
                if(CacheUtils.downloadToStream(url, out))
                {
                    //写入缓存
                    editor.commit();
                }else
                {
                    editor.abort();
                }
                diskCache.flush();//刷新到缓存中
                //从缓存中将图片转换成Bitmap
                snapshot = diskCache.get(key);
                if(snapshot != null)
                {
                    in = (FileInputStream) snapshot.getInputStream(0);
                    bitmap = ListViewAdapter.decodeSampleBitmapFromStream(in, reqWidth, reqHeight);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if(result != null)
            {
                listener.onSuccessLoad(result);
                ImageView imageView = (ImageView) listView.findViewWithTag(index);
                TextView textView = (TextView)listView.findViewWithTag(index+"#");
                if(imageView != null)
                    imageView.setImageBitmap(result);
                if(textView != null)
                    textView.setText("从网络获取的");
            }
            else
                listener.onFailureLoad();
            tasks.remove(this);//加载结束移除任务(这点要特别注意，加载结束一定要记得移出任务)
        }
    }
}

