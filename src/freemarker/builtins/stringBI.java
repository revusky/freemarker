package freemarker.builtins;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

import freemarker.annotations.Parameters;
import freemarker.core.Environment;
import freemarker.core.nodes.generated.BuiltInExpression;
import freemarker.core.variables.*;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateHashModel;

import static freemarker.core.variables.Wrap.*;

/**
 * Implementation of ?string built-in 
 */

public class stringBI extends ExpressionEvaluatingBuiltIn {
	
    @Override
    public Object get(Environment env, BuiltInExpression caller,
        Object model) 
    {
        if (model instanceof Number) {
            return new NumberFormatter((Number)model, env);
        }
        if (model instanceof TemplateDateModel) {
            TemplateDateModel dm = (TemplateDateModel) model;
            int dateType = dm.getDateType();
            return new DateFormatter(Wrap.getDate(dm, caller.getTarget(), env), dateType, env);
        }
        if (isBoolean(model)) {
            return new BooleanFormatter(model, env);
        }
        return model.toString();
    }
	
	
    static class BooleanFormatter implements VarArgsFunction  
    {
        private final Object bool;
        private final Environment env;
        
        BooleanFormatter(Object bool, Environment env) {
            this.bool = bool;
            this.env = env;
        }

        public String toString() {
            if (isString(bool)) {
                return (asString(bool));
            } else {
                return env.getBooleanFormat(asBoolean(bool));
            }
        }

        public Object apply(Object... arguments)
                {
            if (arguments.length != 2) {
                throw new EvaluationException(
                        "boolean?string(...) requires exactly "
                        + "2 arguments.");
            }
            return asString(arguments[asBoolean(bool) ? 0 : 1]);
        }
    }
    
    
    static class DateFormatter implements TemplateHashModel, VarArgsFunction {
        private final Date date;
        private final int dateType;
        private final Environment env;
        private final DateFormat defaultFormat;

        DateFormatter(Date date, int dateType, Environment env) {
            this.date = date;
            this.dateType = dateType;
            this.env = env;
            defaultFormat = env.getDateFormatObject(dateType);
        }

        public String toString() { 
            if(dateType == TemplateDateModel.UNKNOWN) {
                throw new EvaluationException("Can't convert the date to string, because it is not known which parts of the date variable are in use. Use ?date, ?time or ?datetime built-in, or ?string.<format> or ?string(format) built-in with this date.");
            }
            return defaultFormat.format(date);
        }

        public Object get(String key) 
        {
            return env.getDateFormatObject(dateType, key).format(date);
        }
        
        public Object apply(Object... arguments)
            {
            if (arguments.length != 1) {
                throw new EvaluationException(
                        "date?string(...) requires exactly 1 argument.");
            }
            return get((String) arguments[0]);
        }

        public boolean isEmpty()
        {
            return false;
        }
    }
    
    static class NumberFormatter implements TemplateHashModel, VarArgsFunction {
        private final Number number;
        private final Environment env;
        private final NumberFormat defaultFormat;
        private String cachedValue;

        NumberFormatter(Number number, Environment env)
        {
            this.number = number;
            this.env = env;
            defaultFormat = env.getNumberFormatObject(env.getNumberFormat());
        }

        public String toString()
        {
            if(cachedValue == null) {
                cachedValue = defaultFormat.format(number);
            }
            return cachedValue;
        }

        public Object get(String key)
        {
            return env.getNumberFormatObject(key).format(number);
        }
        
        @Parameters("format")
        public Object apply(Object... arguments) {
            if (arguments.length != 1) {
                throw new EvaluationException(
                        "number?string(...) requires exactly 1 argument.");
            }
            return get(asString(arguments[0]));
        }

        public boolean isEmpty()
        {
            return false;
        }
    }
}

	
