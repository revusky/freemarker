
package freemarker3.xml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import freemarker3.log.Logger;
import freemarker3.template.TemplateHashModel;
import freemarker3.template.TemplateSequenceModel;
import freemarker3.core.variables.*;

/**
 * A base class for wrapping a W3C DOM Node as a FreeMarker template model.
 * 
 * @author <a href="mailto:jon@revusky.com">Jonathan Revusky</a>
 * @version $Id: NodeModel.java,v 1.80 2005/06/22 11:33:31 ddekany Exp $
 */
abstract public class WrappedDomNode implements WrappedNode, TemplateHashModel, TemplateSequenceModel {

    static final Logger logger = Logger.getLogger("freemarker3.dom");

    static private ErrorHandler errorHandler = new ErrorHandler() {

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            if (logger.isWarnEnabled()) {
                logger.warn(exception.getMessage(), exception);
            }
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            if (logger.isErrorEnabled()) {
                logger.error(exception.getMessage(), exception);
            }
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            error(exception);
            throw exception;
        }
    };

    static private DocumentBuilderFactory docBuilderFactory;

    /**
     * The W3C DOM Node being wrapped.
     */
    final Node node;
    private NodeList children;
    private WrappedDomNode parent;

    /**
     * Sets the DOM Parser implementation to be used when building NodeModel
     * objects from XML files.
     */
    static public void setDocumentBuilderFactory(DocumentBuilderFactory docBuilderFactory) {
        WrappedDomNode.docBuilderFactory = docBuilderFactory;
    }

    /**
     * @return the DOM Parser implementation that is used when
     *         building NodeModel objects from XML files.
     */
    static public DocumentBuilderFactory getDocumentBuilderFactory() {
        if (docBuilderFactory == null) {
            docBuilderFactory = DocumentBuilderFactory.newInstance();
            docBuilderFactory.setNamespaceAware(true);
            docBuilderFactory.setIgnoringElementContentWhitespace(true);
        }
        return docBuilderFactory;
    }

    /**
     * sets the error handler to use when parsing the document.
     */
    static public void setErrorHandler(ErrorHandler errorHandler) {
        WrappedDomNode.errorHandler = errorHandler;
    }

    /**
     * Create a NodeModel from a SAX input source. Adjacent text nodes will be
     * merged (and CDATA sections
     * are considered as text nodes).
     * 
     * @param removeComments whether to remove all comment nodes
     *                       (recursively) from the tree before processing
     * @param removePIs      whether to remove all processing instruction nodes
     *                       (recursively from the tree before processing
     */
    static public WrappedDomNode parse(InputSource is, boolean removeComments, boolean removePIs)
            throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilder builder = getDocumentBuilderFactory().newDocumentBuilder();
        if (errorHandler != null)
            builder.setErrorHandler(errorHandler);
        Document doc = builder.parse(is);
        if (removeComments && removePIs) {
            simplify(doc);
        } else {
            if (removeComments) {
                removeComments(doc);
            }
            if (removePIs) {
                removePIs(doc);
            }
            mergeAdjacentText(doc);
        }
        return wrapNode(doc);
    }

    /**
     * Create a NodeModel from an XML input source. By default,
     * all comments and processing instruction nodes are
     * stripped from the tree.
     */
    static public WrappedDomNode parse(InputSource is)
            throws SAXException, IOException, ParserConfigurationException {
        return parse(is, true, true);
    }

    /**
     * Create a NodeModel from an XML file.
     * 
     * @param removeComments whether to remove all comment nodes
     *                       (recursively) from the tree before processing
     * @param removePIs      whether to remove all processing instruction nodes
     *                       (recursively from the tree before processing
     */
    static public WrappedDomNode parse(File f, boolean removeComments, boolean removePIs)
            throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilder builder = getDocumentBuilderFactory().newDocumentBuilder();
        if (errorHandler != null)
            builder.setErrorHandler(errorHandler);
        Document doc = builder.parse(f);
        if (removeComments) {
            removeComments(doc);
        }
        if (removePIs) {
            removePIs(doc);
        }
        mergeAdjacentText(doc);
        return wrapNode(doc);
    }

    /**
     * Create a NodeModel from an XML file. By default,
     * all comments and processing instruction nodes are
     * stripped from the tree.
     */
    static public WrappedDomNode parse(File f)
            throws SAXException, IOException, ParserConfigurationException {
        return parse(f, true, true);
    }

    protected WrappedDomNode(Node node) {
        this.node = node;
    }

    /**
     * @return the underlying W3C DOM Node object that this WrappedNode
     *         is wrapping.
     */
    @Override
    public Node getWrappedObject() {
        return node;
    }

    public Object get(String key) {
        if (key.startsWith("@@")) {
            if (key.equals("@@text")) {
                return getText(node);
            }
            if (key.equals("@@namespace")) {
                String nsURI = node.getNamespaceURI();
                return nsURI == null ? null : nsURI;
            }
            if (key.equals("@@local_name")) {
                String localName = node.getLocalName();
                if (localName == null) {
                    localName = getNodeName();
                }
                return localName;
            }
            if (key.equals("@@markup")) {
                StringBuilder buf = new StringBuilder();
                NodeOutputter nu = new NodeOutputter(node);
                nu.outputContent(node, buf);
                return buf.toString();
            }
            if (key.equals("@@nested_markup")) {
                StringBuilder buf = new StringBuilder();
                NodeOutputter nu = new NodeOutputter(node);
                nu.outputContent(node.getChildNodes(), buf);
                return buf.toString();
            }
            if (key.equals("@@qname")) {
                String qname = getQualifiedName();
                return qname == null ? null : qname;
            }
        }
        XPathSupport xps = getXPathSupport();
        if (xps != null) {
            return xps.executeQuery(node, key);
        } else {
            throw new EvaluationException(
                    "Can't try to resolve the XML query key, because no XPath support is available. "
                            + "It's either malformed or an XPath expression: " + key);
        }
    }

    public WrappedNode getParentNode() {
        if (parent == null) {
            Node parentNode = node.getParentNode();
            if (parentNode == null) {
                if (node instanceof Attr) {
                    parentNode = ((Attr) node).getOwnerElement();
                }
            }
            parent = wrapNode(parentNode);
        }
        return parent;
    }

    public List<WrappedNode> getChildNodes() {
        if (children == null) {
            children = node.getChildNodes();
        }
        List<WrappedNode> result = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            result.add(wrapNode(children.item(i)));
        }
        return result;
    }

    public final String getNodeType() {
        short nodeType = node.getNodeType();
        switch (nodeType) {
            case Node.ATTRIBUTE_NODE:
                return "attribute";
            case Node.CDATA_SECTION_NODE:
                return "text";
            case Node.COMMENT_NODE:
                return "comment";
            case Node.DOCUMENT_FRAGMENT_NODE:
                return "document_fragment";
            case Node.DOCUMENT_NODE:
                return "document";
            case Node.DOCUMENT_TYPE_NODE:
                return "document_type";
            case Node.ELEMENT_NODE:
                return "element";
            case Node.ENTITY_NODE:
                return "entity";
            case Node.ENTITY_REFERENCE_NODE:
                return "entity_reference";
            case Node.NOTATION_NODE:
                return "notation";
            case Node.PROCESSING_INSTRUCTION_NODE:
                return "pi";
            case Node.TEXT_NODE:
                return "text";
        }
        throw new EvaluationException("Unknown node type: " + nodeType + ". This should be impossible!");
    }

    public Object exec(List<Object> args) {
        if (args.size() != 1) {
            throw new EvaluationException("Expecting exactly one arguments");
        }
        String query = (String) args.get(0);
        // Now, we try to behave as if this is an XPath expression
        XPathSupport xps = getXPathSupport();
        if (xps == null) {
            throw new EvaluationException("No XPath support available");
        }
        return xps.executeQuery(node, query);
    }

    public final int size() {
        return 1;
    }

    public final Object get(int i) {
        return i == 0 ? this : null;
    }

    public String getNodeNamespace() {
        int nodeType = node.getNodeType();
        if (nodeType != Node.ATTRIBUTE_NODE && nodeType != Node.ELEMENT_NODE) {
            return null;
        }
        String result = node.getNamespaceURI();
        if (result == null && nodeType == Node.ELEMENT_NODE) {
            result = "";
        } else if ("".equals(result) && nodeType == Node.ATTRIBUTE_NODE) {
            result = null;
        }
        return result;
    }

    public final int hashCode() {
        return node.hashCode();
    }

    public boolean equals(Object other) {
        if (other == null)
            return false;
        return other.getClass() == this.getClass()
                && ((WrappedDomNode) other).node.equals(this.node);
    }

    static public WrappedDomNode wrapNode(Node node) {
        if (node == null) {
            return null;
        }
        WrappedDomNode result = null;
        switch (node.getNodeType()) {
            case Node.DOCUMENT_NODE:
                result = new DocumentModel((Document) node);
                break;
            case Node.ELEMENT_NODE:
                result = new ElementModel((Element) node);
                break;
            case Node.ATTRIBUTE_NODE:
                result = new AttributeNodeModel((Attr) node);
                break;
            case Node.CDATA_SECTION_NODE:
            case Node.COMMENT_NODE:
            case Node.TEXT_NODE:
                result = new CharacterDataNodeModel((org.w3c.dom.CharacterData) node);
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                result = new PINodeModel((ProcessingInstruction) node);
                break;
            case Node.DOCUMENT_TYPE_NODE:
                result = new DocumentTypeModel((DocumentType) node);
                break;
        }
        return result;
    }

    /**
     * Recursively removes all comment nodes
     * from the subtree.
     *
     * @see #simplify
     */
    static public void removeComments(Node node) {
        NodeList children = node.getChildNodes();
        int i = 0;
        int len = children.getLength();
        while (i < len) {
            Node child = children.item(i);
            if (child.hasChildNodes()) {
                removeComments(child);
                i++;
            } else {
                if (child.getNodeType() == Node.COMMENT_NODE) {
                    node.removeChild(child);
                    len--;
                } else {
                    i++;
                }
            }
        }
    }

    /**
     * Recursively removes all processing instruction nodes
     * from the subtree.
     *
     * @see #simplify
     */
    static public void removePIs(Node node) {
        NodeList children = node.getChildNodes();
        int i = 0;
        int len = children.getLength();
        while (i < len) {
            Node child = children.item(i);
            if (child.hasChildNodes()) {
                removePIs(child);
                i++;
            } else {
                if (child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                    node.removeChild(child);
                    len--;
                } else {
                    i++;
                }
            }
        }
    }

    /**
     * Merges adjacent text/cdata nodes, so that there are no
     * adjacent text/cdata nodes. Operates recursively
     * on the entire subtree. You thus lose information
     * about any CDATA sections occurring in the doc.
     *
     * @see #simplify
     */
    static public void mergeAdjacentText(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Text || child instanceof CDATASection) {
                Node next = child.getNextSibling();
                if (next instanceof Text || next instanceof CDATASection) {
                    String fullText = child.getNodeValue() + next.getNodeValue();
                    ((CharacterData) child).setData(fullText);
                    node.removeChild(next);
                }
            } else {
                mergeAdjacentText(child);
            }
            child = child.getNextSibling();
        }
    }

    /**
     * Removes comments and processing instruction, and then unites adjacent text
     * nodes.
     * Note that CDATA sections count as text nodes.
     */
    static public void simplify(Node node) {
        NodeList children = node.getChildNodes();
        int i = 0;
        int len = children.getLength();
        Node prevTextChild = null;
        while (i < len) {
            Node child = children.item(i);
            if (child.hasChildNodes()) {
                simplify(child);
                prevTextChild = null;
                i++;
            } else {
                int type = child.getNodeType();
                if (type == Node.PROCESSING_INSTRUCTION_NODE) {
                    node.removeChild(child);
                    len--;
                } else if (type == Node.COMMENT_NODE) {
                    node.removeChild(child);
                    len--;
                } else if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
                    if (prevTextChild != null) {
                        CharacterData ptc = (CharacterData) prevTextChild;
                        ptc.setData(ptc.getNodeValue() + child.getNodeValue());
                        node.removeChild(child);
                        len--;
                    } else {
                        prevTextChild = child;
                        i++;
                    }
                } else {
                    prevTextChild = null;
                    i++;
                }
            }
        }
    }

    WrappedDomNode getDocumentNodeModel() {
        if (node instanceof Document) {
            return this;
        } else {
            return wrapNode(node.getOwnerDocument());
        }
    }

    static private String getText(Node node) {
        if (node instanceof Text || node instanceof CDATASection) {
            return ((org.w3c.dom.CharacterData) node).getData();
        } else if (node instanceof Element) {
            StringBuilder result = new StringBuilder();
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                result.append(getText(children.item(i)));
            }
            return result.toString();
        } else if (node instanceof Document) {
            return getText(((Document) node).getDocumentElement());
        } else {
            return "";
        }
    }

    XPathSupport getXPathSupport() {
        return DefaultXPathSupport.instance;
    }

    String getQualifiedName() {
        return getNodeName();
    }
}
