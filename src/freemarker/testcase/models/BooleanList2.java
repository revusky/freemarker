package freemarker.testcase.models;

import freemarker.template.*;

/**
 * Model for testing list models. Every
 * other method simply delegates to a SimpleList model.
 *
 * @author  <a href="mailto:run2000@users.sourceforge.net">Nicholas Cull</a>
 * @version $Id: BooleanList2.java,v 1.12 2003/01/12 23:40:25 revusky Exp $
 */
public class BooleanList2 implements TemplateSequenceModel {

    private LegacyList  cList;

    /** Creates new BooleanList2 */
    public BooleanList2() {
        cList = new LegacyList();
    }

    /**
     * @return the specified index in the list
     */
    public Object get(int i) {
        return cList.get(i);
    }

    public int size() {
        return cList.size();
    }
}
