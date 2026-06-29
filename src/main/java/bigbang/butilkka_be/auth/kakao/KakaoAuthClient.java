package bigbang.butilkka_be.auth.kakao;

import bigbang.butilkka_be.common.exception.AppException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoAuthClient {

    private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient = RestClient.create();

    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        try {
            return restClient.get()
                    .uri(KAKAO_USER_ME_URL)
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserInfo.class);
        } catch (Exception e) {
            throw AppException.unauthorized("유효하지 않은 카카오 토큰입니다");
        }
    }
}
