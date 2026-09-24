package gm.engine.impl;

import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.LedgerLineDto;
import gm.dto.ParticipationDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import gm.engine.TestFiles;
import gm.engine.api.FileLoadException;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine as the server sees it: people arrive by name with empty pockets, load funds, upload
 * files that add to one growing market, and trade on whatever anybody has uploaded.
 */
class MarketEngineTest {

    private static final double TOLERANCE = 0.0001;
    private static final int MUJTABA = 1;
    private static final int QUAKE = 2;
    private static final int WORLD_CUP = 3;
    private static final int RAIN = 4;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    /** Tikva uploads small.xml, then Avrum uploads multiple.xml; both have money, Menash too. */
    private GuessMarketEngine twoUploads() {
        for (String name : List.of("Tikva", "Avrum", "Menash")) {
            engine.enterMarket(name);
            engine.deposit(name, 10_000);
        }
        engine.uploadEvents("Tikva", "small.xml", TestFiles.open("ex3/small.xml"));
        engine.uploadEvents("Avrum", "multiple.xml", TestFiles.open("ex3/multiple.xml"));
        return engine;
    }

    @Test
    @DisplayName("A new name enters the market with nothing, and the same name, in any case, is the same person")
    void enteringTheMarket() {
        UserDetailDto avrum = engine.enterMarket("Avrum");
        engine.deposit("Avrum", 50);

        UserDetailDto again = engine.enterMarket("  aVRUM ");

        assertEquals("Avrum", avrum.name());
        assertEquals(0.0, avrum.balance(), TOLERANCE);
        assertEquals("Avrum", again.name(), "the name keeps the spelling it arrived with first");
        assertEquals(50.0, again.balance(), TOLERANCE, "coming back finds everything as it was left");
        assertEquals(1, engine.listUsers().size());
    }

    @Test
    @DisplayName("A blank name is refused")
    void aBlankNameIsRefused() {
        assertThrows(InvalidSelectionException.class, () -> engine.enterMarket("   "));
        assertThrows(InvalidSelectionException.class, () -> engine.enterMarket(null));
        assertTrue(engine.listUsers().isEmpty());
    }

    @Test
    @DisplayName("Before anything is uploaded the market is simply empty, not an error")
    void anEmptyMarket() {
        assertTrue(engine.listEvents().isEmpty());
        assertTrue(engine.listUsers().isEmpty());
    }

    @Test
    @DisplayName("Loading funds raises the balance and adds a line to the account")
    void loadingFunds() {
        engine.enterMarket("Menash");

        UserDetailDto after = engine.deposit("Menash", 120.5);

        assertEquals(120.5, after.balance(), TOLERANCE);
        LedgerDto ledger = engine.ledger("Menash", 0);
        assertEquals(List.of(new LedgerLineDto(1, "Deposit", 120.5, 120.5)), ledger.lines());
    }

    @Test
    @DisplayName("A deposit of nothing, or by nobody, is refused")
    void badDepositsAreRefused() {
        engine.enterMarket("Menash");

        assertThrows(InvalidSelectionException.class, () -> engine.deposit("Menash", 0));
        assertThrows(InvalidSelectionException.class, () -> engine.deposit("Menash", -3));
        assertThrows(InvalidSelectionException.class, () -> engine.deposit("Nobody", 10));
    }

    @Test
    @DisplayName("The ledger hands back only the lines after the ones the caller already has")
    void theLedgerIsFetchedByDelta() {
        engine.enterMarket("Menash");
        engine.deposit("Menash", 10);
        engine.deposit("Menash", 20);
        engine.deposit("Menash", 30);

        LedgerDto rest = engine.ledger("Menash", 1);

        assertEquals(1, rest.after());
        assertEquals(List.of(2, 3), rest.lines().stream().map(LedgerLineDto::number).toList());
        assertTrue(engine.ledger("Menash", 3).lines().isEmpty(), "nothing new has happened");
        assertTrue(engine.ledger("Menash", 99).lines().isEmpty(), "asking beyond the end is simply nothing new");
        assertThrows(InvalidSelectionException.class, () -> engine.ledger("Menash", -1));
    }

