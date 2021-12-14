package retrofitj.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import okhttp3.Request;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofitj.annotation.JsonApplication;
import retrofitj.annotation.RetrofitJ;
import retrofitj.compiler.wrapper.Annotation;
import retrofitj.compiler.wrapper.Method;
import retrofitj.compiler.wrapper.Parameter;
import retrofitj.utils.Utils;

import static retrofitj.compiler.wrapper.Utils.parameterType;
import static retrofitj.compiler.wrapper.Utils.tType;
import static retrofitj.compiler.wrapper.Utils.safeClose;

@AutoService(Processor.class)
public class RetrofitJCompiler extends AbstractProcessor {


    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(RetrofitJ.class.getCanonicalName());
    }

    private Elements mElementUtils; // 操作元素的工具类
    private Filer mFiler;  // 用来创建文件
    private Messager mMessager; // 用来输出日志、错误或警告信息

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mElementUtils = processingEnv.getElementUtils();
        mFiler = processingEnv.getFiler();
        mMessager = processingEnv.getMessager();


    }



    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<TypeElement> elementList = new ArrayList<>();

        for (Element ele : roundEnv.getElementsAnnotatedWith(RetrofitJ.class)) {
            if (ele.getKind() != ElementKind.INTERFACE) {
                mMessager.printMessage(Diagnostic.Kind.WARNING, "@RetrofitJ must be attach to a Interface");
                continue;
            }
            TypeElement typeElement = (TypeElement) ele;
            elementList.add(typeElement);
        }
        handleInterfaceList(elementList);
        return true;
    }

    private void handleInterfaceList(List<TypeElement> elementList) {
        if (elementList.isEmpty()) {
            return;
        }


//        List<MethodSpec> methodSpecList = new ArrayList<>();

        // 针对每个类，生成一个代理类
        for (TypeElement ele : elementList) {
            handleInterface0(ele);
        }

        // 生成完之后，创建一个大管家
        genMapClass(elementList);
    }

    private static final class ReplacedMethod {
        Method originalMethod;
        List<Parameter> rawParameters = new ArrayList<>();
        List<Parameter> packedParameters = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
    }

    private void handleInterface0(TypeElement typeElement) {
        final Map<Method, ReplacedMethod> replacedMethodInfo = new LinkedHashMap<>(4, 1F);

        for (Element e : typeElement.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, "unsupported element [" + e + "] found in [" + typeElement + "]," +
                        "which should be a MethodElement");
                return;
            }
            ExecutableElement ele = ((ExecutableElement) e);
            if (ele.isDefault()) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, "this element [" + e + "] in [" + typeElement + "] " +
                        "has default method body, abort");
                return;
            }

            Method method = new Method(ele);

            // 判断有没有被 @JsonApplication 注解
            JsonApplication json = ele.getAnnotation(JsonApplication.class);
            if (json == null) {
                replacedMethodInfo.put(method, null);
                continue;
            }
            // 如果函数被 @JsonApplication 注解，寻找被 @JsonField 或 @JsonMap 注解的参数
            ReplacedMethod replacedMethod = new ReplacedMethod();
            replacedMethod.originalMethod = method;

            for (Parameter p : method.parameters) {
                // 遍历这个参数的每个注解，如果发现 @JsonField 或者 @JsonMap，
                // 则将这个参数是需要自定义处理的。我们暂时将其加到队列
                Annotation _json = null;
                for (Annotation ann : p.annotations) {
                    if (Constants.JSON_FIELD.equals(ann.className)) {
                        _json = ann;
                        break;
                    }
                    if (Constants.JSON_MAP.equals(ann.className)) {
                        _json = ann;
                        break;
                    }
                }

                if (_json == null) {
                    replacedMethod.rawParameters.add(p);
                }
                else {
                    replacedMethod.packedParameters.add(p);
                    replacedMethod.annotations.add(_json);
                }
            }
            if (replacedMethod.packedParameters.isEmpty()) {
                replacedMethodInfo.put(method, null);
                continue;
            }

            // 生成一个假的函数
            Method builder = new Method(ele);
            builder.parameters.clear();
            builder.parameters.addAll(replacedMethod.rawParameters);

            // 生成自定义参数 @retrofit2.http.Body RequestBody body 参数
            Parameter.Builder requestBody = new Parameter
                    .Builder(Constants.REQUEST_BODY, "body")
                    .addAnnotation(new Annotation.Builder(Constants.RETROFIT_BODY));
            builder.parameters.add(requestBody.build());
            replacedMethodInfo.put(builder, replacedMethod);
        }

        final String packageName = mElementUtils.getPackageOf(typeElement).getQualifiedName().toString();
        final String className = typeElement.getSimpleName().toString();

        // 创建新的接口
        // 将类注解附加过去
        final TypeSpec.Builder interfaceSpec = TypeSpec
                .interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
            Annotation annotation = new Annotation(am);
            if (! Constants.RETROFITJ.equals(annotation.className)) {
                interfaceSpec.addAnnotation(AnnotationSpec.get(am));
            }
        }
        // 将替换好的函数转移过去
        for (Method method : replacedMethodInfo.keySet()) {
            interfaceSpec.addMethod(method.toSpec());
        }
        // stub 类
        final TypeSpec stub = genStubClass(
                replacedMethodInfo,
                packageName,
                className,
                "retrofitj." + packageName,
                className
        );
        interfaceSpec.addType(stub);


        final JavaFile javaFile = JavaFile
                .builder("retrofitj." + packageName, interfaceSpec.build())
                .build();
        mMessager.printMessage(Diagnostic.Kind.WARNING, javaFile.toString());


        Writer writer = null;
        try {
            JavaFileObject obj = mFiler.createSourceFile(className);
            writer = obj.openWriter();
            writer.write(javaFile.toString());
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            safeClose(writer);
        }
    }


    private TypeSpec genStubClass(
            Map<Method, ReplacedMethod> replacedMethodInfo,
            String realPackageName,
            String realClassName,
            String proxyPackageName,
            String proxyClassName) {

        final ClassName realObjectType = ClassName.get(realPackageName, realClassName);
        final ClassName proxyObjectType = ClassName.get(proxyPackageName, proxyClassName);

        TypeSpec.Builder builder = TypeSpec
                .classBuilder("Stub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(realObjectType);

        FieldSpec proxyObject = FieldSpec
                .builder(proxyObjectType, "proxyObject")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();

        MethodSpec constructor = MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("retrofit2", "Retrofit"), "retrofit")
                .addCode("this.proxyObject = retrofit.create($N.class);\n", proxyClassName)
                .build();

        builder.addField(proxyObject).addMethod(constructor);

        // 实现接口里的所有函数
        for (Map.Entry<Method, ReplacedMethod> entry : replacedMethodInfo.entrySet()) {
            final Method method = entry.getKey();
            final ReplacedMethod replacedMethod = entry.getValue();

            MethodSpec.Builder methodSpec = MethodSpec
                    .methodBuilder(method.simpleName)
                    .returns(TypeName.get(method.rawElement.getReturnType()))
                    .addModifiers(Modifier.PUBLIC);

            // 为函数添加 @Override 注解
            methodSpec.addAnnotation(AnnotationSpec.builder(Override.class).build());

            if (replacedMethod == null) {
                // 说明这个函数没有被替换，复制参数
                for (Parameter p : method.parameters) {
                    methodSpec.addParameter(p.toSpec());
                }
                // 转发参数
                methodSpec.addCode("return this.proxyObject.$N(", method.simpleName);
                for (int i = 0, z = method.parameters.size(); i < z; i ++) {
                    final Parameter p = method.parameters.get(i);
                    if (i == z - 1)
                        methodSpec.addCode("$N", p.name);
                    else
                        methodSpec.addCode("$N, ", p.name);
                }
                methodSpec.addCode(");\n");
                builder.addMethod(methodSpec.build());
                continue;
            }

            // 走到这里说明这个函数的参数里存在 @JsonField 或 @JsonMap
            for (Parameter p : replacedMethod.originalMethod.parameters) {
                methodSpec.addParameter(p.toSpec());
            }

            // 遍历参数，优先处理 @JsonMap
            Parameter jsonMap = null;
            for (int i = 0, z = replacedMethod.packedParameters.size(); i < z; i ++) {
                if (Constants.JSON_MAP.equals(replacedMethod.annotations.get(i).className)) {
                    jsonMap = replacedMethod.packedParameters.get(i);
                    break;
                }
            }

            // 先处理 @JsonMap 的情况
            if (jsonMap != null && replacedMethod.packedParameters.size() != 1) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, "@JsonMap 和 @JsonField 不能一起食用");
                return null;
            }

            // 转发参数
            if (jsonMap != null) {
                methodSpec.addCode("RequestBody __auto_gen_body = $T.toJsonRequestBody($N);\n", jsonMap.name, Utils.class);
            }
            else {
                methodSpec.addCode("$T<String, Object> __auto_gen_map = new $T<>();\n", Map.class, LinkedHashMap.class);

                for (int i = 0, z = replacedMethod.packedParameters.size(); i < z; i ++) {
                    final Parameter p = replacedMethod.packedParameters.get(i);
                    final Annotation jsonField = replacedMethod.annotations.get(i);
                    methodSpec.addCode("__auto_gen_map.put($S, $N);\n", jsonField.valueMap.get("value"), p.name);
                }
                methodSpec.addCode("RequestBody __auto_gen_body = $T.toJsonRequestBody(__auto_gen_map);\n", Utils.class);
            }
            methodSpec.addCode("return this.proxyObject.$N(", method.simpleName);
            for (Parameter p : replacedMethod.rawParameters) {
                methodSpec.addCode("$N, ", p.name);
            }
            methodSpec.addCode("__auto_gen_body);\n");
            builder.addMethod(methodSpec.build());
        }
        return builder.build();
    }



    private void genMapClass(List<TypeElement> elementList) {
        Writer writer = null;

        try {
            JavaFileObject jfo = mFiler.createSourceFile("RetrofitJ");
            writer = jfo.openWriter();


            writer.append("package retrofitj.http;\n\n");
            writer.append("import java.util.Map;\n");
            writer.append("import java.util.LinkedHashMap;\n");
            writer.append("import retrofit2.Retrofit;\n");
            writer.append("\n\n");
            writer.append("public final class RetrofitJ {\n\n");
            writer.append("     private interface Creator {\n");
            writer.append("         Object create(Retrofit retrofit);\n");
            writer.append("     }");
            writer.append("\n\n");
            writer.append("     private static final Map<String, Creator> sCache = new LinkedHashMap<>();\n");
            writer.append("\n\n");
            writer.append("     static {\n");

            for (TypeElement ele : elementList) {
                final String packageName = mElementUtils.getPackageOf(ele).getQualifiedName().toString();
                final String className = ele.getSimpleName().toString();
                writer.append(String.format("       sCache.put(\"%s.%s\", (Creator) retrofit -> new retrofitj.%s.%s.Stub(retrofit));\n", packageName, className, packageName, className));
            }
            writer.append("     }\n");
            writer.append("\n\n");

            writer.append("     @SuppressWarnings(\"unchecked\")\n");
            writer.append("     public static <T> T create(Retrofit retrofit, Class<T> clazz) {\n");
            writer.append("         final Creator creator = sCache.get(clazz.getName());\n");
            writer.append("         if (creator == null) {\n");
            writer.append("             return retrofit.create(clazz);\n");
            writer.append("         }\n");
            writer.append("         return (T) creator.create(retrofit);\n");
            writer.append("     }\n");
            writer.append("}\n\n");
        }
        catch (Exception e) {
            e.printStackTrace();
            mMessager.printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
        finally {
            retrofitj.compiler.wrapper.Utils.safeClose(writer);
        }

        if (true) {
            return;
        }

        TypeSpec.Builder builder = TypeSpec
                .classBuilder("RetrofitJ")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        TypeSpec.Builder Builder = TypeSpec
                .interfaceBuilder("Builder")
                .addModifiers(Modifier.PUBLIC);

        ParameterSpec Builder_build_Retrofit = ParameterSpec
                .builder(ClassName.get("retrofit2", "Retrofit"), "retrofit")
                .build();

        MethodSpec.Builder Builder_build = MethodSpec
                .methodBuilder("build")
                .returns(Object.class)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(Builder_build_Retrofit);

        Builder.addMethod(Builder_build.build());
        builder.addType(Builder.build());

        FieldSpec.Builder sCache = FieldSpec
                .builder(parameterType(Map.class, String.class, Object.class), "sCache")
                .addModifiers(Modifier.FINAL, Modifier.PRIVATE, Modifier.STATIC);
        builder.addField(sCache.build());

        CodeBlock.Builder _static_ = CodeBlock
                .builder()
                .addStatement("sCache = new $T<>($N)", HashMap.class, Integer.toString(elementList.size()));

        for (TypeElement ele : elementList) {
            final String packageName = mElementUtils.getPackageOf(ele).getQualifiedName().toString();
            final String className = ele.getSimpleName().toString();

            _static_.addStatement("sCache.put($S, (Builder) retrofit -> new $T(retrofit))",
                    ele.toString(), ClassName.get("retrofitj." + packageName, className));
        }
        builder.addStaticBlock(_static_.build());


        ParameterSpec create_class = ParameterSpec
                .builder(Class.class, "clazz")
                .build();

        MethodSpec.Builder create = MethodSpec
                .methodBuilder("create")
                .returns(tType())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Builder_build_Retrofit)
                .addParameter(create_class);
        builder.addMethod(create.build());

        mMessager.printMessage(Diagnostic.Kind.WARNING, builder.build().toString());
    }


    private void handleInterface(TypeElement typeElement) {
        // 遍历每个函数。如果发现某个函数被自定义注解，生成代理函数；

        final StringBuilder sb = new StringBuilder(64);
        final List<String> methodList = new ArrayList<>(16);

        for (Element e : typeElement.getEnclosedElements()) {
            if (e.getKind() != ElementKind.METHOD) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, "unsupported element [" + e + "] found in [" + typeElement + "]," +
                        "which should be a MethodElement");
                return;
            }
            ExecutableElement ele = ((ExecutableElement) e);
            sb.setLength(0);

            if (ele.isDefault()) {
                mMessager.printMessage(Diagnostic.Kind.ERROR, "this element [" + e + "] in [" + typeElement + "] " +
                        "has default method body, abort");
                return;
            }

            // 检查函数中有没有我们感兴趣的注解
            final JsonApplication jsonApplication = ele.getAnnotation(JsonApplication.class);

            for (AnnotationMirror am : ele.getAnnotationMirrors()) {
//                final String annotation = am.toString();
                sb.append('\t').append(am).append('\n');
            }

            sb.append('\t').append(ele.getReturnType()).append(' ').append(ele.getSimpleName()).append("(\n");


            if (jsonApplication == null) {
                boolean hasVariableElement = false;
                for (VariableElement ve : ele.getParameters()) {
                    hasVariableElement = true;

                    sb.append("\t\t");
                    for (AnnotationMirror am : ve.getAnnotationMirrors()) {
                        sb.append(am).append(' ');
                    }
                    sb.append(ve.asType()).append(' ').append(ve).append(",\n");
                }
                if (hasVariableElement) {
                    sb.setLength(sb.length() - 2);
                }
            }
            else {
                sb.append("\t\t@").append(RETROFIT_BODY_NAME).append(' ').append(OKHTTP_REQUEST_BODY_NAME).append(" body");
            }
            sb.append("\n\t);\n\n");

