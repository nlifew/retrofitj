package retrofitj.http;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Retrofit;

/**
 * @author wangaihu
 * @date 2021/11/24
 */
public class RetrofitJ {

    @SuppressWarnings("unchecked")
    public static <T> T create(Retrofit retrofit, Class<T> clazz) {
        retrofitj.annotation.RetrofitJ retrofitJ = clazz.getAnnotation(retrofitj.annotation.RetrofitJ.class);
        if (retrofitJ == null) {
            return retrofit.create(clazz);
        }
        try {
            Class<?> service = Class.forName("retrofitj." + clazz.getName() + "_Service");
            Class<?> stub = Class.forName("retrofitj." + clazz.getName() + "_Service$Stub");
            Constructor<?> constructor = stub.getConstructor(service);
            return (T) constructor.newInstance(retrofit.create(service));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return retrofit.create(clazz);
    }
}
