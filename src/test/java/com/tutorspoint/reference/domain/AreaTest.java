package com.tutorspoint.reference.domain;

import com.tutorspoint.common.domain.Language;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The area hierarchy, and the invariants that keep it two levels deep and on the map.
 *
 * <p>These are enforced by the entity and again by a CHECK constraint in
 * {@code V4__reference_data.sql}. Two places on purpose: the entity fails fast with a
 * message a developer can act on, the constraint means no other writer — a future admin
 * import, a hand-run migration — can put a town without a district into the table.
 */
class AreaTest {

    private static final BigDecimal COLOMBO_LAT = new BigDecimal("6.927100");
    private static final BigDecimal COLOMBO_LON = new BigDecimal("79.861200");

    @Test
    void aDistrictHoldsItsTownsAndTheTownKnowsItsDistrict() {
        Area colombo = colombo();

        Area nugegoda = colombo.addTown("COLOMBO_NUGEGODA", 80,
                new BigDecimal("6.864900"), new BigDecimal("79.899700"));

        assertThat(colombo.isDistrict()).isTrue();
        assertThat(colombo.getTowns()).containsExactly(nugegoda);
        assertThat(nugegoda.isDistrict()).isFalse();
        assertThat(nugegoda.getType()).isEqualTo(AreaType.TOWN);
        assertThat(nugegoda.getParent()).isEqualTo(colombo);
    }

    @Test
    void aTownCannotHoldTownsOfItsOwn() {
        Area nugegoda = colombo().addTown("COLOMBO_NUGEGODA", 80, COLOMBO_LAT, COLOMBO_LON);

        assertThatIllegalStateException()
                .isThrownBy(() -> nugegoda.addTown("SOMEWHERE", 10, COLOMBO_LAT, COLOMBO_LON))
                .withMessageContaining("Only a district can hold towns");
    }

    @Test
    void townsAreNotMutableThroughTheGetter() {
        assertThat(colombo().getTowns()).isUnmodifiable();
    }

    @Test
    void rejectsCoordinatesOffTheGlobe() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Area.district("NOWHERE", 10, new BigDecimal("91.000000"), COLOMBO_LON))
                .withMessageContaining("latitude");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Area.district("NOWHERE", 10, COLOMBO_LAT, new BigDecimal("181.000000")))
                .withMessageContaining("longitude");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Area.district("NOWHERE", 10, null, COLOMBO_LON));
    }

    @Test
    void anAreaIsTranslatedLikeEveryOtherReferenceValue() {
        Area colombo = colombo();
        colombo.translate(Language.EN, "Colombo");
        colombo.translate(Language.SI, "කොළඹ");
        colombo.translate(Language.TA, "கொழும்பு");

        assertThat(colombo.nameIn(Language.SI)).isEqualTo("කොළඹ");
        assertThat(colombo.nameIn(Language.TA)).isEqualTo("கொழும்பு");
    }

    private static Area colombo() {
        return Area.district("COLOMBO", 10, COLOMBO_LAT, COLOMBO_LON);
    }
}
