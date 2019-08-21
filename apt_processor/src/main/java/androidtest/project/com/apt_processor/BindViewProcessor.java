package androidtest.project.com.apt_processor;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import androidtest.project.com.apt_annotation.BindView;

/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description 注解 Processor
 */
public class BindViewProcessor extends AbstractProcessor {

    private Messager mMessager;
    private Elements mElementUtils;
    private Map<String, ClassCreatorProxy> mProxyMap = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mMessager = processingEnv.getMessager();
        mElementUtils = processingEnv.getElementUtils();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(BindView.class.getCanonicalName());
        return supportTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        mMessager.printMessage(Diagnostic.Kind.NOTE, "processing...");
        mProxyMap.clear();
        //获得被BindView注解标记的element
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(BindView.class);
        //对不同的Activity进行分类
        for (Element element : elements) {
            VariableElement variableElement = (VariableElement) element;
            TypeElement classElement = (TypeElement) variableElement.getEnclosingElement();
            String fullClassName = classElement.getQualifiedName().toString();
            ClassCreatorProxy proxy = mProxyMap.get(fullClassName);
            if (proxy == null) {
                proxy = new ClassCreatorProxy(mElementUtils, classElement);
                mProxyMap.put(fullClassName, proxy);
            }
            BindView bindAnnotation = variableElement.getAnnotation(BindView.class);
            int id = bindAnnotation.value();
            proxy.putElement(id, variableElement);
        }
        //通过遍历mProxyMap，创建java文件
        for (String key : mProxyMap.keySet()) {
            ClassCreatorProxy proxyInfo = mProxyMap.get(key);
            try {
                mMessager.printMessage(Diagnostic.Kind.NOTE, " --> create " + proxyInfo.getProxyClassFullName());
                JavaFileObject jfo = processingEnv.getFiler().createSourceFile(proxyInfo.getProxyClassFullName(), proxyInfo.getTypeElement());
                Writer writer = jfo.openWriter();
                writer.write(proxyInfo.generateJavaCode());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                mMessager.printMessage(Diagnostic.Kind.NOTE, " --> create " + proxyInfo.getProxyClassFullName() + "error");
            }
        }

        mMessager.printMessage(Diagnostic.Kind.NOTE, "process finish ...");
        return true;
    }


    /**
     * 要绑定的View的信息载体
     */
    class ViewInfo {
        /**
         * //view的变量名
         */
        String viewName;
        /**
         * xml中的id
         */
        int id;

        public ViewInfo(String viewName, int id) {
            this.viewName = viewName;
            this.id = id;
        }
    }

