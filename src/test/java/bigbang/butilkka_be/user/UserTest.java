package bigbang.butilkka_be.user;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void updateStore_setsFieldsAndMarksOnboarded() {
        User user = User.create(123L, "테스트");

        user.updateStore("1168064000", "CS100001", 37.5, 127.0, "테스트가게", LocalDate.of(2020, 1, 1));

        assertThat(user.isOnboarded()).isTrue();
        assertThat(user.getStoreRegion()).isEqualTo("1168064000");
        assertThat(user.getCategoryCode()).isEqualTo("CS100001");
        assertThat(user.getStoreLat()).isEqualTo(37.5);
        assertThat(user.getStoreLng()).isEqualTo(127.0);
        assertThat(user.getStoreName()).isEqualTo("테스트가게");
        assertThat(user.getStoreOpenDate()).isEqualTo(LocalDate.of(2020, 1, 1));
    }
}
