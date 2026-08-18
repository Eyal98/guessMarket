package gm.engine.xml;

import gm.engine.TestFiles;
import gm.engine.api.FileLoadException;
import gm.engine.model.CommissionType;
import gm.engine.model.Event;
import gm.engine.model.EventOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every sample file under test-files is loaded here. The sound ones must come through intact, and
 * each faulty one must produce a message that names the event and says what is actually wrong with
 * it, since that message is all the person loading the file has to go on.
 */
class EventsFileLoaderTest {

    private final EventsFileLoader loader = new EventsFileLoader();

    @Test
    @DisplayName("A sound file is loaded with its events in file order")
    void loadsASoundFile() {
        List<Event> events = loader.load(TestFiles.path("events-basic.xml"));

        assertEquals(3, events.size());

        Event first = events.get(0);
        assertEquals(3, first.id());
        assertEquals("Earth Quake on Dead Sea", first.name());
        assertEquals(50, first.commission().percent());
        assertEquals(CommissionType.ON_PURCHASE, first.commission().type());
        assertEquals(List.of("Yes", "No"), optionNames(first));
        assertEquals("LMSR (b=100)", first.methodDescription());
        assertTrue(first.isOpen(), "a freshly loaded event should be open");

        assertEquals(1, events.get(1).id());
        assertEquals(CommissionType.ON_CLOSE, events.get(1).commission().type());
        assertEquals("LMSR (b=200)", events.get(1).methodDescription());

        assertEquals(7, events.get(2).id());
        assertEquals(0, events.get(2).commission().percent());
    }

    @Test
    @DisplayName("Surrounding spaces are ignored and the commission type is read whatever its case")
    void trimsTextAndIgnoresLetterCase() {
        Event event = loader.load(TestFiles.path("events-single.xml")).get(0);

        assertEquals("Rain In Tel Aviv This Week", event.name());
        assertEquals("Yes", event.options().get(0).name());
        assertTrue(event.description().startsWith("Will more than"), event.description());
        assertTrue(event.description().endsWith("Service."), event.description());
        assertEquals(CommissionType.ON_PURCHASE, event.commission().type());
    }

    @Test
    @DisplayName("A path containing spaces needs no special treatment")
    void loadsFromAPathContainingSpaces() {
        List<Event> events = loader.load(TestFiles.path("with spaces/events in a folder with spaces.xml"));

        assertEquals(1, events.size());
        assertEquals("Event Loaded From A Path With Spaces", events.get(0).name());
    }

    @Test
    @DisplayName("A file that is not there is reported by name")
    void rejectsAMissingFile() {
        assertMentions(failureFor("no-such-file.xml"), "there is no file at", "no-such-file.xml");
    }

    @Test
    @DisplayName("A file whose name does not end with .xml is refused")
    void rejectsAFileThatIsNotNamedXml() {
        assertMentions(failureFor("not-an-xml-file.txt"), "is not an XML file", ".xml");
    }

    @Test
    @DisplayName("A folder given where a file was expected is refused")
    void rejectsAFolder() {
        FileLoadException failure = assertThrows(FileLoadException.class, () -> loader.load(TestFiles.folder()));

        assertMentions(failure, "is a folder, not a file");
    }

    @Test
    @DisplayName("An empty path is refused")
    void rejectsAnEmptyPath() {
        assertMentions(assertThrows(FileLoadException.class, () -> loader.load("   ")), "no file path was given");
    }

    @Test
    @DisplayName("Text that is not XML is reported with the line it broke on")
    void rejectsTextThatIsNotXml() {
        assertMentions(failureFor("bad-malformed.xml"), "not a valid XML document", "Line");
    }

    @Test
    @DisplayName("Sound XML that is not a Guess Market is refused by its root element")
    void rejectsAFileThatIsNotAGuessMarket() {
        assertMentions(failureFor("bad-wrong-root.xml"), "Shopping-List", "Guess-Market");
    }

