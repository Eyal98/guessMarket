package gm.engine.xml;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A small, forgiving view over one DOM element.
 * <p>
 * Two habits are built in and apply everywhere the loader looks at the file. Element and attribute
 * names are matched without regard to letter case, which costs nothing and removes a whole class of
 * "the file did not load" failures caused by nothing more than capitalisation. Text is returned
 * trimmed, because the file format says surrounding spaces are to be ignored.
 * <p>
 * Anything missing comes back as an empty optional rather than {@code null}, so the loader has to
 * decide what to say about it.
 */
final class XmlNode {

    private final Element element;

    XmlNode(Element element) {
        this.element = element;
    }

    /** The element's name, exactly as it is written in the file. */
    String name() {
        return element.getNodeName();
    }

    boolean isNamed(String name) {
        return element.getNodeName().equalsIgnoreCase(name);
    }

    /** Every direct child with the given name, in document order. */
    List<XmlNode> children(String name) {
        List<XmlNode> found = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child && child.getNodeName().equalsIgnoreCase(name)) {
                found.add(new XmlNode(child));
            }
        }
        return found;
    }

    /** The first direct child with the given name. */
    Optional<XmlNode> child(String name) {
        List<XmlNode> found = children(name);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** This element's own text, trimmed. */
    Optional<String> text() {
        String content = element.getTextContent();
        return content == null ? Optional.empty() : Optional.of(content.trim());
    }

    /** The trimmed text of the first child with the given name, empty if there is no such child. */
    Optional<String> childText(String name) {
        return child(name).flatMap(XmlNode::text);
    }

    /** The trimmed value of an attribute, empty if the element does not carry it. */
    Optional<String> attribute(String name) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (attribute.getNodeName().equalsIgnoreCase(name)) {
                return Optional.of(attribute.getNodeValue().trim());
            }
        }
        return Optional.empty();
    }
}
