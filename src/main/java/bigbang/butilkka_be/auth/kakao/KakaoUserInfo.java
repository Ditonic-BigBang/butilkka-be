package bigbang.butilkka_be.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfo(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(KakaoProfile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoProfile(String nickname) {}

    public String nickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) return "";
        return kakaoAccount.profile().nickname();
    }
}
