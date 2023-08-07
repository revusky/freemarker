package freemarker.core.ast;

import freemarker.template.*;
import freemarker.ext.beans.ObjectWrapper;
import freemarker.ext.beans.Pojo;
import freemarker.core.Environment;
import freemarker.core.parser.ast.TemplateNode;

/**
 * An abstract class for nodes in the parse tree 
 * that represent a FreeMarker expression.
 */
abstract public class Expression extends TemplateNode {

    public abstract Object getAsTemplateModel(Environment env);
    
    
    public String getDescription() {
    	return "the expression: "  + this;
    }

    public String getStringValue(Environment env) {
        return getStringValue(getAsTemplateModel(env), this, env);
    }
    
    static public boolean isDisplayableAsString(Object tm) {
    	return ObjectWrapper.isString(tm)
    	     ||tm instanceof TemplateNumberModel
             || tm instanceof Number
    	     || tm instanceof TemplateDateModel;
    }
    
    public static String getStringValue(Object referent, Expression exp, Environment env)
    {
        if (referent instanceof Number) {
            return env.formatNumber((Number) referent);
        }
        if (referent instanceof TemplateNumberModel) {
            return env.formatNumber(EvaluationUtil.getNumber((TemplateNumberModel) referent, exp, env));
        }
        if (referent instanceof TemplateDateModel) {
            TemplateDateModel dm = (TemplateDateModel) referent;
            return env.formatDate(EvaluationUtil.getDate(dm, exp, env), dm.getDateType());
        }
        if (referent instanceof TemplateScalarModel) {
            return EvaluationUtil.getString((TemplateScalarModel) referent, exp, env);
        }
        assertNonNull(referent, exp, env);
        String msg = "Error " + exp.getStartLocation()
                     +"\nExpecting a string, " 
                     + "date or number here, Expression " + exp 
                     + " is instead a " 
                     + referent.getClass().getName();
        throw new NonStringException(msg, env);
    }

    public Expression deepClone(String name, Expression subst) {
        Expression clone = _deepClone(name, subst);
        clone.copyLocationFrom(this);
        clone.setParent(this.getParent());
        return clone;
    }

    public abstract Expression _deepClone(String name, Expression subst);

    public boolean isTrue(Environment env) {
        Object referent = getAsTemplateModel(env);
        if (referent instanceof Boolean) {
            return ((Boolean) referent);
        }
        if (referent instanceof TemplateBooleanModel) {
            return ((TemplateBooleanModel) referent).getAsBoolean();
        }
        assertNonNull(referent, this, env);
        String msg = "Error " + getStartLocation()
                     + "\nExpecting a boolean (true/false) expression here"
                     + "\nExpression " + this + " does not evaluate to true/false "
                     + "\nit is an instance of " + referent.getClass().getName();
        throw new NonBooleanException(msg, env);
    }

	static public boolean isEmpty(Object model) 
    {
        if (model instanceof Pojo) {
            return ((Pojo) model).isEmpty();
        } else if (model instanceof TemplateSequenceModel) {
            return ((TemplateSequenceModel) model).size() == 0;
        } else if (model instanceof TemplateScalarModel) {
            String s = ((TemplateScalarModel) model).getAsString();
            return (s == null || s.length() == 0);
        } else if (model instanceof TemplateCollectionModel) {
            return !((TemplateCollectionModel) model).iterator().hasNext();
        } else if (model instanceof TemplateHashModel) {
            return ((TemplateHashModel) model).isEmpty();
        } else if (model instanceof TemplateNumberModel
                || model instanceof TemplateDateModel
                || model instanceof TemplateBooleanModel) {
            return false;
        } else {
            return true;
        }
    }
}
