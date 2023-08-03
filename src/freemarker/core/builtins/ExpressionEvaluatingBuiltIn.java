package freemarker.core.builtins;

import freemarker.core.Environment;
import freemarker.core.ast.BuiltInExpression;

/**
 * @author Attila Szegedi
 * @version $Id: $
 */
public abstract class ExpressionEvaluatingBuiltIn extends BuiltIn {

    @Override
    public Object get(Environment env, BuiltInExpression caller) 
    {
        return get(env, caller, caller.getTarget().getAsTemplateModel(env));
    }
    
    public abstract Object get(Environment env, BuiltInExpression caller, 
            Object model);
}