//            mMessager.printMessage(Diagnostic.Kind.WARNING, sb);
            methodList.add(sb.toString());
        }


        // 创建 java 文件

        final String packageName = mElementUtils.getPackageOf(typeElement).getQualifiedName().toString();
        final String className = typeElement.getSimpleName().toString();

        sb.setLength(0);
        final String newPackageName = sb.append("retrofitj.").append(packageName).toString();

        sb.setLength(0);
        final String newClassName = sb.append(className).append("_Service").toString();

        Writer writer = null;

        try {
            JavaFileObject file = mFiler.createSourceFile(newPackageName + "." + newClassName);

            writer = file.openWriter();

            writer.write(String.format("package %s;\n", newPackageName));
            writer.write(AUTO_GEN_INFO);
            writer.write("public interface " + newClassName + " {\n");
            for (String str : methodList) {
                writer.write(str);
            }
            writer.write("\tpublic static final class Stub implements " + packageName + "." + className + " { \n\n");
            writer.write("\t\tprivate final " + newClassName + " mRealObject;\n\n");
            writer.write("\t\tpublic Stub(" + newClassName + " realObject) {\n");
            writer.write("\t\t\tmRealObject = realObject;\n");
            writer.write("\t\t}\n\n");

            for (Element e : typeElement.getEnclosedElements()) {
                final ExecutableElement ele = (ExecutableElement) e;
                final List<? extends VariableElement> paramList = ele.getParameters();


                writer.write("\t\t@Override\n");
                writer.write("\t\tpublic " + ele.getReturnType() + " " + ele.getSimpleName() + "(\n");

                for (int i = 0, z = paramList.size(); i < z; i ++) {
                    final VariableElement ve = paramList.get(i);
                    writer.write("\t\t\t " + ve.asType() + " _" + i + (i == z - 1 ? "" : ",") + "\n");
                }
                writer.write("\t\t) {\n");


                final JsonApplication application = ele.getAnnotation(JsonApplication.class);
                if (application == null) {
                    // 一般函数的代理逻辑
                    writer.write("\t\t\treturn mRealObject." + ele.getSimpleName() + "(");
                    for (int i = 0, z = paramList.size(); i < z; i ++) {
                        writer.write("_" + i + (i == z - 1 ? "" : ", "));
                    }
                    writer.write(");\n");
                }
                else {
                    // 跳板逻辑
                    writer.write("\t\t\tfinal java.util.Map<String, Object> map = new java.util.LinkedHashMap(" + paramList.size() + ", 1.0F);\n");
                    for (int i = 0, z = paramList.size(); i < z; i ++) {
                        final VariableElement ve = paramList.get(i);
                        writer.write("\t\t\tmap.put(\"" + ve + "\", _" + i + ");\n");
                    }
                    writer.write("\t\t\treturn mRealObject." + ele.getSimpleName() + "(retrofitj.utils.Utils.toJsonRequestBody(map));\n");
                }
                writer.write("\t\t}\n\n");
            }

            writer.write("\t}\n");
            writer.write("}");
        }
        catch (Exception e) {
            mMessager.printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
        finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

//        final List<String> methodSpecList = new ArrayList<>(16);
    }

    private static final String AUTO_GEN_INFO = "\n\n" +
            "/**\n" +
            "  * DO NOT EDIT THIS !\n" +
            "  * This file is generated by annotation processor, any modifiers will lost when next build\n" +
            "  */\n" +
            "\n\n";

//    private static final String JSON_APPLICATION_NAME = JsonApplication.class.getName();
    private static final String RETROFIT_BODY_NAME = "retrofit2.http.Body";
    private static final String OKHTTP_REQUEST_BODY_NAME = "okhttp3.RequestBody";


    private void whoAreYou(Element it) {
        for (Class<?> i = it.getClass(); i != null; i = i.getSuperclass()) {
            mMessager.printMessage(Diagnostic.Kind.WARNING, "--------------------->");
            mMessager.printMessage(Diagnostic.Kind.WARNING, i.getName());
            for (Class<?> j : i.getInterfaces()) {
                mMessager.printMessage(Diagnostic.Kind.WARNING, j.getName());
            }
        }
    }
}






