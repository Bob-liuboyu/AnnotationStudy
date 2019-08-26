# Android APT 注解开发--实践之BindView
### 什么是apt
APT(Annotation Processing Tool)即注解处理器，是一种处理注解的工具，确切的说它是javac的一个工具，它用来在编译时扫描和处理注解。注解处理器以Java代码(或者编译过的字节码)作为输入，生成.java文件作为输出。
简单来说就是在编译期，通过注解生成.java文件。

如果没接触过注解开发的同学可以看我之前的文章
[Android注解--初探](https://www.jianshu.com/p/f5cdc2c3725b)

### apt 的作用
使用APT的优点就是方便、简单，可以少些很多重复的代码。

用过ButterKnife、Dagger、EventBus等注解框架的同学就能感受到，利用这些框架可以少些很多代码，只要写一些注解就可以了。
其实，他们不过是通过注解，生成了一些代码。

### 本文需求
通过APT实现一个功能，通过对View变量的注解，实现View的绑定（类似于ButterKnife中的@BindView）

#### 创建项目
- 创建Android Module命名为app 依赖 apt_library
- 创建Java library Module命名为 apt_annotation
- 创建Java library Module命名为 apt_processor 依赖 apt_annotation
- 创建Android library Module 命名为 apt_library 依赖 apt_processor

> 注解开发需要创建Java library因为有些方法、类  Android library 中并不支持
#### Module职责
- apt_annotation：自定义注解，存放@BindView
- apt_processor：注解处理器，根据apt-annotation中的注解，在编译期生成xxxActivity_ViewBinding.java代码
- apt_library：工具类，调用xxxActivity_ViewBinding.java中的方法，实现View的绑定。

#### 实现
其实有两种方式可以实现这个功能
1. RetentionPolicy.CLASS 编译时注解
2. RetentionPoicy.RUNTIME 运行时注解

在很多情况下，运行时注解和编译时注解可以实现相同的功能，比如依赖注入框架，我们既可以在运行时通过反射来初始化控件，也可以再编译时就生成控件初始化代码。那么，这两者有什么区别呢？
答：编译时注解性能比运行时注解好，运行时注解需要使用到反射技术，对程序的性能有一定影响，而编译时注解直接生成了源代码，运行过程中直接执行代码，没有反射这个过程。
#### 实现一：RetentionPolicy.CLASS 编译时注解
##### 1、创建注解类BindView
```
/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description BindView 注解定义
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface BindView {
    int value();
}
```
>@Retention(RetentionPolicy.CLASS)：表示编译时注解
>@Target(ElementType.FIELD)：表示注解范围为类成员（构造方法、方法、成员变量）
##### 2、apt_processor（注解处理器）
在Module中添加依赖
```
dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')
    api project(':apt_annotation')
}
```
创建BindViewProcessor
```
/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description 注解处理器
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
        return SourceVersion.RELEASE_8;
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
}
```

- init：初始化。可以得到ProcessingEnviroment，ProcessingEnviroment提供很多有用的工具类Elements, Types 和 Filer
- getSupportedAnnotationTypes：指定这个注解处理器是注册给哪个注解的，这里说明是注解BindView
- getSupportedSourceVersion：指定使用的Java版本，通常这里返回SourceVersion.latestSupported()
- process：可以在这里写扫描、评估和处理注解的代码，生成Java文件

ClassCreatorProxy是创建Java代码的代理类，如下：
```
/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description 生成代码工具类
 */
public class ClassCreatorProxy {
    private String mBindingClassName;
    private String mPackageName;
    private TypeElement mTypeElement;
    private Map<Integer, VariableElement> mVariableElementMap = new HashMap<>();

    public ClassCreatorProxy(Elements elementUtils, TypeElement classElement) {
        this.mTypeElement = classElement;
        PackageElement packageElement = elementUtils.getPackageOf(mTypeElement);
        String packageName = packageElement.getQualifiedName().toString();
        String className = mTypeElement.getSimpleName().toString();
        this.mPackageName = packageName;
        this.mBindingClassName = className + "_ViewBinding";
    }

    public void putElement(int id, VariableElement element) {
        mVariableElementMap.put(id, element);
    }

    /**
     * 创建Java代码
     *
     * @return
     */
    public String generateJavaCode() {
        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(mPackageName).append(";\n\n");
        builder.append("import androidtest.project.com.apt_library.*;\n");
        builder.append('\n');
        builder.append("public class ").append(mBindingClassName);
        builder.append(" {\n");

        generateMethods(builder);
        builder.append('\n');
        builder.append("}\n");
        return builder.toString();
    }

    /**
     * 加入Method
     *
     * @param builder
     */
    private void generateMethods(StringBuilder builder) {
        builder.append("public void bind(" + mTypeElement.getQualifiedName() + " host ) {\n");
        for (int id : mVariableElementMap.keySet()) {
            VariableElement element = mVariableElementMap.get(id);
            String name = element.getSimpleName().toString();
            String type = element.asType().toString();
            builder.append("host." + name).append(" = ");
            builder.append("(" + type + ")host.findViewById( " + id + ");\n");
        }
        builder.append("  }\n");
    }

    public String getProxyClassFullName() {
        return mPackageName + "." + mBindingClassName;
    }

    public TypeElement getTypeElement() {
        return mTypeElement;
    }
}
```

添加SPI配置文件，对于SPI不是很理解的同学，可以看我的[Android 动态服务SPI--模块节藕](https://www.jianshu.com/p/4be48efcce33)
1. 需要在 processors 库的 main 目录下新建 resources 资源文件夹；
2. 在 resources文件夹下建立 META-INF/services 目录文件夹；
3. 在 META-INF/services 目录文件夹下创建 javax.annotation.processing.Processor 文件；
4. 在 javax.annotation.processing.Processor 文件写入注解处理器的全称，包括包路径；）

文件内容如下
```
androidtest.project.com.apt_processor.BindViewProcessor
```
##### 3、apt_library 工具类
在BindViewProcessor中创建了对应的xxxActivity_ViewBinding.java，我们改怎么调用？当然是反射啦！！！

在Module的build.gradle中添加依赖
```
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    api rootProject.ext.dependencies.appcompatV7
    api project(':apt_processor')
}
```
创建注解工具类BindViewTools
```
/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description 注解工具类
 */
public class BindViewTools {
    public static void bind(Activity activity) {

        Class clazz = activity.getClass();
        try {
            Class bindViewClass = Class.forName(clazz.getName() + "_ViewBinding");
            Method method = bindViewClass.getMethod("bind", activity.getClass());
            method.invoke(bindViewClass.newInstance(), activity);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
```
apt_library的部分就比较简单了，通过反射找到对应的ViewBinding类，然后调用其中的bind()方法完成View的绑定。

##### 3、app主模块
在 app Module 的 build.gradle中添加依赖
```
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    api rootProject.ext.dependencies.appcompatV7
    api rootProject.ext.dependencies.design
    implementation project(':apt_library')
}
```

使用
在MainActivity中，在View的前面加上BindView注解，把id传入即可
```
public class MainActivity extends AppCompatActivity {

    @BindView(value = R.id.tv_1)
    TextView mTextView;
    @BindView(value = R.id.btn_2)
    Button mButton;
    @BindView(value = R.id.iv_3)
    ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BindViewTools.bind(this);
        mTextView.setText("我是 TextView");
        mButton.setText("我是 Button");
        mImageView.setImageResource(R.color.colorPrimary);
    }
}
```

Make project 是骡子是马出来溜溜，看一下我们生成的代码

![屏幕快照 2019-08-22 上午10.45.19.png](https://upload-images.jianshu.io/upload_images/1959357-2337876c068d6d4c.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

```
public class MainActivity_ViewBinding {
    public void bind(androidtest.project.com.annotationstudy.MainActivity host) {
        host.mButton = (android.widget.Button) host.findViewById(2131230754);
        host.mImageView = (android.widget.ImageView) host.findViewById(2131230802);
        host.mTextView = (android.widget.TextView) host.findViewById(2131230899);
    }
}
```
看一下运行结果：
![WechatIMG286.jpeg](https://upload-images.jianshu.io/upload_images/1959357-027e1a5600a386a4.jpeg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


ok！我们的目的已经达成了，接下来我们反思一下
>1. 手动配置spi略嫌麻烦，有没有什么便捷方式？
>2. java 代码都是通过 StringBuilder 一点一点拼出来的，很容易出错，有什么更好的办法么？

当然都可以解决
###### 问题一：
Google 提供的便捷的工具，通过auto-service中的@AutoService即可以自动生成AutoService注解处理器，自动生成 META-INF/services/javax.annotation.processing.Processor
使用方法也很简单
apt_processor gradle 引入依赖
```
dependencies {
    implementation fileTree(include: ['*.jar'], dir: 'libs')
    implementation 'com.google.auto.service:auto-service:1.0-rc2'
    api project(':apt_annotation')
}
```
这里有个坑，特么查好久，死活生成不了

Android Gradle由4.x升级至5.0，需要引入下面这句话，否则无法自动生成 spi配置文件
```
annotationProcessor "com.google.auto.service:auto-service:1.0-rc2"
```
然后修改 BindViewProcessor 文件，添加@AutoService(Processor.class)即可
```
@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor
```
看一下文件的生成位置
![屏幕快照 2019-08-22 下午2.04.35.png](https://upload-images.jianshu.io/upload_images/1959357-26f7f250aa7b601f.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


###### 问题二：
可以利用java提供的 javapoet 来生成java 代码
本文也做了实践
```
/**
     * 创建Java代码
     * @return
     */
    public TypeSpec generateJavaCode2() {
        TypeSpec bindingClass = TypeSpec.classBuilder(mBindingClassName)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(generateMethods2())
                .build();
        return bindingClass;

    }

    /**
     * 加入Method
     */
    private MethodSpec generateMethods2() {
        ClassName host = ClassName.bestGuess(mTypeElement.getQualifiedName().toString());
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("bind")
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addParameter(host, "host");

        for (int id : mVariableElementMap.keySet()) {
            VariableElement element = mVariableElementMap.get(id);
            String name = element.getSimpleName().toString();
            String type = element.asType().toString();
            methodBuilder.addCode("host." + name + " = " + "(" + type + ")host.findViewById( " + id + ");\n");
        }
        return methodBuilder.build();
    }


    public String getPackageName() {
        return mPackageName;
    }
```

修改 BindViewProcessor 生成方式即可
```
@Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        //省略部分代码...
        //通过javapoet生成
        for (String key : mProxyMap.keySet()) {
            ClassCreatorProxy proxyInfo = mProxyMap.get(key);
            JavaFile javaFile = JavaFile.builder(proxyInfo.getPackageName(), proxyInfo.generateJavaCode2()).build();
            try {
                //　生成文件
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mMessager.printMessage(Diagnostic.Kind.NOTE, "process finish ...");
        return true;
    }
```

如果感兴趣，可以学一下 [javapoet详细用法](https://github.com/square/javapoet)

#### 实现二：RetentionPoicy.RUNTIME 运行时注解
这个就很简单了，从字面就可以理解，运行的时候，再去解析注解，下面看一下实现
```
/**
     * 运行时解析注解 BindView
     *
     * @param activity 使用InjectView的目标对象
     */
    public static void inject(Activity activity) {
        Field[] fields = activity.getClass().getDeclaredFields();
        //通过该方法设置所有的字段都可访问，否则即使是反射，也不能访问private修饰的字段
        AccessibleObject.setAccessible(fields, true);
        for (Field field : fields) {
            boolean needInject = field.isAnnotationPresent(BindView.class);
            if (needInject) {
                BindView anno = field.getAnnotation(BindView.class);
                int id = anno.value();
                if (id == -1) {
                    continue;
                }
                View view = activity.findViewById(id);
                Class fieldType = field.getType();
                try {
                    //把View转换成field声明的类型
                    field.set(activity, fieldType.cast(view));
                } catch (Exception e) {
                    Log.e(BindView.class.getSimpleName(), e.getMessage());
                }
            }
        }
    }
```
再把 BindView 注解改为运行时注解即可
```
/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description BindView 注解定义
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BindView {
    int value();
}
```

我们之前讲过,编译时注解性能比运行时注解好，运行时注解需要使用到反射技术，对程序的性能有一定影响，而编译时注解直接生成了源代码，运行过程中直接执行代码，没有反射这个过程。

最后呈上本文 [github Demo 链接](https://github.com/Bob-liuboyu/AnnotationStudy)

参考博客

- [Android APT](https://www.jianshu.com/p/7af58e8e3e18)
- [Android注解&APT技术](https://www.jianshu.com/p/7454a933dcaf)
- [Android Gradle由4.x升级至5.0导致Apt项目失效](https://www.jianshu.com/p/098a9573f2c0)
