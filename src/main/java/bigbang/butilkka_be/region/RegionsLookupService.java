package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.lookup.RegionLookupService;
import bigbang.butilkka_be.lookup.model.GeoJsonFeature;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionsLookupService {

    private final RegionLookupService regionLookupService;

    public List<RegionLookupCandidate> lookup(String keyword, Double lat, Double lng) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCoordinate = lat != null && lng != null;

        if (hasKeyword == hasCoordinate) {
            throw AppException.badRequest("keyword 또는 (lat, lng) 중 하나만 입력해야 합니다");
        }

        List<GeoJsonFeature> features = hasKeyword
                ? regionLookupService.searchByKeyword(keyword)
                : regionLookupService.findFeatureByCoordinate(lat, lng)
                        .map(List::of)
                        .orElseGet(List::of);

        if (features.isEmpty()) {
            throw AppException.notFound("매칭되는 상권이 없습니다.");
        }

        return features.stream().map(this::toCandidate).toList();
    }

    private RegionLookupCandidate toCandidate(GeoJsonFeature feature) {
        String dongName = RegionLookupService.extractDongName(feature.admName());
        String address = "서울특별시 " + feature.districtName() + " " + dongName;
        Point centroid = feature.geometry().getCentroid();
        return new RegionLookupCandidate(feature.admCode(), dongName, address, centroid.getY(), centroid.getX());
    }
}
