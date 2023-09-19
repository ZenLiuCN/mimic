package cn.zenliu.java.mimic;

import cn.zenliu.units.reflect.Ref;
import lombok.SneakyThrows;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Zen.Liu
 * @since 2023-09-19
 */
@ApiStatus.AvailableSince("1.1.2")
public
interface Util {
    @SneakyThrows
    static MethodHandle fetchMethod(Class<?> declaringClass, Method m) {
        return Ref.$.unreflectSpeical(m, declaringClass);
    }

    @SneakyThrows
    static Object fetchStaticFieldValue(Class<?> cls, String field) {
        var mField = cls.getField(field);
        if (!Modifier.isStatic(mField.getModifiers()))
            throw new IllegalStateException("not static field '" + field + "' on '" + cls + "'");
        return Ref.$.access(mField).get(null);
    }

    static <T extends Annotation> List<T> collectAnnotations(Method m, Class<T> an, List<Class<?>> faces) {
        var collected = new ArrayList<T>();
        for (Class<?> cls : faces) {
            try {
                var fn = cls.getDeclaredMethod(m.getName(), m.getParameterTypes());
                collected.addAll(Arrays.asList(fn.getAnnotationsByType(an)));
            } catch (NoSuchMethodException ignored) {

            }
        }
        return collected;
    }

    interface Predication {
        Predicate<Method> isPublicNoneDefault = x -> !x.isDefault() &&
                                                     !Modifier.isStatic(x.getModifiers()) &&
                                                     Modifier.isPublic(x.getModifiers());
        Predicate<Method> isBeanGetter = x -> (x.getParameterCount() == 0 &&
                                               (x.getName().startsWith("get") && x.getReturnType() != Void.TYPE ||
                                                (x.getReturnType() == boolean.class && x.getName().startsWith("is")))
        );
        Predicate<Method> isBeanSetter = x -> (x.getParameterCount() == 1 &&
                                               x.getName().startsWith("set") &&
                                               (x.getReturnType() == Void.TYPE || x.getReturnType() == x.getDeclaringClass()));

        Predicate<Method> isFluentSetter = x -> (
            x.getParameterCount() == 1 &&
            (x.getReturnType() == Void.TYPE || x.getReturnType() == x.getDeclaringClass())
        );
        Predicate<Method> isFluentGetter = x -> (x.getParameterCount() == 0 && x.getReturnType() != Void.TYPE);
        Predicate<Method> fluentGetter = isPublicNoneDefault.and(isFluentGetter);
        Predicate<Method> fluentSetter = isPublicNoneDefault.and(isFluentSetter);
        Predicate<Method> beanSetter = isPublicNoneDefault.and(isBeanSetter);
        Predicate<Method> beanGetter = isPublicNoneDefault.and(isBeanGetter);
        Predicate<Method> mixGetter = isPublicNoneDefault.and(isFluentGetter.or(isBeanGetter));
        Predicate<Method> mixSetter = isPublicNoneDefault.and(isFluentSetter.or(isBeanSetter));
        Function<Method, String> fluentGetterName = Method::getName;
        Function<Method, String> fluentSetterName = Method::getName;
        Function<Method, String> beanGetterName = x -> Case.pascalToCamel(x.getName().startsWith("is") ? x.getName().substring(2) : x.getName().substring(3));
        Function<Method, String> beanSetterName = x -> Case.pascalToCamel(x.getName().substring(3));
        Function<Method, String> mixSetterName = x -> x.getName().startsWith("set") ? Case.pascalToCamel(x.getName().substring(3)) : x.getName();
        Function<Method, String> mixGetterName = x -> x.getName().startsWith("is") || x.getName().startsWith("get") ? beanGetterName.apply(x) : x.getName();

    }

    static String methodPropertyNameExtract(String x) {
        return x.startsWith("is") ? Case.pascalToCamel(x.substring(2)) :
            x.startsWith("get") || x.startsWith("set") ? Case.pascalToCamel(x.substring(3)) : x;
    }

    interface Case {
        int CASE_MASK = 0x20;

        static boolean isLower(char c) {
            return (c >= 'a') && (c <= 'z');
        }

        static boolean isUpper(char c) {
            return (c >= 'A') && (c <= 'Z');
        }

        static char toUpper(char c) {
            return isLower(c) ? (char) (c ^ CASE_MASK) : c;
        }

        static char toLower(char c) {
            return isUpper(c) ? (char) (c ^ CASE_MASK) : c;
        }

        static boolean isLowerUnderscore(String str, char divider) {
            return str.chars().allMatch(x -> x == divider || !isUpper((char) x));
        }

        static boolean isUpperDivide(String str, char divider) {
            return str.chars().allMatch(x -> x == divider || !isLower((char) x));
        }

        static boolean isPascal(String str) {
            return isUpper(str.charAt(0)) && str.chars().noneMatch(x -> x == '-' || x == '_');
        }

        static boolean isCamel(String str) {
            return isLower(str.charAt(0)) && str.chars().noneMatch(x -> x == '-' || x == '_');
        }

        static String camelToLowerDivide(String str, char divider) {
            var sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (isUpper(c)) {
                    sb.append(divider).append(toLower(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        static String camelToUpperDivide(String str, char divider) {
            var sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (isUpper(c)) {
                    sb.append(divider).append(c);
                } else {
                    sb.append(toUpper(c));
                }
            }
            return sb.toString();
        }

        static String camelToPascal(String str) {
            return toUpper(str.charAt(0)) + str.substring(1);
        }

        static String pascalToCamel(String str) {
            return toLower(str.charAt(0)) + str.substring(1);
        }

        static String pascalToUpperDivide(String str, char divider) {
            var sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (isUpper(c)) {
                    if (sb.length() > 2)
                        sb.append(divider);
                    sb.append(c);
                } else {
                    sb.append(toUpper(c));
                }
            }
            return sb.toString();
        }

        static String pascalToLowerDivide(String str, char divider) {
            var sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (isUpper(c)) {
                    if (sb.length() > 2)
                        sb.append(divider);
                    sb.append(toLower(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        static String divideToCamel(String str, char divider) {
            var sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (c == divider) {
                    if (sb.length() == 0) {
                        continue;
                    }
                    sb.append(Case.toUpper(c));
                } else {
                    sb.append(Case.toLower(c));
                }
            }
            return sb.toString();
        }

        static String divideToPascal(String str, char divider) {
            var sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (c == divider) {
                    if (sb.length() == 0) {
                        continue;
                    }
                    sb.append(Case.toUpper(c));
                } else {
                    if (sb.length() == 0) {
                        sb.append(Case.toUpper(c));
                    } else sb.append(Case.toLower(c));
                }
            }
            return sb.toString();
        }

        Function<String, String> classToTable = s -> pascalToLowerDivide(s, '_');
        Function<String, String> propertyToTable = s -> camelToLowerDivide(s, '_');
        Function<String, String> tableToClass = s -> divideToPascal(s, '_');
        Function<String, String> tableToProperty = s -> divideToCamel(s, '_');
    }

}
