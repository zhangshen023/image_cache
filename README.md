# image_cache
对listview的图片用disklrucache进行缓存

有sdcard的时候就缓存到sdcard中，没有就缓存到内存之中。

缓存步骤：如果本地缓存中有相应的图片缓存，那么就从本地缓存中获取到，否者就去网络下载，下载成功后加入缓存之中。
