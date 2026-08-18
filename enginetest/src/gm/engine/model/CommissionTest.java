package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommissionTest {

    private static final double TOLERANCE = 0.0001;

    @Test
    @DisplayName("An on-purchase commission is charged on the purchase and never at closing time")
    void onPurchaseChargesOnlyBuyers() {
        Commission commission = new Commission(50, CommissionType.ON_PURCHASE);

        assertEquals(31.0057, commission.purchaseFee(62.0115), TOLERANCE);
        assertEquals(0.0, commission.closingFee(100.0), TOLERANCE);
    }

    @Test
    @DisplayName("An on-close commission is charged at closing time and never on a purchase")
    void onCloseChargesOnlyWinners() {
        Commission commission = new Commission(50, CommissionType.ON_CLOSE);

        assertEquals(0.0, commission.purchaseFee(62.0115), TOLERANCE);
        assertEquals(50.0, commission.closingFee(100.0), TOLERANCE);
    }

    @Test
    @DisplayName("A commission of zero costs nothing either way")
    void zeroPercentIsFree() {
        assertEquals(0.0, new Commission(0, CommissionType.ON_PURCHASE).purchaseFee(62.0115), TOLERANCE);
        assertEquals(0.0, new Commission(0, CommissionType.ON_CLOSE).closingFee(100.0), TOLERANCE);
    }

    @Test
    @DisplayName("The highest allowed commission is 90 percent")
    void ninetyPercentIsAllowed() {
        assertEquals(90.0, new Commission(90, CommissionType.ON_CLOSE).closingFee(100.0), TOLERANCE);
    }

    @Test
    @DisplayName("A commission outside 0..90 is rejected")
    void percentIsRangeChecked() {
        assertThrows(IllegalArgumentException.class, () -> new Commission(91, CommissionType.ON_CLOSE));
        assertThrows(IllegalArgumentException.class, () -> new Commission(-1, CommissionType.ON_CLOSE));
    }

    @Test
    @DisplayName("Commission types are recognised from the file regardless of letter case")
    void typeLookupIgnoresCase() {
        assertEquals(Optional.of(CommissionType.ON_PURCHASE), CommissionType.fromFileValue("on-purchase"));
        assertEquals(Optional.of(CommissionType.ON_CLOSE), CommissionType.fromFileValue("ON-CLOSE"));
        assertEquals(Optional.of(CommissionType.ON_CLOSE), CommissionType.fromFileValue("  On-Close  "));
    }

    @Test
    @DisplayName("An unknown commission type is reported as absent rather than guessed")
    void unknownTypeIsRejected() {
        assertTrue(CommissionType.fromFileValue("on-sale").isEmpty());
        assertTrue(CommissionType.fromFileValue("").isEmpty());
        assertTrue(CommissionType.fromFileValue(null).isEmpty());
    }
}
