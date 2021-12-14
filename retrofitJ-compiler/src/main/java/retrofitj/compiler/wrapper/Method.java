package retrofitj.compiler.wrapper;

import com.google.common.collect.Lists;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

/**
 * @author wangaihu
 * @date 2021/11/25
 *
 * @GET("/user")
 * Call<Bean> fetchUserInfo(@Query("id") String id, @Query("time") long time);
 */
public class Method {

    public Method(ExecutableElement element) {
        rawElement = element;
        simpleName = element.getSimpleName().toString();
        returnType = element.getReturnType().toString();

        annotations = new ArrayList<>(4);
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            annotations.add(new Annotation(am));
        }

        parameters = new ArrayList<>(8);
        for (VariableElement ve : element.getParameters()) {
            parameters.add(new Parameter(ve));
        }

        modifiers = new ArrayList<>(element.getModifiers());
    }


    public final ExecutableElement rawElement;

    public final String simpleName;

    public final String returnType;

    public final List<Annotation> annotations;

    public final List<Parameter> parameters;

    public final List<Modifier> modifiers;

//    private MethodSpec specCache;

    public MethodSpec toSpec() {
//        if (specCache != null) {
//            return specCache;
//        }
        final MethodSpec.Builder builder = MethodSpec
                .methodBuilder(simpleName)
                .returns(TypeName.get(rawElement.getReturnType()))
                .addModifiers(modifiers);
        for (Annotation ann : annotations) {
            builder.addAnnotation(ann.toSpec());
        }
        for (Parameter parameter : parameters) {
            builder.addParameter(parameter.toSpec());
        }
        return builder.build();
    }
}
