package gm.engine.xml;

import gm.engine.api.FileLoadException;
import gm.engine.model.Event;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads an events file and turns it into events, or explains exactly why it cannot.
 * <p>
 * Checking happens in two passes. Faults that make the rest of the file meaningless, such as a
 * missing file or text that is not XML at all, stop the load at once. Everything else is gathered:
 * each event is examined in full and every fault recorded, so a single attempt reports all of them
 * together.
 * <p>
 * Nothing here touches the state of the system. The caller receives events only when the whole file
 * was sound, which is what lets a faulty file leave a previously loaded file untouched.
 */
public final class EventsFileLoader {

    private static final String XML_EXTENSION = ".xml";
    private static final String ROOT_ELEMENT = "Guess-Market";
    private static final String EVENTS_ELEMENT = "GM-events";
    private static final String EVENT_ELEMENT = "GM-event";

    /**
     * Reads the file at {@code path}.
     *
     * @return the events it describes, in the order they appear
     * @throws FileLoadException if the file cannot be read or does not describe a sound set of events
     */
    public List<Event> load(String path) {
        XmlNode root = new XmlNode(parse(readableFileAt(path)).getDocumentElement());
        if (!root.isNamed(ROOT_ELEMENT)) {
            throw new FileLoadException("this is not a Guess Market file: its root element is <" + root.name()
                    + "> instead of <" + ROOT_ELEMENT + ">.");
        }
        return readEvents(root);
    }

    private File readableFileAt(String path) {
        if (path == null || path.isBlank()) {
            throw new FileLoadException("no file path was given.");
        }
        String trimmed = path.trim();
        File file = new File(trimmed);
        if (!file.exists()) {
            throw new FileLoadException("there is no file at \"" + trimmed + "\".");
        }
        if (!file.isFile()) {
            throw new FileLoadException("\"" + trimmed + "\" is a folder, not a file.");
        }
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(XML_EXTENSION)) {
            throw new FileLoadException("\"" + trimmed + "\" is not an XML file. The file name must end with "
                    + XML_EXTENSION + ".");
        }
        if (!file.canRead()) {
            throw new FileLoadException("the file \"" + trimmed + "\" cannot be read. Please check that it is"
                    + " not open in another program and that you have permission to read it.");
        }
        return file;
    }

    private Document parse(File file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new FailOnAnyError());
            return builder.parse(file);
        } catch (SAXParseException e) {
            throw new FileLoadException("the file is not a valid XML document. Line " + e.getLineNumber()
                    + ", column " + e.getColumnNumber() + ": " + e.getMessage());
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new FileLoadException("the file could not be read: " + e.getMessage());
        }
    }

    private List<Event> readEvents(XmlNode root) {
        XmlNode eventsNode = root.child(EVENTS_ELEMENT).orElseThrow(() -> new FileLoadException(
                "it has no <" + EVENTS_ELEMENT + "> element, so it describes no events."));
        List<XmlNode> eventNodes = eventsNode.children(EVENT_ELEMENT);
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
        checkIdsAreUnique(readers, problems);

        if (!problems.isEmpty()) {
            throw new FileLoadException(problems);
        }
        return List.copyOf(events);
    }

    private void checkIdsAreUnique(List<EventNodeReader> readers, List<String> problems) {
        Map<Integer, String> firstUseOfId = new HashMap<>();
        for (EventNodeReader reader : readers) {
            if (reader.declaredId().isEmpty()) {
                continue;
            }
            int id = reader.declaredId().getAsInt();
            String earlier = firstUseOfId.putIfAbsent(id, reader.label());
            if (earlier != null) {
                problems.add(reader.label() + ": its id is " + id + ", which is already used by " + earlier
                        + ". Every event must have an id of its own.");
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
