package retrofitj.compiler.wrapper;


import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

/**
 * @author wangaihu
 * @date 2021/11/25
 *
 * 对于注解 @retrofit2.http.GET(value = "/user", others = 123)，其包含以下属性:
 *
 * className: "retrofit2.http.GET"
 * valueMap: { ["value", "/user"], ["others", 123] }
 */
public class Annotation {


    public Annotation(AnnotationMirror mirror) {
        raw = mirror;
        className = mirror.getAnnotationType().toString();

        final Map<? extends ExecutableElement, ? extends AnnotationValue> map = mirror.getElementValues();
        valueMap = new HashMap<>(map.size(), 1F);

        for (Map.Entry<?, ?> e : map.entrySet()) {
            final ExecutableElement key = (ExecutableElement) e.getKey();
            final AnnotationValue value = (AnnotationValue) e.getValue();
            valueMap.put(key.getSimpleName().toString(), value.getValue());
        }
    }

    public final AnnotationMirror raw;

    public final String className;

    public final Map<String, Object> valueMap;


//    private AnnotationSpec specCache;

    public AnnotationSpec toSpec() {
//        if (specCache != null) {
//            return specCache;
//        }
        if (raw != null) {
            return AnnotationSpec.get(raw);
        }

        AnnotationSpec.Builder builder = AnnotationSpec
                .builder(ClassName.bestGuess(className));
        for (Map.Entry<String, Object> e : valueMap.entrySet()) {
            builder.addMember(e.getKey(), "$N", e.getValue());
        }
        return builder.build();
    }



    private Annotation(Builder builder) {
        raw = null;
        className = builder.className;
        valueMap = new HashMap<>(builder.map);
    }

    public static final class Builder {

        private final String className;
        private final Map<String, Object> map = new HashMap<>(4, 1F);

        public Builder(String className) {
            this.className = className;
        }

        public Builder addValue(String key, Object value) {
            map.put(key, value);
            return this;
        }

        public Builder removeValue(String key) {
            map.remove(key);
            return this;
        }

        public Annotation build() {
            return new Annotation(this);
        }
    }
}
