package androidtest.project.com.apt_library;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import androidtest.project.com.apt_annotation.BindView;

/**
 * @author liuboyu  E-mail:545777678@qq.com
 * @Date 2019-08-21
 * @Description 注解工具类
 */
public class BindViewTools {
    /**
     * 编译时注解方式
     *
     * @param activity
     */
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
}
