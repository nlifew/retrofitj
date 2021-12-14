package retrofitj.utils;

import com.google.gson.Gson;

import java.util.Map;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * @author wangaihu
 * @date 2021/11/24
 */
public class Utils {

    public static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json;charset=utf-8");

    public static final Gson gson = new Gson();

    public static RequestBody toJsonRequestBody(Map<String, Object> params) {
        return RequestBody.create(JSON_MEDIA_TYPE, gson.toJson(params));
    }
}
