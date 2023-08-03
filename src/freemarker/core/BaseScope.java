package freemarker.core;

import java.util.*;

import freemarker.template.*;

/**
 * A basic scope that stores variables locally in a hash map. 
 * @author Jonathan Revusky
 * @version $Id: $
 */
public class BaseScope extends AbstractScope {

    private HashMap<String,Object> variables = new HashMap<>();

    BaseScope(Scope enclosingScope) {
        super(enclosingScope);
    }

    public void put(String key, Object value) {
        variables.put(key, value);
    }

    public Object remove(String key) {
        return variables.remove(key);
    }

    public Object get(String key) { 
        return variables.get(key);
    }

    public boolean definesVariable(String key) {
        return variables.containsKey(key);
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    public TemplateCollectionModel keys() {
        return new SimpleCollection(variables.keySet(), TRIVIAL_WRAPPER);
    }

    public TemplateCollectionModel values() {
        return new SimpleCollection(variables.values(), TRIVIAL_WRAPPER);
    }


    public int size() {
        return variables.size();
    }

    public void clear() {
        variables.clear();
    }

    public Collection<String> getDirectVariableNames() {
        return Collections.unmodifiableCollection(variables.keySet());
    }

    // An object wrapper where everything is known to be either a string or already a TemplateModel

    static ObjectWrapper TRIVIAL_WRAPPER = new ObjectWrapper() {
        public TemplateModel wrap(Object obj) {
            if (obj instanceof String) {
                return new SimpleScalar((String) obj);
            }
            return (TemplateModel) obj;
        }
    };
}
