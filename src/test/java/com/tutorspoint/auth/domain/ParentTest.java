package com.tutorspoint.auth.domain;

import com.tutorspoint.common.domain.Language;
import com.tutorspoint.common.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Child sub-profiles are part of the parent aggregate (FR-A5). */
class ParentTest {

    private Parent parent() {
        return new Parent("kamal@example.lk", "$2a$10$hash", "Kamal Silva", "+94712223334", Language.SI);
    }

    @Test
    void aParentStartsWithNoChildren() {
        assertThat(parent().getChildren()).isEmpty();
    }

    @Test
    void addChildLinksTheChildToItsParent() {
        Parent parent = parent();

        ChildProfile child = parent.addChild("Sanduni", "Grade 11", "GCE O/L", "Visakha Vidyalaya", null);

        assertThat(parent.getChildren()).containsExactly(child);
        assertThat(child.getParent()).isSameAs(parent);
        assertThat(child.getName()).isEqualTo("Sanduni");
        assertThat(child.getSchool()).isEqualTo("Visakha Vidyalaya");
        assertThat(child.getNotes()).isNull();
    }

    @Test
    void aParentMayManageManyChildren() {
        Parent parent = parent();

        parent.addChild("Sanduni", "Grade 11", "GCE O/L", null, null);
        parent.addChild("Dinuka", "Grade 8", "Grade 6-9", null, "Needs help with maths");

        assertThat(parent.getChildren()).hasSize(2);
    }

    @Test
    void theChildrenCollectionCannotBeEditedFromOutside() {
        Parent parent = parent();
        ChildProfile child = parent.addChild("Sanduni", "Grade 11", "GCE O/L", null, null);

        assertThatThrownBy(() -> parent.getChildren().remove(child))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void removeChildDetachesOnlyThisParentsOwnChild() {
        Parent parent = parent();
        ChildProfile ours = parent.addChild("Sanduni", "Grade 11", "GCE O/L", null, null);
        ChildProfile someoneElses = parent().addChild("Tharindu", "Grade 5", "Grade 5 Scholarship", null, null);

        parent.removeChild(ours);

        assertThat(parent.getChildren()).isEmpty();
        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> parent.removeChild(someoneElses))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("CHILD_PROFILE_NOT_OWNED"));
    }

    @Test
    void aDeletedAccountCannotGainChildren() {
        Parent parent = parent();
        parent.delete();

        assertThatExceptionOfType(BusinessRuleViolationException.class)
                .isThrownBy(() -> parent.addChild("Sanduni", "Grade 11", "GCE O/L", null, null))
                .satisfies(ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_DELETED"));
    }

    @Test
    void aChildNeedsAName_aGradeAndAnExamLevel() {
        Parent parent = parent();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> parent.addChild(" ", "Grade 11", "GCE O/L", null, null))
                .withMessageContaining("name");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> parent.addChild("Sanduni", null, "GCE O/L", null, null))
                .withMessageContaining("grade");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> parent.addChild("Sanduni", "Grade 11", "", null, null))
                .withMessageContaining("examLevel");
    }

    @Test
    void updateDetailsBlanksOutOptionalFieldsThatWereCleared() {
        ChildProfile child = parent().addChild("Sanduni", "Grade 11", "GCE O/L", "Visakha Vidyalaya", "Evenings only");

        child.updateDetails("Sanduni Perera", "Grade 12", "GCE A/L", "  ", null);

        assertThat(child.getName()).isEqualTo("Sanduni Perera");
        assertThat(child.getGrade()).isEqualTo("Grade 12");
        assertThat(child.getExamLevel()).isEqualTo("GCE A/L");
        assertThat(child.getSchool()).isNull();
        assertThat(child.getNotes()).isNull();
    }
}
