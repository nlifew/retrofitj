package retrofitj.compiler.wrapper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;

import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

/**
 * @author wangaihu
 * @date 2021/11/25
 */
public class Parameter {

    public Parameter(VariableElement ve) {
        element = ve;
        className = ve.asType().toString();
        name = ve.getSimpleName().toString();

        final List<? extends AnnotationMirror> list = ve.getAnnotationMirrors();
        annotations = new ArrayList<>(list.size());

        for (AnnotationMirror am : list) {
            annotations.add(new Annotation(am));
        }
//        modifiers = ve.getModifiers().toArray(new Modifier[0]);
    }


//    public final Modifier[] modifiers;

    public final VariableElement element;

    public final List<Annotation> annotations;

    public final String className;

    public final String name;

//    private ParameterSpec specCache;

    public ParameterSpec toSpec() {
//        if (specCache != null) {
//            return specCache;
//        }
        // 不要用这个，这个会丢失注解
//        if (element != null) {
//            return specCache = ParameterSpec.get(element);
//        }

        ParameterSpec.Builder builder = ParameterSpec
                .builder(Utils.typeNameOf(className), name);

        for (Annotation annotation : annotations) {
            builder.addAnnotation(annotation.toSpec());
        }
        return builder.build();
    }


    private Parameter(Builder builder) {
        element = null;
        className = builder.className;
        name = builder.name;
        annotations = new ArrayList<>(builder.annotations);
//        modifiers = builder.modifiers.toArray(new Modifier[0]);
    }

    public static final class Builder {

        private final String className;
        private final String name;

        private final List<Annotation> annotations = new ArrayList<>(0);

        public Builder(String className, String name) {
            this.className = className;
            this.name = name;
        }

//        public Builder modifiers(Modifier... modifiers) {
//            this.modifiers.addAll(Arrays.asList(modifiers));
//            return this;
//        }

        public Builder addAnnotation(Annotation annotation) {
            annotations.add(annotation);
            return this;
        }

        public Builder addAnnotation(Annotation.Builder annotation) {
            annotations.add(annotation.build());
            return this;
        }

        public Parameter build() {
            return new Parameter(this);
        }
    }
}
