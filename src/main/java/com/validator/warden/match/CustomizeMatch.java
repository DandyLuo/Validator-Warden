package com.validator.warden.match;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.validator.warden.annotation.Matcher;
import com.validator.warden.core.domain.WdContext;
import com.validator.warden.exception.JudgeException;
import com.validator.warden.funcation.MultiPredicate;
import com.validator.warden.util.SpringSingleFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统自行判断，对应{@link Matcher#customize()}
 *
 * @author DandyLuo
 */
@Slf4j
public class CustomizeMatch extends AbstractBlackWhiteMatch {

    private Predicate<Object> valuePre = null;
    private BiPredicate<Object, Object> objValuePre = null;
    private BiPredicate<Object, Object> valueContextPre = null;
    private MultiPredicate<Object, Object, WdContext> objValueContextPre = null;
    private String judgeStr;
    private WdContext context;

    @Override
    public boolean match(final Object object, final String name, final Object value) {
        if (null != this.valuePre) {
            if (this.valuePre.test(value)) {
                this.setBlackMsg("属性 {0} 的值 {1} 命中禁用条件回调 {2} ", name, value, this.judgeStr);
                return true;
            } else {
                this.setWhiteMsg("属性 {0} 的值 {1} 没命中只允许条件回调 {2} ", name, value, this.judgeStr);
            }
        } else if (null != this.valueContextPre) {
            if (this.valueContextPre.test(value, this.context)) {
                this.setBlackMsg("属性 {0} 的值 {1} 命中禁用条件回调 {2} ", name, value, this.judgeStr);
                return true;
            } else {
                this.setWhiteMsg("属性 {0} 的值 {1} 没命中只允许条件回调 {2} ", name, value, this.judgeStr);
            }
        } else if (null != this.objValuePre) {
            if (this.objValuePre.test(object, value)) {
                this.setBlackMsg("属性 {0} 的值 {1} 命中禁用条件回调 {2} ", name, value, this.judgeStr);
                return true;
            } else {
                this.setWhiteMsg("属性 {0} 的值 {1} 没命中只允许条件回调 {2} ", name, value, this.judgeStr);
            }
        } else if (null != this.objValueContextPre) {
            if (this.objValueContextPre.test(object, value, this.context)) {
                this.setBlackMsg("属性 {0} 的值 {1} 命中禁用条件回调 {2} ", name, value, this.judgeStr);
                return true;
            } else {
                this.setWhiteMsg("属性 {0} 的值 {1} 没命中只允许条件回调 {2} ", name, value, this.judgeStr);
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return null == this.valuePre && null == this.objValuePre && null == this.valueContextPre && null == this.objValueContextPre;
    }

    /**
     * 将一个类中的函数转换为一个系统自定义的过滤匹配器
     *
     * <p>
     * 过滤器可以有多种类型，根据参数的不同有不同的类型
     * <p>
     * @param judge 回调判决，这里是类和对应的函数组成
     * @param context 上下文
     * @return 匹配器的判决器
     */
    @SuppressWarnings("all")
    public static CustomizeMatch build(String judge, WdContext context) {
        if (null == judge || judge.isEmpty() || !judge.contains("#")) {
            return null;
        }

        CustomizeMatch customizeMatcher = new CustomizeMatch();
        int index = judge.indexOf("#");
        String classStr = judge.substring(0, index);
        String funStr = judge.substring(index + 1);
        // 是否包含函数标志
        AtomicReference<Boolean> containFlag = new AtomicReference<>(false);

        try {
            Class<?> cls = Class.forName(classStr);
            Object object = SpringSingleFactory.getSingle(cls);
            String booleanStr = "boolean";
            customizeMatcher.judgeStr = judge;
            customizeMatcher.context = context;

            // 这里对系统回调支持两种回调方式
            Stream.of(cls.getDeclaredMethods()).filter(m -> m.getName().equals(funStr)).forEach(m -> {
                containFlag.set(true);
                Class<?> returnType = m.getReturnType();
                if (returnType.getSimpleName().equals(Boolean.class.getSimpleName())
                    || returnType.getSimpleName().equals(booleanStr)) {
                    int paramsCnt = m.getParameterCount();
                    // 一个参数，则该参数为属性的类型
                    if (1 == paramsCnt) {
                        customizeMatcher.valuePre = v -> {
                            try {
                                m.setAccessible(true);
                                return (boolean) m.invoke(object, v);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                log.error(e.getMessage());
                                throw new RuntimeException(e);
                            }
                        };
                    } else if (2 == paramsCnt) {
                        Class<?> p2Cls = m.getParameterTypes()[1];
                        if (WdContext.class.isAssignableFrom(p2Cls)) {
                            // 两个参数，则第一个为核查的对象，第二个为参数为属性的值
                            customizeMatcher.valueContextPre = (v, c) -> {
                                try {
                                    m.setAccessible(true);
                                    return (boolean) m.invoke(object, v, c);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    log.error(e.getMessage());
                                    throw new RuntimeException(e);
                                }
                            };
                        } else {
                            // 两个参数，则第一个为待核查的属性的值，第二个为MkConstext
                            customizeMatcher.objValuePre = (o, v) -> {
                                try {
                                    m.setAccessible(true);
                                    return (boolean) m.invoke(object, o, v);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    log.error(e.getMessage());
                                    throw new RuntimeException(e);
                                }
                            };
                        }
                    } else if (3 == paramsCnt) {
                        Class<?> p3Cls = m.getParameterTypes()[2];
                        // 三个参数，这个时候，第一个参数是核查的对象，第二个参数为属性的值，第三个参数为contexts
                        if(WdContext.class.isAssignableFrom(p3Cls)) {
                            customizeMatcher.objValueContextPre = (o, v, c) -> {
                                try {
                                    m.setAccessible(true);
                                    return (boolean) m.invoke(object, o, v, c);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    log.error(e.getMessage());
                                    throw new RuntimeException(e);
                                }
                            };
                        } else {
                            try {
                                throw new JudgeException("函数"+funStr+"参数匹配失败，三个参数的时候，第三个参数需要为MkContext类型");
                            } catch (JudgeException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } else {
                        try {
                            throw new JudgeException("函数"+funStr+"的参数匹配失败，最多三个参数");
                        } catch (JudgeException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    try {
                        throw new JudgeException("函数"+funStr+"返回值不是boolean，添加匹配器失败");
                    } catch (JudgeException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (Exception e) {
            if (e instanceof ClassNotFoundException) {
                log.error("类{}路径没有找到", classStr, e);
            } else {
                throw new RuntimeException(e);
            }
        }

        if(!containFlag.get()){
            try {
                throw new JudgeException("类"+classStr+"不包含函数" + funStr);
            } catch (JudgeException e) {
                throw new RuntimeException(e);
            }
        }

        return customizeMatcher;
    }
}
