package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofitj.http.RetrofitJ;
import retrofitj.utils.Utils;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        findViewById(R.id.tv_hello).setOnClickListener(v -> {

            AppService appService = RetrofitJ.create(mRetrofit, AppService.class);
            appService.getUserInfo2("12345", System.currentTimeMillis(), null).enqueue(new Callback<Bean>() {
                @Override
                public void onResponse(@NonNull Call<Bean> call, @NonNull retrofit2.Response<Bean> response) {
                    Log.d(TAG, "onResponse: " + response.body());
                }

                @Override
                public void onFailure(@NonNull Call<Bean> call, @NonNull Throwable t) {

                }
            });
        });
    }



    private final Interceptor mInterceptor = new Interceptor() {

        @Override
        @NonNull
        public Response intercept(@NonNull Chain chain) throws IOException {
            final Request request = chain.request();
            Log.i(TAG, "intercept: " + request);

            final Bean bean = new Bean();
            bean.errno = 0;
            bean.errMsg = "OK";
            bean.data = "Hello, world !";

            return new Response.Builder()
                    .code(200)
                    .request(request)
                    .message("ok")
                    .protocol(Protocol.HTTP_1_1)
                    .body(ResponseBody.create(Utils.JSON_MEDIA_TYPE, Utils.gson.toJson(bean)))
                    .build();
        }
    };


    private final HttpLoggingInterceptor mLoggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
        @Override
        public void log(@NonNull String message) {
            Log.d(TAG, message);
        }
    });

    private final OkHttpClient mClient = new OkHttpClient.Builder()
            .addInterceptor(mLoggingInterceptor)
            .addInterceptor(mInterceptor)
            .build();

    private final Retrofit mRetrofit = new Retrofit.Builder()
            .baseUrl("https://127.0.0.1")
            .client(mClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    interface Builder {
        Object build(Retrofit retrofit);
    }

}