    @Test
    @DisplayName("Two events sharing an id are reported, naming both of them")
    void rejectsDuplicateIds() {
        assertMentions(failureFor("bad-duplicate-ids.xml"), "Second Event", "First Event", "id is 4");
    }

    @Test
    @DisplayName("A commission above 90 is reported with the offending value")
    void rejectsACommissionAbove90() {
        assertMentions(failureFor("bad-commission-too-high.xml"), "commission is 95", "between 0 and 90");
    }

    @Test
    @DisplayName("A negative commission is reported with the offending value")
    void rejectsANegativeCommission() {
        assertMentions(failureFor("bad-commission-negative.xml"), "commission is -5", "between 0 and 90");
    }

    @Test
    @DisplayName("An unknown commission type is reported alongside the accepted ones")
    void rejectsAnUnknownCommissionType() {
        assertMentions(failureFor("bad-commission-type.xml"), "on-sale", "on-purchase", "on-close");
    }

    @Test
    @DisplayName("An event with three options is refused")
    void rejectsAnEventWithThreeOptions() {
        assertMentions(failureFor("bad-three-options.xml"), "3 options", "exactly 2");
    }

    @Test
    @DisplayName("An event with a single option is refused")
    void rejectsAnEventWithOneOption() {
        assertMentions(failureFor("bad-one-option.xml"), "1 options", "exactly 2");
    }

    @Test
    @DisplayName("A liquidity index of zero is refused")
    void rejectsANonPositiveLiquidity() {
        assertMentions(failureFor("bad-liquidity-zero.xml"), "liquidity index", "is 0", "positive whole number");
    }

    @Test
    @DisplayName("A liquidity index that is not a number is refused")
    void rejectsALiquidityThatIsNotANumber() {
        assertMentions(failureFor("bad-liquidity-text.xml"), "one hundred", "not a whole number");
    }

    @Test
    @DisplayName("A missing mandatory element is named")
    void rejectsAMissingDescription() {
        assertMentions(failureFor("bad-missing-description.xml"), "<description>", "has no");
    }

    @Test
    @DisplayName("An order book event is explained rather than left to fail obscurely")
    void explainsThatOrderBooksAreNotSupportedYet() {
        assertMentions(failureFor("bad-order-book.xml"), "order book", "GM-LMSR");
    }

    @Test
    @DisplayName("Every fault in the file is reported together, including ids clashing with a rejected event")
    void reportsEveryProblemInOneGo() {
        FileLoadException failure = failureFor("bad-many-problems.xml");

        assertEquals(5, failure.problems().size(), failure.getMessage());
        assertMentions(failure,
                "commission is 150",
                "3 options",
                "Event #3: it has no name attribute",
                "<b> value",
                "already used by Event #2");
    }

    @Test
    @DisplayName("A faulty file produces one report rather than a stack trace")
    void faultyFilesNeverLeakAnUnexpectedFailure() {
        for (String fileName : List.of("bad-duplicate-ids.xml", "bad-commission-too-high.xml",
                "bad-commission-negative.xml", "bad-commission-type.xml", "bad-three-options.xml",
                "bad-one-option.xml", "bad-liquidity-zero.xml", "bad-liquidity-text.xml",
                "bad-missing-description.xml", "bad-order-book.xml", "bad-malformed.xml",
                "bad-wrong-root.xml", "bad-many-problems.xml")) {
            assertThrows(FileLoadException.class, () -> loader.load(TestFiles.path(fileName)), fileName);
        }
    }

    private FileLoadException failureFor(String fileName) {
        return assertThrows(FileLoadException.class, () -> loader.load(TestFiles.path(fileName)), fileName);
    }

    private static void assertMentions(FileLoadException failure, String... expected) {
        String message = failure.getMessage();
        for (String fragment : expected) {
            assertTrue(message.contains(fragment),
                    "expected the report to mention \"" + fragment + "\", but it read:" + System.lineSeparator() + message);
        }
    }

    private static List<String> optionNames(Event event) {
        return event.options().stream().map(EventOption::name).toList();
    }
}
