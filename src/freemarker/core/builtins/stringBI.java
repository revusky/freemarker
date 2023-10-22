package freemarker.core.builtins;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;

import freemarker.annotations.Parameters;
import freemarker.core.Environment;
import freemarker.core.nodes.generated.BuiltInExpression;
import freemarker.core.EvaluationUtil;
import freemarker.core.nodes.generated.TemplateNode;
import freemarker.template.*;

import static freemarker.ext.beans.ObjectWrapper.*;

/**
 * Implementation of ?string built-in 
 */

public class stringBI extends ExpressionEvaluatingBuiltIn {
	
    @Override
    public Object get(Environment env, BuiltInExpression caller,
        Object model) 
    {
        if (isNumber(model)) {
            return new NumberFormatter(asNumber(model), env);
        }
        if (model instanceof TemplateDateModel) {
            TemplateDateModel dm = (TemplateDateModel) model;
            int dateType = dm.getDateType();
            return new DateFormatter(EvaluationUtil.getDate(dm, caller.getTarget(), env), dateType, env);
        }
        if (isBoolean(model)) {
            return new BooleanFormatter(model, env);
        }
        if (isString(model)) {
            return asString(model);
        } 
      	throw TemplateNode.invalidTypeException(model, caller.getTarget(), env, "number, date, or string");
    }
	
	
    static class BooleanFormatter
    implements TemplateScalarModel, LazilyEvaluatableArguments  
    {
        private final Object bool;
        private final Environment env;
        
        BooleanFormatter(Object bool, Environment env) {
            this.bool = bool;
            this.env = env;
        }

        public String getAsString() {
            if (isString(bool)) {
                return (asString(bool));
            } else {
                return env.getBooleanFormat(asBoolean(bool));
            }
        }

        public Object exec(List arguments)
                {
            if (arguments.size() != 2) {
                throw new TemplateModelException(
                        "boolean?string(...) requires exactly "
                        + "2 arguments.");
            }
            return asString(arguments.get(asBoolean(bool) ? 0 : 1));
        }
    }
    
    
    static class DateFormatter
    implements TemplateScalarModel, TemplateHashModel, TemplateMethodModel {
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

        public String getAsString() { 
            if(dateType == TemplateDateModel.UNKNOWN) {
                throw new TemplateModelException("Can't convert the date to string, because it is not known which parts of the date variable are in use. Use ?date, ?time or ?datetime built-in, or ?string.<format> or ?string(format) built-in with this date.");
            }
            return defaultFormat.format(date);
        }

        public Object get(String key) 
        {
            return env.getDateFormatObject(dateType, key).format(date);
        }
        
        public Object exec(List arguments)
            {
            if (arguments.size() != 1) {
                throw new TemplateModelException(
                        "date?string(...) requires exactly 1 argument.");
            }
            return get((String) arguments.get(0));
        }

        public boolean isEmpty()
        {
            return false;
        }
    }
    
    static class NumberFormatter implements TemplateScalarModel, TemplateHashModel, TemplateMethodModel {
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

        public String getAsString()
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
        public Object exec(List arguments) {
            if (arguments.size() != 1) {
                throw new TemplateModelException(
                        "number?string(...) requires exactly 1 argument.");
            }
            return get(asString(arguments.get(0)));
        }

        public boolean isEmpty()
        {
            return false;
        }
    }
}

	