    @Test
    @DisplayName("Uploads add up, every event run by whoever uploaded it, numbered in arrival order")
    void uploadsAccumulate() {
        twoUploads();

        List<EventInfoDto> events = engine.listEvents();

        assertEquals(4, events.size());
        assertEquals("Mujtaba is Dead", events.get(0).name());
        assertEquals("Tikva", events.get(0).marketMakerName());
        assertEquals(List.of(1, 2, 3, 4), events.stream().map(EventInfoDto::number).toList());
        assertTrue(events.subList(1, 4).stream().allMatch(event -> event.marketMakerName().equals("Avrum")));
        assertTrue(events.stream().allMatch(event -> event.status().equals("Not started")));
    }

    @Test
    @DisplayName("An upload says which events it added")
    void anUploadReportsWhatItAdded() {
        engine.enterMarket("Avrum");

        UploadResultDto result = engine.uploadEvents("Avrum", "multiple.xml", TestFiles.open("ex3/multiple.xml"));

        assertEquals("multiple.xml", result.fileName());
        assertEquals(List.of("Earth Quake on Dead Sea", "World Cap Winner", "Will it rain tomorrow ?"),
                result.eventNames());
    }

    @Test
    @DisplayName("Uploading a file whose event already exists changes nothing and says why")
    void aRepeatedUploadIsRefused() {
        twoUploads();

        FileLoadException refusal = assertThrows(FileLoadException.class,
                () -> engine.uploadEvents("Menash", "small.xml", TestFiles.open("ex3/small.xml")));

        assertTrue(refusal.getMessage().contains("already"), refusal.getMessage());
        assertEquals(4, engine.listEvents().size());
        assertFalse(engine.listUsers().stream()
                .filter(user -> user.name().equals("Menash")).findFirst().orElseThrow().marketMaker());
    }

    @Test
    @DisplayName("Somebody who never logged in cannot upload")
    void strangersCannotUpload() {
        assertThrows(InvalidSelectionException.class,
                () -> engine.uploadEvents("Nobody", "small.xml", TestFiles.open("ex3/small.xml")));
        assertTrue(engine.listEvents().isEmpty());
    }

