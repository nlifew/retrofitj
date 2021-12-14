package retrofitj.compiler.wrapper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import java.io.Closeable;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author wangaihu
 * @date 2021/11/25
 */
public class Utils {

    public static TypeName typeNameOf(String name) {
        switch (name) {
            case "void": return TypeName.VOID;
            case "byte": return TypeName.BYTE;
            case "boolean": return TypeName.BOOLEAN;
            case "char": return TypeName.CHAR;
            case "short": return TypeName.SHORT;
            case "int": return TypeName.INT;
            case "long": return TypeName.LONG;
            case "float": return TypeName.FLOAT;
            case "double": return TypeName.DOUBLE;
        }
        return ClassName.bestGuess(name);
    }

    public static Type parameterType(Class<?> raw, Type... inner) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return inner;
            }

            @Override
            public Type getRawType() {
                return raw;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }

    private static <T> T inner() {
        return null;
    }

    public static Type tType() {
        try {
            Method method = Utils.class.getDeclaredMethod("inner");
            return method.getGenericReturnType();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void safeClose(Closeable c) {
        try {
            c.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void safeClose(Writer writer) {
        if (writer == null) {
            return;
        }

        try {
            writer.flush();
            writer.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
