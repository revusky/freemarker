package freemarker3.builtins;

import freemarker3.core.Environment;
import freemarker3.core.nodes.generated.BuiltInExpression;
import freemarker3.core.nodes.generated.Expression;
import freemarker3.core.parser.FMLexer;
import freemarker3.core.parser.FMParser;
import freemarker3.core.parser.ParseException;
import freemarker3.template.TemplateException;
import freemarker3.core.variables.EvaluationException;

import static freemarker3.core.variables.Wrap.asString;

/**
 * Implementation of ?eval built-in 
 */

public class evalBI extends ExpressionEvaluatingBuiltIn {

    @Override
    public Object get(Environment env, BuiltInExpression caller, Object model) 
    {
        try {
            return eval(asString(model), env, caller);
        } catch (ClassCastException cce) {
            throw new EvaluationException("Expecting string on left of ?eval built-in");

        } catch (NullPointerException npe) {
            throw new EvaluationException(npe);
        }
    }

    Object eval(String s, Environment env, BuiltInExpression caller) 
    {
        String input = "(" + s + ")";
        FMLexer token_source= new FMLexer("input", input, FMLexer.LexicalState.EXPRESSION, caller.getBeginLine(), caller.getBeginColumn());;
        FMParser parser = new FMParser(token_source);
        parser.setTemplate(caller.getTemplate());
        Expression exp = null;
        try {
            exp = parser.Expression();
        } catch (ParseException pe) {
            pe.setTemplateName(caller.getTemplate().getName());
            throw new TemplateException(pe, env);
        }
        return exp.evaluate(env);
    }
}