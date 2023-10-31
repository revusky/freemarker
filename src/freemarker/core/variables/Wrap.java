package freemarker.core.variables;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.lang.reflect.Array;
import freemarker.core.variables.scope.Scope;

public class Wrap {

    public static final Object CAN_NOT_UNWRAP = new Object();
    private static int defaultDateType = WrappedDate.UNKNOWN;

    private Wrap() {}

    public static boolean isMap(Object obj) {
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        return obj instanceof Map;
    }

    public static boolean isList(Object obj) {
        if (obj instanceof WrappedSequence) {
            return true;
        }
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        if (obj.getClass().isArray()) {
            return true;
        }
        return obj instanceof List;
    }

    public static List<?> asList(Object obj) {
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        if (obj instanceof WrappedSequence) {
            WrappedSequence tsm = (WrappedSequence) obj;
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < tsm.size(); i++)
                result.add(tsm.get(i));
            return result;
        }
        if (obj.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < Array.getLength(obj); i++) {
                result.add(Array.get(obj, i));
            }
            return result;
        }
        return (List<?>) obj;
    }

    public static boolean isNumber(Object obj) {
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        if (obj instanceof Number) {
            return true;
        }
        return false;
    }

    public static boolean isDate(Object obj) {
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        if (obj instanceof Date) {
            return true;
        }
        if (obj instanceof WrappedDate) {
            return true;
        }
        return false;
    }

    public static Number asNumber(Object obj) {
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        return (Number) obj;
    }

    public static Date asDate(Object obj) {
        if (obj instanceof WrappedDate) {
            return ((WrappedDate) obj).getAsDate();
        }
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        return (Date) obj;
    }

    public static boolean isString(Object obj) {
        if (obj instanceof WrappedString) {
            return true;
        }
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        return obj instanceof CharSequence;
    }

    public static String asString(Object obj) {
        if (obj instanceof WrappedString) {
            return ((WrappedString) obj).getAsString();
        }
        return obj.toString();
    }

    public static boolean isDisplayableAsString(Object tm) {
        return isString(tm)
                || tm instanceof Pojo
                || isNumber(tm)
                || tm instanceof WrappedDate;
    }

    public static boolean isBoolean(Object obj) {
        if (obj instanceof WrappedBoolean) {
            return true;
        }
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        return obj instanceof Boolean;
    }

    public static boolean asBoolean(Object obj) {
        if (obj instanceof WrappedBoolean) {
            return ((WrappedBoolean) obj).getAsBoolean();
        }
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        return (Boolean) obj;
    }

    public static boolean isIterable(Object obj) {
        if (obj instanceof WrappedSequence)
            return true;
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        if (obj.getClass().isArray())
            return true;
        return obj instanceof Iterable || obj instanceof Iterator;
    }

    public static Iterator<?> asIterator(Object obj) {
        if (obj instanceof Pojo) {
            obj = ((Pojo) obj).getWrappedObject();
        }
        if (obj instanceof Iterator) {
            return (Iterator<?>) obj;
        }
        if (obj.getClass().isArray()) {
            final Object arr = obj;
            return new Iterator<Object>() {
                int index = 0;

                public boolean hasNext() {
                    return index < Array.getLength(arr);
                }

                public Object next() {
                    return Array.get(arr, index++);
                }
            };
        }
        if (obj instanceof WrappedSequence) {
            final WrappedSequence seq = (WrappedSequence) obj;
            return new Iterator<Object>() {
                int index = 0;

                public boolean hasNext() {
                    return index < seq.size();
                }

                public Object next() {
                    return seq.get(index++);
                }
            };
        }
        return ((Iterable<?>) obj).iterator();
    }

    /**
     * Sets the default date type to use for date models that result from
     * a plain <tt>java.util.Date</tt> instead of <tt>java.sql.Date</tt> or
     * <tt>java.sql.Time</tt> or <tt>java.sql.Timestamp</tt>. Default value is
     * {@link WrappedDate#UNKNOWN}.
     * 
     * @param defaultDateType the new default date type.
     */
    public static synchronized void setDefaultDateType(int defaultDateType) {
        Wrap.defaultDateType = defaultDateType;
    }

    static synchronized int getDefaultDateType() {
        return defaultDateType;
    }

    public static Object wrap(Object object) {
        if (object == null) {
            return Constants.JAVA_NULL;
        }
        if (isMarkedAsPojo(object.getClass())) {
            return new Pojo(object);
        }
        if (object instanceof WrappedVariable
                || object instanceof Pojo
                || object instanceof Scope
                || object instanceof Boolean
                || object instanceof Number
                || object instanceof String
                || object instanceof Iterator
                || object instanceof Enumeration) {
            return object;
        }
        if (object instanceof CharSequence) {
            return object.toString(); // REVISIT
        }
        if (object instanceof List) {
            // return object;
            return new Pojo(object);
        }
        if (object.getClass().isArray()) {
            return new Pojo(object);
        }
        if (object instanceof Map) {
            return object;
        }
        if (object instanceof Date) {
            return new DateModel((Date) object);
        }
        if (object instanceof ResourceBundle) {
            return new ResourceBundleModel((ResourceBundle) object);
        }
        return new Pojo(object);
    }

    private static Map<Class<?>, Boolean> markedAsPojoLookup = new HashMap<>();

    private static boolean isMarkedAsPojo(Class<?> clazz) {
        Boolean lookupValue = markedAsPojoLookup.get(clazz);
        if (lookupValue != null)
            return lookupValue;
        if (clazz.getAnnotation(freemarker.annotations.Pojo.class) != null) {
            markedAsPojoLookup.put(clazz, true);
            return true;
        }
        for (Class<?> interf : clazz.getInterfaces()) {
            if (isMarkedAsPojo(interf)) {
                markedAsPojoLookup.put(clazz, true);
                return true;
            }
        }
        if (clazz.getSuperclass() != null) {
            lookupValue = isMarkedAsPojo(clazz.getSuperclass());
        } else {
            lookupValue = false;
        }
        markedAsPojoLookup.put(clazz, lookupValue);
        return lookupValue;
    }

    public static Object unwrap(Object object) {
        if (object == null) {
            throw new EvaluationException("invalid reference");
        }
        if (object == Constants.JAVA_NULL) {
            return null;
        }
        if (object instanceof Pojo) {
            return ((Pojo) object).getWrappedObject();
        }
        return object;
    }

    public static Object unwrap(Object object, Class<?> desiredType) {
        if (object == null) {
            return null;
        }
        object = unwrap(object);
        if (desiredType.isInstance(object)) {
            return object;
        }
        if (desiredType == String.class) {
            return object.toString();
        }
        if (desiredType == Boolean.TYPE || desiredType == Boolean.class) {
            if (object instanceof Boolean) {
                return (Boolean) object;
            }
            if (object instanceof WrappedBoolean) {
                return ((WrappedBoolean) object).getAsBoolean() ? Boolean.TRUE : Boolean.FALSE;
            }
            return CAN_NOT_UNWRAP;
        }
        if (object instanceof Number) {
            Number num = (Number) object;
            if (desiredType == Integer.class || desiredType == Integer.TYPE) {
                return num.intValue();
            }
            if (desiredType == Long.class || desiredType == Long.TYPE) {
                return num.longValue();
            }
            if (desiredType == Short.class || desiredType == Short.TYPE) {
                return num.shortValue();
            }
            if (desiredType == Byte.class || desiredType == Byte.TYPE) {
                return num.byteValue();
            }
            if (desiredType == Float.class || desiredType == Float.TYPE) {
                return num.floatValue();
            }
            if (desiredType == Double.class || desiredType == Double.TYPE) {
                return num.doubleValue();
            }
            if (desiredType == BigDecimal.class) {
                return new BigDecimal(num.toString());
            }
            if (desiredType == BigInteger.class) {
                return new BigInteger(num.toString());
            }
        }
        if (desiredType == Date.class && object instanceof WrappedDate) {
            // REVISIT
            return ((WrappedDate) object).getAsDate();
        }
        return CAN_NOT_UNWRAP;
    }
}