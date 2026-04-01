package com.safeg

import android.app.Application
import android.content.Context
import android.text.TextUtils
import com.androidnetworking.AndroidNetworking


class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this


        AndroidNetworking.initialize(context);

    }


    companion object {
        // TAG variable stores to application class name, when data is downloading,
        // then each request is has this same tag so that if user cancel download,
        // we can cancel all the pending request with this tag.
        val TAG = MyApp::class.java.simpleName

        /**
         * Singleton pattern is used in this class.
         *
         * @return MyApp Instance
         */
        // private instance for App
        @get:Synchronized
        var instance: MyApp? = null
            private set

        // image loader for showing images in grid view when show images are "yes"
        //private ImageLoader mImageLoader;
        val context: Context?
            get() = instance
    }
}
