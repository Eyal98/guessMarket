package gm.engine.xml;

import gm.engine.TestFiles;
import gm.engine.api.FileLoadException;
import gm.engine.model.CommissionType;
import gm.engine.model.Event;
import gm.engine.model.EventOption;
import gm.engine.model.EventStatus;
import gm.engine.model.LmsrEvent;
import gm.engine.model.OrderBookEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading an uploaded events file. Every sample under test-files/ex3 is read here, as a stream, the
 * way it reaches the server. The sound ones must come through intact, and each faulty one must produce
 * a message that names the event and says what is actually wrong with it, since that message is all
 * the person who uploaded the file has to go on.
 */
class EventsFileLoaderTest {

    private static final Predicate<String> NOTHING_TAKEN = name -> false;

    private final EventsFileLoader loader = new EventsFileLoader();

    private List<Event> read(String sample) {
        return loader.read(fileNameOf(sample), TestFiles.open(sample), NOTHING_TAKEN);
    }

    private FileLoadException failureFor(String sample) {
        return assertThrows(FileLoadException.class, () -> read(sample), sample);
    }

    private static String fileNameOf(String sample) {
        return Path.of(sample).getFileName().toString();
    }

    @Test
    @DisplayName("The official small.xml loads its one LMSR event, dormant and waiting for a market maker")
    void theOfficialSmallFile() {
        List<Event> events = read("ex3/small.xml");

        assertEquals(1, events.size());
        LmsrEvent event = assertInstanceOf(LmsrEvent.class, events.get(0));
        assertEquals("Mujtaba is Dead", event.name());
        assertEquals(List.of("Hell Yea !", "No way !"), optionNames(event));
        assertEquals(5, event.commission().percent());
        assertEquals(CommissionType.ON_PURCHASE, event.commission().type());
        assertEquals(100, event.liquidity());
        assertEquals(EventStatus.NOT_STARTED, event.status());
        assertNull(event.marketMaker(), "whoever uploads the file becomes its market maker, not the loader");
    }

    @Test
    @DisplayName("The official multiple.xml loads both order books and the LMSR event, in file order")
    void theOfficialMultipleFile() {
        List<Event> events = read("ex3/multiple.xml");

        assertEquals(List.of("Earth Quake on Dead Sea", "World Cap Winner", "Will it rain tomorrow ?"),
                events.stream().map(Event::name).toList());
        OrderBookEvent quake = assertInstanceOf(OrderBookEvent.class, events.get(0));
        assertEquals(1000, quake.initialInvestment());
        assertEquals(1, quake.baseValue());
        assertFalse(quake.allowsMint());
        assertTrue(assertInstanceOf(OrderBookEvent.class, events.get(1)).allowsMint());
        assertEquals(200, assertInstanceOf(LmsrEvent.class, events.get(2)).liquidity());
        assertEquals(CommissionType.ON_CLOSE, events.get(2).commission().type());
    }

    @Test
    @DisplayName("A sound file of our own loads every event with the terms it was given")
    void aSoundFile() {
        List<Event> events = read("ex3/events-basic.xml");

        assertEquals(3, events.size());
        assertEquals("Earth Quake on Dead Sea", events.get(0).name());
        assertEquals(50, events.get(0).commission().percent());
        assertEquals("LMSR (b=200)", events.get(1).methodDescription());
        assertEquals(0, events.get(2).commission().percent());
    }

    @Test
    @DisplayName("Surrounding spaces are ignored and the commission type is read whatever its case")
    void trimsTextAndIgnoresLetterCase() {
        Event event = read("ex3/events-single.xml").get(0);

        assertEquals("Rain In Tel Aviv This Week", event.name());
        assertEquals("Yes", event.options().get(0).name());
        assertTrue(event.description().startsWith("Will more than"), event.description());
        assertEquals(CommissionType.ON_PURCHASE, event.commission().type());
    }

    @Test
    @DisplayName("Events of three and four options load, for both trading methods")
    void moreThanTwoOptions() {
        List<Event> events = read("ex3/three-options.xml");

        assertEquals(List.of("Argentina", "Spain", "Brazil"), optionNames(events.get(0)));
        assertEquals(4, events.get(1).options().size());
        assertInstanceOf(OrderBookEvent.class, events.get(1));
    }

