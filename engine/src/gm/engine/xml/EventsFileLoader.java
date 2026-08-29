package gm.engine.xml;

import gm.engine.api.FileLoadException;
import gm.engine.model.Event;
import gm.engine.model.SystemState;
import gm.engine.model.User;
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
    private static final String USERS_ELEMENT = "GM-users";
    private static final String USER_ELEMENT = "GM-user";

    /**
     * Reads the file at {@code path}.
     *
     * @return the events and users it describes, in the order they appear
     * @throws FileLoadException if the file cannot be read or does not describe a sound market
     */
    public SystemState load(String path) {
        XmlNode root = new XmlNode(parse(readableFileAt(path)).getDocumentElement());
        if (!root.isNamed(ROOT_ELEMENT)) {
            throw new FileLoadException("this is not a Guess Market file: its root element is <" + root.name()
                    + "> instead of <" + ROOT_ELEMENT + ">.");
        }
        if (root.child(USERS_ELEMENT).isEmpty()) {
            throw new FileLoadException("it has no <" + USERS_ELEMENT + "> element. This looks like a file"
                    + " written for the earlier version of the exercise, which had no users; this version"
                    + " needs one.");
        }
        return readMarket(root);
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

    private SystemState readMarket(XmlNode root) {
        List<String> problems = new ArrayList<>();

        List<XmlNode> eventNodes = childrenOf(root, EVENTS_ELEMENT, EVENT_ELEMENT,
                "it contains no events. A Guess Market file needs at least one <" + EVENT_ELEMENT + ">.");
        List<Event> events = new ArrayList<>();
        List<EventNodeReader> readers = new ArrayList<>();
        for (int i = 0; i < eventNodes.size(); i++) {
            EventNodeReader reader = new EventNodeReader(eventNodes.get(i), i + 1, problems);
            readers.add(reader);
            reader.read().ifPresent(events::add);
        }
        checkIdsAreUnique(readers, problems);

        List<XmlNode> userNodes = childrenOf(root, USERS_ELEMENT, USER_ELEMENT,
                "it contains no users. A Guess Market file needs at least one <" + USER_ELEMENT + ">.");
        List<User> users = new ArrayList<>();
        List<UserNodeReader> userReaders = new ArrayList<>();
        for (int i = 0; i < userNodes.size(); i++) {
            UserNodeReader reader = new UserNodeReader(userNodes.get(i), i + 1, problems);
            userReaders.add(reader);
            reader.read().ifPresent(users::add);
        }
        checkNamesAreUnique(users, problems);
        assignMarketMakers(events, users, userReaders, problems);

        if (!problems.isEmpty()) {
            throw new FileLoadException(problems);
        }
        return new SystemState(events, users);
    }

    private List<XmlNode> childrenOf(XmlNode root, String containerName, String itemName, String ifEmpty) {
        XmlNode container = root.child(containerName).orElseThrow(() -> new FileLoadException(
                "it has no <" + containerName + "> element."));
        List<XmlNode> items = container.children(itemName);
        if (items.isEmpty()) {
            throw new FileLoadException(ifEmpty);
        }
        return items;
    }

    private void checkNamesAreUnique(List<User> users, List<String> problems) {
        Map<String, String> seen = new HashMap<>();
        for (User user : users) {
            String earlier = seen.putIfAbsent(user.name().toLowerCase(Locale.ROOT), user.name());
            if (earlier != null) {
                problems.add("Two users are both called \"" + user.name()
                        + "\". Every user must have a name of their own.");
            }
        }
    }

    /**
     * Hands each event to the user who claims to run it, and complains about both ways that can go
     * wrong: a user pointing at an event that is not there, and an event nobody has claimed.
     */
    private void assignMarketMakers(List<Event> events, List<User> users,
                                    List<UserNodeReader> userReaders, List<String> problems) {
        Map<Integer, Event> eventsById = new HashMap<>();
        for (Event event : events) {
            eventsById.put(event.id(), event);
        }
        for (int i = 0; i < userReaders.size() && i < users.size(); i++) {
            UserNodeReader reader = userReaders.get(i);
            User user = users.get(i);
            for (int eventId : reader.runsEventIds()) {
                Event event = eventsById.get(eventId);
                if (event == null) {
                    problems.add(reader.label() + ": it is the market maker of event " + eventId
                            + ", but no event has that id.");
                    continue;
                }
                if (event.marketMaker() != null) {
                    problems.add("Event \"" + event.name() + "\" has two market makers, "
                            + event.marketMaker().name() + " and " + user.name()
                            + ". Every event must have exactly one.");
                    continue;
                }
                event.assignMarketMaker(user);
            }
        }
        for (Event event : events) {
            if (event.marketMaker() == null) {
                problems.add("Event \"" + event.name()
                        + "\" has no market maker. Every event must be run by exactly one user.");
            }
        }
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
