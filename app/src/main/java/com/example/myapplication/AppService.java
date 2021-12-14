package com.example.myapplication;


import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Tag;
import retrofitj.annotation.RetrofitJ;
import retrofitj.annotation.JsonApplication;
import retrofitj.annotation.JsonField;

/**
 * @author wangaihu
 * @date 2021/11/24
 */
@RetrofitJ
public interface AppService {

    @GET("/user")
    Call<Bean> getUserInfo(@Query("id") String id, @Query("from") long time);


    @POST("/user")
    @JsonApplication
    Call<Bean> getUserInfo2(
            @JsonField("id") String id,
            @JsonField("from") long time,
            @Tag Object tag
    );
}
