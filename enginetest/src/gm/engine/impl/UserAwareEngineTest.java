package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.NoFileLoadedException;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.PurchaseResultDto;
import gm.engine.api.dto.UserDetailDto;
import gm.engine.api.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine as the graphical front end will drive it: every action says who is taking it.
 * <p>
 * The official small.xml is used throughout. It holds two events — one LMSR run by Tikva, one order
 * book run by Avrum — and three users, the third of whom runs nothing.
 */
class UserAwareEngineTest {

    private static final double TOLERANCE = 0.0001;
    private static final int MUJTABA = 1;
    private static final int WORLD_CUP = 2;
    private static final int AVRUM = 1;
    private static final int TIKVA = 2;
    private static final int MENASH = 3;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    private GuessMarketEngine loaded() {
        engine.loadEventsFile(TestFiles.path("ex2/small.xml"));
        return engine;
    }

    @Test
    @DisplayName("Before a file is loaded, asking about users refuses politely")
    void usersNeedAFileFirst() {
        assertThrows(NoFileLoadedException.class, engine::listUsers);
        assertThrows(NoFileLoadedException.class, () -> engine.userDetail(1));
    }

    @Test
    @DisplayName("Users are listed in file order, numbered from 1, with the cash they were given")
    void usersAreListedInFileOrder() {
        List<UserDto> users = loaded().listUsers();

        assertEquals(List.of(1, 2, 3), users.stream().map(UserDto::number).toList());
        assertEquals(List.of("Avrum", "Tikva", "Menash"), users.stream().map(UserDto::name).toList());
        assertEquals(1000.0, users.get(0).balance(), TOLERANCE);
        assertFalse(users.get(0).blocked());
    }

    @Test
    @DisplayName("A user detail says which events they run and which they have taken part in")
    void userDetailShowsWhatTheyRunAndWhatTheyHold() {
        UserDetailDto tikva = loaded().userDetail(TIKVA);

        assertEquals("Tikva", tikva.name());
        assertEquals(10000.0, tikva.balance(), TOLERANCE);
        assertEquals(List.of("Mujtaba is Dead"), tikva.marketMakerOf());
        assertTrue(tikva.participations().isEmpty(), "nothing has been traded yet");
    }

    @Test
    @DisplayName("Only the market maker can open an event, and it costs them")
    void openingIsTheMarketMakersToDo() {
        GuessMarketEngine market = loaded();

        assertThrows(InvalidSelectionException.class, () -> market.openEvent(MUJTABA, MENASH));

        market.openEvent(MUJTABA, TIKVA);

        assertEquals("Active", market.listEvents().get(0).status());
        assertEquals(10000.0 - 69.3147, market.userDetail(TIKVA).balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Buying says who is buying, and the money lands where it should")
    void buyingIsDoneByANamedUser() {
        GuessMarketEngine market = loaded();
        market.openEvent(MUJTABA, TIKVA);

        PurchaseResultDto purchase = market.buyShares(MUJTABA, MENASH, 1, 100);

        assertEquals("Hell Yea !", purchase.optionName());
        assertEquals(62.0115, purchase.sharesCost(), TOLERANCE);
        assertEquals(3.1006, purchase.commission(), TOLERANCE);
        assertEquals(65.1121, purchase.totalPaid(), TOLERANCE);
        // Menash started with 100, so this leaves him short of it but still solvent.
        assertEquals(100.0 - 65.1121, market.userDetail(MENASH).balance(), TOLERANCE);
        assertFalse(market.userDetail(MENASH).blocked());
    }

    @Test
    @DisplayName("What a user holds in an event shows up in their detail")
    void participationsAppearInTheUserDetail() {
        GuessMarketEngine market = loaded();
        market.openEvent(MUJTABA, TIKVA);
        market.buyShares(MUJTABA, TIKVA, 1, 100);

        UserDetailDto tikva = market.userDetail(TIKVA);

        assertEquals(1, tikva.participations().size());
        assertEquals("Mujtaba is Dead", tikva.participations().get(0).event().name());
        assertEquals(100, tikva.participations().get(0).options().get(0).shares());
        assertEquals(1, tikva.participations().get(0).trades().size());
    }

    @Test
    @DisplayName("A user who has spent past zero is turned away from anything new")
    void aBlockedUserCannotActAgain() {
        GuessMarketEngine market = loaded();
        market.openEvent(MUJTABA, TIKVA);
        // 200 shares cost 143.38 plus commission, well past the 100 Menash holds.
        market.buyShares(MUJTABA, MENASH, 1, 200);

        assertTrue(market.userDetail(MENASH).blocked());
        assertThrows(InvalidSelectionException.class, () -> market.buyShares(MUJTABA, MENASH, 1, 1));
    }

    @Test
    @DisplayName("Only the market maker can close an event, and the holders are paid")
    void closingIsTheMarketMakersToDo() {
        GuessMarketEngine market = loaded();
        market.openEvent(MUJTABA, TIKVA);
        market.buyShares(MUJTABA, TIKVA, 1, 100);

        assertThrows(InvalidSelectionException.class, () -> market.closeEvent(MUJTABA, MENASH, 1));

        market.closeEvent(MUJTABA, TIKVA, 1);

        assertEquals("Closed", market.listEvents().get(0).status());
    }

    @Test
    @DisplayName("An order book event is opened by its own market maker, who ends up holding the stock")
    void anOrderBookEventIsOpenedByItsOwner() {
        GuessMarketEngine market = loaded();

        market.openEvent(WORLD_CUP, AVRUM);

        assertEquals("Active", market.listEvents().get(1).status());
        UserDetailDto avrum = market.userDetail(AVRUM);
        assertEquals(900.0, avrum.balance(), TOLERANCE);
        assertEquals(1, avrum.participations().size());
        assertEquals(100, avrum.participations().get(0).options().get(0).shares());
        assertEquals(100, avrum.participations().get(0).options().get(1).shares());
    }

    @Test
    @DisplayName("Choosing a user or event that does not exist says what the choices are")
    void outOfRangeSelectionsAreExplained() {
        GuessMarketEngine market = loaded();

        assertTrue(assertThrows(InvalidSelectionException.class, () -> market.userDetail(4))
                .getMessage().contains("between 1 and 3"));
        assertThrows(InvalidSelectionException.class, () -> market.userDetail(0));
        assertThrows(InvalidSelectionException.class, () -> market.openEvent(9, TIKVA));
    }

    @Test
    @DisplayName("Every event knows which trading method it uses, for the filters to work on")
    void eventsSayHowTheyAreTraded() {
        List<EventInfoDto> events = loaded().listEvents();

        assertEquals("LMSR", events.get(0).methodKind());
        assertEquals("Order book", events.get(1).methodKind());
        assertEquals("Not started", events.get(0).status());
    }
}