    @Test
    @DisplayName("Everybody sees everybody's name, balance, and whether they run an event")
    void everybodySeesEverybody() {
        twoUploads();

        List<UserDto> users = engine.listUsers();

        assertEquals(List.of("Tikva", "Avrum", "Menash"), users.stream().map(UserDto::name).toList());
        assertTrue(users.get(0).marketMaker());
        assertTrue(users.get(1).marketMaker());
        assertFalse(users.get(2).marketMaker());
        assertEquals(10_000.0, users.get(2).balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Only the market maker can open an event, and it costs them")
    void onlyTheMarketMakerOpens() {
        twoUploads();

        assertThrows(InvalidSelectionException.class, () -> engine.openEvent(MUJTABA, "Avrum"));
        EventInfoDto opened = engine.openEvent(MUJTABA, "Tikva");

        assertEquals("Active", opened.status());
        assertEquals(10_000 - 69.3147, engine.userDetail("Tikva").balance(), TOLERANCE);
        assertEquals(69.3147, opened.accountBalance(), TOLERANCE);
    }

    @Test
    @DisplayName("A market maker who has not loaded enough funds cannot open, and is told to load more")
    void openingNeedsFunds() {
        engine.enterMarket("Pauper");
        engine.uploadEvents("Pauper", "small.xml", TestFiles.open("ex3/small.xml"));

        InvalidSelectionException refusal = assertThrows(InvalidSelectionException.class,
                () -> engine.openEvent(MUJTABA, "Pauper"));

        assertTrue(refusal.getMessage().contains("Load more funds"), refusal.getMessage());
    }

    @Test
    @DisplayName("Buying and closing an LMSR event pay the right people, with the commission to the market maker")
    void anLmsrEventFromOpenToClose() {
        twoUploads();
        engine.openEvent(MUJTABA, "Tikva");

        engine.buyShares(MUJTABA, "Menash", 1, 100);
        EventInfoDto closed = engine.closeEvent(MUJTABA, "Tikva", 1);

        assertEquals("Closed", closed.status());
        assertEquals("Hell Yea !", closed.winningOptionName());
        double commission = 62.0115 * 0.05;
        assertEquals(10_000 - 62.0115 - commission + 100, engine.userDetail("Menash").balance(), TOLERANCE);
        assertEquals(10_000 - 69.3147 + commission + (69.3147 + 62.0115 - 100),
                engine.userDetail("Tikva").balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Closing an order book event answers with the closed event, not with an error")
    void closingAnOrderBookEvent() {
        twoUploads();
        engine.openEvent(WORLD_CUP, "Avrum");
        engine.submitOrder(WORLD_CUP, "Avrum", 1, OrderSide.SELL, 10, 0.60);
        engine.submitOrder(WORLD_CUP, "Menash", 1, OrderSide.BUY, 10, 0.60);

        EventInfoDto closed = engine.closeEvent(WORLD_CUP, "Avrum", 1);

        assertEquals("Closed", closed.status());
        assertEquals("Argentina", closed.winningOptionName());
        assertEquals(10_000 - 6 + 10 * 0.85, engine.userDetail("Menash").balance(), TOLERANCE,
                "ten winning shares pay 1 each, less the 15% closing commission");
    }

    @Test
    @DisplayName("A user's detail says which events they run and which they have taken part in")
    void aUserSeesTheirOwnPlace() {
        twoUploads();
        engine.openEvent(MUJTABA, "Tikva");
        engine.buyShares(MUJTABA, "Menash", 2, 10);

        UserDetailDto menash = engine.userDetail("menash");
        UserDetailDto avrum = engine.userDetail("Avrum");

        assertEquals(List.of("Earth Quake on Dead Sea", "World Cap Winner", "Will it rain tomorrow ?"),
                avrum.marketMakerOf());
        assertEquals(1, menash.participations().size());
        ParticipationDto part = menash.participations().get(0);
        assertEquals("Mujtaba is Dead", part.event().name());
        assertEquals(10, part.options().get(1).shares());
    }

    @Test
    @DisplayName("Receiving a commission shows up in the market maker's own ledger, as the exercise asks")
    void aCommissionReachesTheMarketMakersLedger() {
        twoUploads();
        engine.openEvent(MUJTABA, "Tikva");
        int linesBefore = engine.ledger("Tikva", 0).lines().size();

        engine.buyShares(MUJTABA, "Menash", 1, 100);

        List<LedgerLineDto> fresh = engine.ledger("Tikva", linesBefore).lines();
        assertEquals(1, fresh.size());
        assertTrue(fresh.get(0).description().startsWith("Commission from Menash"), fresh.get(0).description());
        assertTrue(fresh.get(0).amount() > 0);
    }

    @Test
    @DisplayName("A user who has spent past zero is turned away from anything new, loading funds included")
    void aBlockedUserCanDoNothing() {
        twoUploads();
        engine.openEvent(MUJTABA, "Tikva");
        engine.enterMarket("Spender");
        engine.deposit("Spender", 1);
        engine.buyShares(MUJTABA, "Spender", 1, 10);

        assertTrue(engine.userDetail("Spender").blocked());
        assertThrows(InvalidSelectionException.class, () -> engine.buyShares(MUJTABA, "Spender", 1, 1));
        assertThrows(InvalidSelectionException.class, () -> engine.deposit("Spender", 100));
    }

    @Test
    @DisplayName("Choosing an event, option or user that does not exist says what the choices are")
    void outOfRangeChoicesExplainThemselves() {
        twoUploads();

        assertTrue(assertThrows(InvalidSelectionException.class, () -> engine.marketState(9))
                .getMessage().contains("between 1 and 4"));
        assertTrue(assertThrows(InvalidSelectionException.class, () -> engine.userDetail("Ghost"))
                .getMessage().contains("Ghost"));
        engine.openEvent(MUJTABA, "Tikva");
        assertTrue(assertThrows(InvalidSelectionException.class, () -> engine.buyShares(MUJTABA, "Menash", 3, 1))
                .getMessage().contains("between 1 and 2"));
    }

    @Test
    @DisplayName("Every event carries its trading method and its own account for the list to show")
    void theListCarriesMethodAndAccount() {
        twoUploads();
        engine.openEvent(QUAKE, "Avrum");

        List<EventInfoDto> events = engine.listEvents();

        assertEquals("LMSR", events.get(0).methodKind());
        assertEquals("Order book", events.get(1).methodKind());
        assertEquals(1000.0, events.get(1).accountBalance(), TOLERANCE, "the initial stock was paid in");
        assertEquals(1000.0, events.get(1).openingCost(), TOLERANCE);
        assertEquals(100 * Math.log(2), events.get(0).openingCost(), TOLERANCE, "the LMSR subsidy, b ln 2");
        assertNull(events.get(1).winningOptionName());
        assertEquals("LMSR", events.get(RAIN - 1).methodKind());
    }
}