    @Test
    @DisplayName("A file whose name does not end with .xml is refused, whatever it holds")
    void onlyXmlFiles() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> loader.read("notes.txt", TestFiles.open("ex3/small.xml"), NOTHING_TAKEN));

        assertMentions(failure, "notes.txt", ".xml");
    }

    @Test
    @DisplayName("An upload without a file is refused")
    void noFileAtAll() {
        assertMentions(assertThrows(FileLoadException.class,
                () -> loader.read(null, null, NOTHING_TAKEN)), "no file");
        assertMentions(assertThrows(FileLoadException.class,
                () -> loader.read("  ", TestFiles.open("ex3/small.xml"), NOTHING_TAKEN)), "no file");
    }

    @Test
    @DisplayName("An empty file is refused as not being XML")
    void anEmptyFile() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> loader.read("empty.xml", new ByteArrayInputStream(new byte[0]), NOTHING_TAKEN));

        assertMentions(failure, "not a valid XML document");
    }

    @Test
    @DisplayName("Text that is not XML is reported with the line it broke on")
    void rejectsTextThatIsNotXml() {
        assertMentions(failureFor("ex3/bad-malformed.xml"), "not a valid XML document", "Line");
    }

    @Test
    @DisplayName("Sound XML that is not a Guess Market is refused by its root element")
    void rejectsAFileThatIsNotAGuessMarket() {
        assertMentions(failureFor("ex3/bad-wrong-root.xml"), "Shopping-List", "Guess-Market");
    }

    @Test
    @DisplayName("An exercise 2 file is refused: users now log in instead of arriving in the file")
    void anExercise2FileIsRefused() {
        assertMentions(failureFor("ex2/small.xml"), "GM-users", "log in");
    }

    @Test
    @DisplayName("An exercise 1 file is refused: its events carry ids, which exercise 3 files do not")
    void anExercise1FileIsRefused() {
        assertMentions(failureFor("ex1/single.xml"), "<id>", "name");
    }

    @Test
    @DisplayName("Two events of one name in the same file are refused, whatever the letter case")
    void duplicateNamesInTheFile() {
        assertMentions(failureFor("ex3/bad-duplicate-names.xml"), "rain on friday", "Rain On Friday");
    }

    @Test
    @DisplayName("An event whose name is already in the market is refused, whatever the letter case")
    void aNameAlreadyInTheMarket() {
        Set<String> market = Set.of("mujtaba is dead");

        FileLoadException failure = assertThrows(FileLoadException.class, () -> loader.read("small.xml",
                TestFiles.open("ex3/small.xml"),
                name -> market.contains(name.trim().toLowerCase(Locale.ROOT))));

        assertMentions(failure, "Mujtaba is Dead", "already");
    }

    @Test
    @DisplayName("A commission above 90 is reported with the offending value")
    void rejectsACommissionAbove90() {
        assertMentions(failureFor("ex3/bad-commission-too-high.xml"), "commission is 95", "between 0 and 90");
    }

    @Test
    @DisplayName("A negative commission is reported with the offending value")
    void rejectsANegativeCommission() {
        assertMentions(failureFor("ex3/bad-commission-negative.xml"), "commission is -5", "between 0 and 90");
    }

    @Test
    @DisplayName("An unknown commission type is reported alongside the accepted ones")
    void rejectsAnUnknownCommissionType() {
        assertMentions(failureFor("ex3/bad-commission-type.xml"), "on-sale", "on-purchase", "on-close");
    }

    @Test
    @DisplayName("An event with a single option is refused")
    void rejectsAnEventWithOneOption() {
        assertMentions(failureFor("ex3/bad-one-option.xml"), "1 option", "at least 2");
    }

    @Test
    @DisplayName("Two options of the same name, among any number, are refused")
    void rejectsRepeatedOptions() {
        assertMentions(failureFor("ex3/bad-duplicate-option-names.xml"), "\"YES\"", "\"Yes\"");
    }

    @Test
    @DisplayName("A liquidity index of zero is refused")
    void rejectsANonPositiveLiquidity() {
        assertMentions(failureFor("ex3/bad-liquidity-zero.xml"), "liquidity index", "is 0", "positive whole number");
    }

    @Test
    @DisplayName("A liquidity index that is not a number is refused")
    void rejectsALiquidityThatIsNotANumber() {
        assertMentions(failureFor("ex3/bad-liquidity-text.xml"), "one hundred", "not a whole number");
    }

    @Test
    @DisplayName("A missing mandatory element is named")
    void rejectsAMissingDescription() {
        assertMentions(failureFor("ex3/bad-missing-description.xml"), "<description>", "has no");
    }

    @Test
    @DisplayName("An order book with a base value of zero and a minting rule that is not a yes or no is refused")
    void rejectsABrokenOrderBook() {
        assertMentions(failureFor("ex3/bad-order-book.xml"), "d is 0", "maybe", "true or false");
    }

    @Test
    @DisplayName("Every fault in the file is reported together, including a name clashing with a faulty event")
    void reportsEveryProblemInOneGo() {
        FileLoadException failure = failureFor("ex3/bad-many-problems.xml");

        assertEquals(5, failure.problems().size(), failure.getMessage());
        assertMentions(failure,
                "commission is 150",
                "1 option",
                "Event #3: it has no name attribute",
                "<b> value",
                "COMMISSION OUT OF RANGE");
    }

    @Test
    @DisplayName("A faulty file produces one report rather than any other kind of failure")
    void faultyFilesNeverLeakAnUnexpectedFailure() {
        for (String sample : List.of("bad-commission-negative.xml", "bad-commission-too-high.xml",
                "bad-commission-type.xml", "bad-duplicate-names.xml", "bad-duplicate-option-names.xml",
                "bad-liquidity-text.xml", "bad-liquidity-zero.xml", "bad-malformed.xml",
                "bad-many-problems.xml", "bad-missing-description.xml", "bad-one-option.xml",
                "bad-order-book.xml", "bad-wrong-root.xml")) {
            failureFor("ex3/" + sample);
        }
    }

    @Test
    @DisplayName("Text in other alphabets reaches the engine intact, because the bytes are read as UTF-8")
    void readsUtf8() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Guess-Market><GM-events><GM-event name="Café Olé">
                  <description>Naïve?</description><commission type="on-close">1</commission>
                  <GM-options><GM-option>Oui</GM-option><GM-option>Non</GM-option></GM-options>
                  <GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method>
                </GM-event></GM-events></Guess-Market>
                """;

        List<Event> events = loader.read("cafe.xml",
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), NOTHING_TAKEN);

        assertEquals("Café Olé", events.get(0).name());
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
