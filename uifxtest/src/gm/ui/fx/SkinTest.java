package gm.ui.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A misnamed stylesheet is invisible until somebody picks that skin and the program falls over, which
 * is exactly the sort of thing a checker finds and a developer does not. These tests make it loud.
 */
@DisplayName("Skins")
class SkinTest {

    @Test
    @DisplayName("Every skin names a stylesheet that is really there")
    void everySkinHasItsStylesheet() {
        for (Skin skin : Skin.values()) {
            assertNotNull(Skin.class.getResource(skin.stylesheet()),
                    skin + " asks for \"" + skin.stylesheet() + "\", which is not on the class path");
        }
    }

    @Test
    @DisplayName("The plain look is the one you get without asking")
    void theDefaultIsThePlainOne() {
        assertEquals(Skin.CLASSIC, Skin.DEFAULT, "the bonus has to arrive switched off");
        assertEquals(Skin.CLASSIC, Skin.values()[0], "and it should be the first one offered");
    }

    @Test
    @DisplayName("There are three to choose between")
    void thereAreThreeToChooseBetween() {
        assertTrue(Skin.values().length >= 3,
                "the bonus asks for three skins, but there are " + Skin.values().length);
    }
}
