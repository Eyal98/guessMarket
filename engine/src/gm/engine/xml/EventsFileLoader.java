package gm.engine.xml;

import gm.engine.api.FileLoadException;
import gm.engine.model.Event;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads an uploaded events file and turns it into events, or explains exactly why it cannot.
 * <p>
 * The file never exists on the server. It arrives as the bytes of an upload and is read straight from
 * them, which is what the exercise demands: the checker's server has no permission to write anywhere.
 * <p>
 * Checking happens in two passes. Faults that make the rest of the file meaningless, such as text that
 * is not XML at all, stop at once. Everything else is gathered: each event is examined in full and
 * every fault recorded, so a single upload reports all of them together.
 * <p>
 * Nothing here touches the market. The caller receives events only when the whole file was sound, and
 * decides for itself who runs them, which is what lets a faulty file change nothing at all.
 */
public final class EventsFileLoader {

    private static final String XML_EXTENSION = ".xml";
    private static final String ROOT_ELEMENT = "Guess-Market";
    private static final String EVENTS_ELEMENT = "GM-events";
    private static final String EVENT_ELEMENT = "GM-event";
    /** Exercise 2's list of users, which exercise 3 files no longer carry. */
    private static final String USERS_ELEMENT = "GM-users";

    /**
     * Reads one uploaded file.
     *
     * @param fileName         the name the file had on the uploader's computer, which must end in .xml
     * @param content          the file's bytes
     * @param nameAlreadyTaken whether the market already holds an event of a given name
     * @return the events it describes, in the order they appear, not yet run by anybody
     * @throws FileLoadException if there is no file, or it does not describe a sound set of new events
     */
    public List<Event> read(String fileName, InputStream content, Predicate<String> nameAlreadyTaken) {
        requireXmlFile(fileName, content);
        XmlNode root = new XmlNode(parse(content).getDocumentElement());
        if (!root.isNamed(ROOT_ELEMENT)) {
            throw new FileLoadException("this is not a Guess Market file: its root element is <" + root.name()
                    + "> instead of <" + ROOT_ELEMENT + ">.");
        }
        if (root.child(USERS_ELEMENT).isPresent()) {
            throw new FileLoadException("it has a <" + USERS_ELEMENT + "> element, which belongs to the files"
                    + " of exercise 2. In this version nobody arrives in a file: people log in by name, and"
                    + " whoever uploads a file becomes the market maker of every event in it.");
        }
        return readEvents(root, nameAlreadyTaken);
    }

    private static void requireXmlFile(String fileName, InputStream content) {
        if (fileName == null || fileName.isBlank() || content == null) {
            throw new FileLoadException("there is no file in the upload. Please choose an XML file to send.");
        }
        if (!fileName.trim().toLowerCase(Locale.ROOT).endsWith(XML_EXTENSION)) {
            throw new FileLoadException("\"" + fileName.trim() + "\" is not an XML file. The file name must"
                    + " end with " + XML_EXTENSION + ".");
        }
    }

    /**
     * Parses the bytes, refusing any document type declaration. A file from somebody else's computer
     * has no business asking the server to fetch other files or expand entities on its behalf, and no
     * Guess Market file needs one.
     */
    private static Document parse(InputStream content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new FailOnAnyError());
            return builder.parse(content);
        } catch (SAXParseException e) {
            throw new FileLoadException("the file is not a valid XML document. Line " + e.getLineNumber()
                    + ", column " + e.getColumnNumber() + ": " + e.getMessage());
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new FileLoadException("the file could not be read: " + e.getMessage());
        }
    }

    private List<Event> readEvents(XmlNode root, Predicate<String> nameAlreadyTaken) {
        XmlNode container = root.child(EVENTS_ELEMENT).orElseThrow(() -> new FileLoadException(
                "it has no <" + EVENTS_ELEMENT + "> element."));
        List<XmlNode> eventNodes = container.children(EVENT_ELEMENT);
        if (eventNodes.isEmpty()) {
            throw new FileLoadException("it contains no events. A Guess Market file needs at least one <"
                    + EVENT_ELEMENT + ">.");
        }

        List<String> problems = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        List<EventNodeReader> readers = new ArrayList<>();
        for (int i = 0; i < eventNodes.size(); i++) {
            EventNodeReader reader = new EventNodeReader(eventNodes.get(i), i + 1, problems);
            readers.add(reader);
            reader.read().ifPresent(events::add);
        }
        checkNamesAreNew(readers, nameAlreadyTaken, problems);

        if (!problems.isEmpty()) {
            throw new FileLoadException(problems);
        }
        return events;
    }

    /**
     * Events are told apart by their names alone, so no two may share one: not two in the same file,
     * and not one in the file with one already in the market. Every event that declared a name is
     * checked, including events that are faulty in other ways, so that a clash is reported alongside
     * everything else rather than only once the rest has been put right.
     */
    private static void checkNamesAreNew(List<EventNodeReader> readers, Predicate<String> nameAlreadyTaken,
                                         List<String> problems) {
        Map<String, String> firstUseOfName = new HashMap<>();
        for (EventNodeReader reader : readers) {
            String name = reader.declaredName();
            if (name == null || name.isBlank()) {
                continue;
            }
            String earlier = firstUseOfName.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), reader.label());
            if (earlier != null) {
                problems.add(reader.label() + ": its name is the same as that of " + earlier
                        + ". Every event needs a name of its own.");
            } else if (nameAlreadyTaken.test(name)) {
                problems.add(reader.label() + ": there is already an event called \"" + name.trim()
                        + "\" in the market. Every event needs a name of its own.");
            }
        }
    }

    /**
     * Turns every parsing complaint into an exception. Without it the parser writes warnings straight
     * to the console, which is not this module's job.
     */
    private static final class FailOnAnyError implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) {
            // A warning still leaves a usable document, and the loader checks the content itself.
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
