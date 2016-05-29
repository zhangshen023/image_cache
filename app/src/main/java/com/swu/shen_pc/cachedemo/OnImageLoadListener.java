package com.swu.shen_pc.cachedemo;

import android.graphics.Bitmap;

/**
 * Created by shen-pc on 5/29/16.
 */
public interface OnImageLoadListener {
    public void onSuccessLoad(Bitmap bitmap);
    public void onFailureLoad();
}