//    /**
//     * 文件管理工具类
//     */
//    private Filer mFilerUtils;
//    /**
//     * Element处理工具类
//     */
//    private Elements mElementsUtils;
//    /**
//     * 用于记录需要绑定的View的名称和对应的id
//     */
//    private Map<TypeElement, Set<ViewInfo>> mToBindMap = new HashMap<>();
//
//    @Override
//    public synchronized void init(ProcessingEnvironment processingEnv) {
//        super.init(processingEnv);
//
//        mFilerUtils = processingEnv.getFiler();
//        mElementsUtils = processingEnv.getElementUtils();
//    }
//
//    /**
//     * 将要支持的注解放入其中
//     *
//     * @return
//     */
//    @Override
//    public Set<String> getSupportedAnnotationTypes() {
//        HashSet<String> supportTypes = new LinkedHashSet<>();
//        supportTypes.add(BindView.class.getCanonicalName());
//        return supportTypes;
//    }
//
//    /**
//     * 表示支持最新的Java版本
//     *
//     * @return
//     */
//    @Override
//    public SourceVersion getSupportedSourceVersion() {
//        return SourceVersion.latestSupported();
//    }
//
//    @Override
//    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
//        System.out.println("start process");
//        if (set != null && set.size() != 0) {
//            //获得被BindView注解标记的element
//            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(BindView.class);
//            //对不同的Activity进行分类
//            categories(elements);
//
//            //对不同的Activity生成不同的帮助类
//            for (TypeElement typeElement : mToBindMap.keySet()) {
//                //获取要生成的帮助类中的所有代码
//                String code = generateCode(typeElement);
//                //构建要生成的帮助类的类名
//                String helperClassName = typeElement.getQualifiedName() + "$$Autobind";
//
//                //输出帮助类的java文件，在这个例子中就是MainActivity$$Autobind.java文件
//                //输出的文件在build->source->apt->目录下
//                try {
//                    JavaFileObject jfo = mFilerUtils.createSourceFile(helperClassName, typeElement);
//                    Writer writer = jfo.openWriter();
//                    writer.write(code);
//                    writer.flush();
//                    writer.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//            }
//            return true;
//        }
//        return false;
//    }
//
//    private void categories(Set<? extends Element> elements) {
//        //遍历每一个element
//        for (Element element : elements) {
//            //被@BindView标注的应当是变量，这里简单的强制类型转换
//            VariableElement variableElement = (VariableElement) element;
//            //获取代表Activity的TypeElement
//            TypeElement enclosingElement = (TypeElement) variableElement.getEnclosingElement();
//            //views储存着一个Activity中将要绑定的view的信息
//            Set<ViewInfo> views = mToBindMap.get(enclosingElement);
//            //如果views不存在就new一个
//            if (views == null) {
//                views = new HashSet<>();
//                mToBindMap.put(enclosingElement, views);
//            }
//            //获取到一个变量的注解
//            BindView bindAnnotation = variableElement.getAnnotation(BindView.class);
//            //取出注解中的value值，这个值就是这个view要绑定的xml中的id
//            int id = bindAnnotation.value();
//            //把要绑定的View的信息存进views中
//            views.add(new ViewInfo(variableElement.getSimpleName().toString(), id));
//        }
//    }
//
//    private String generateCode(TypeElement typeElement) {
//        //获取要绑定的View所在类的名称
//        String rawClassName = typeElement.getSimpleName().toString();
//        //获取要绑定的View所在类的包名
//        String packageName = ((PackageElement) mElementsUtils.getPackageOf(typeElement)).getQualifiedName().toString();
//        //要生成的帮助类的名称
//        String helperClassName = rawClassName + "$$Autobind";
//
//        StringBuilder builder = new StringBuilder();
//        //构建定义包的代码
//        builder.append("package ").append(packageName).append(";\n");
//        //构建import类的代码
//        builder.append("import androidtest.project.com.apt_library.IBindHelper;\n\n");
//
//        //构建定义帮助类的代码
//        builder.append("public class ").append(helperClassName).append(" implements ").append("IBindHelper");
//        builder.append(" {\n");
//        //声明这个方法为重写IBindHelper中的方法
//        builder.append("\t@Override\n");
//        //构建方法的代码
//        builder.append("\tpublic void inject(" + "Object" + " target ) {\n");
//        //遍历每一个需要绑定的view
//        for (ViewInfo viewInfo : mToBindMap.get(typeElement)) {
//            builder.append("\t\t");
//            //强制类型转换
//            builder.append(rawClassName + " substitute = " + "(" + rawClassName + ")" + "target;\n");
//
//            builder.append("\t\t");
//            //构建赋值表达式
//            builder.append("substitute." + viewInfo.viewName).append(" = ");
//            //构建赋值表达式
//            builder.append("substitute.findViewById(" + viewInfo.id + ");\n");
//        }
//        builder.append("\t}\n");
//        builder.append('\n');
//        builder.append("}\n");
//
//        return builder.toString();
//    }
//
//    /**
//     * 要绑定的View的信息载体
//     */
//    class ViewInfo {
//        /**
//         * //view的变量名
//         */
//        String viewName;
//        /**
//         * xml中的id
//         */
//        int id;
//
//        public ViewInfo(String viewName, int id) {
//            this.viewName = viewName;
//            this.id = id;
//        }
//    }
}