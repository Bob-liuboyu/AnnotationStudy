package androidtest.project.com.apt_annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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