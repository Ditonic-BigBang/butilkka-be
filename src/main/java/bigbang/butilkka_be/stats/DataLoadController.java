package bigbang.butilkka_be.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/data")
@RequiredArgsConstructor
public class DataLoadController {

    private final DataLoadService dataLoadService;

    /**
     * CSV 데이터 일괄 적재
     * POST /api/admin/data/load
     * 이미 데이터 있으면 skip (중복 실행 안전)
     */
    @PostMapping("/load")
    public ResponseEntity<String> load() {
        dataLoadService.loadAll();
        return ResponseEntity.ok("데이터 적재 완료");
    }
}
