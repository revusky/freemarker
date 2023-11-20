
package freemarker.core.variables;

/*
 * 22 October 1999: This class added by Holger Arendt.
 */
import java.util.List;

/**
 * Objects that act as methods in a template data model implement this 
 * interface.
 * @version $Id: WrappedMethod.java,v 1.11 2003/09/22 23:56:54 revusky Exp $
 */
public interface WrappedMethod {

    /**
     * Executes a method call. 
     */
    public Object exec(List<Object> arguments);
}
