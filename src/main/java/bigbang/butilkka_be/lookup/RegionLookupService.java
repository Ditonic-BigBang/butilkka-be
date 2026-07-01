package bigbang.butilkka_be.lookup;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.lookup.dto.LookupResponse;
import bigbang.butilkka_be.lookup.model.GeoJsonFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class RegionLookupService {

    private static final int SRID_WGS84 = 4326;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(
            new PrecisionModel(), SRID_WGS84);

    private List<GeoJsonFeature> seoulFeatures = List.of();

    @PostConstruct
    public void init() {
        loadGeoJson();
    }

    private void loadGeoJson() {
        List<GeoJsonFeature> features = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource("geojson/seoul.geojson");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                JsonNode featureNodes = root.get("features");

                for (JsonNode feature : featureNodes) {
                    JsonNode properties = feature.get("properties");
                    String admCode = properties.get("adm_cd2").asText();
                    String admName = properties.get("adm_nm").asText();
                    String districtCode = properties.get("sgg").asText();
                    String districtName = properties.get("sggnm").asText();

                    JsonNode geometryNode = feature.get("geometry");
                    Geometry geometry = parseGeometry(geometryNode);

                    features.add(new GeoJsonFeature(
                            admCode, admName, districtCode, districtName, geometry));
                }

                this.seoulFeatures = List.copyOf(features);
                log.info("Loaded {} Seoul administrative regions from GeoJSON",
                        seoulFeatures.size());
            }
        } catch (IOException e) {
            log.error("Failed to load GeoJSON file", e);
            throw new RuntimeException("GeoJSON 파일 로드 실패", e);
        }
    }

    private Geometry parseGeometry(JsonNode geometryNode) {
        String type = geometryNode.get("type").asText();
        JsonNode coordinates = geometryNode.get("coordinates");

        if ("Polygon".equals(type)) {
            return parsePolygon(coordinates);
        } else if ("MultiPolygon".equals(type)) {
            return parseMultiPolygon(coordinates);
        }

        throw new IllegalArgumentException("Unsupported geometry type: " + type);
    }

    private Polygon parsePolygon(JsonNode coordinates) {
        JsonNode ring = coordinates.get(0);
        Coordinate[] coords = new Coordinate[ring.size()];

        for (int i = 0; i < ring.size(); i++) {
            JsonNode point = ring.get(i);
            coords[i] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
        }

        return geometryFactory.createPolygon(coords);
    }

    private Geometry parseMultiPolygon(JsonNode coordinates) {
        Polygon[] polygons = new Polygon[coordinates.size()];

        for (int i = 0; i < coordinates.size(); i++) {
            JsonNode ring = coordinates.get(i).get(0);
            Coordinate[] coords = new Coordinate[ring.size()];

            for (int j = 0; j < ring.size(); j++) {
                JsonNode point = ring.get(j);
                coords[j] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
            }

            polygons[i] = geometryFactory.createPolygon(coords);
        }

        return geometryFactory.createMultiPolygon(polygons);
    }

    public LookupResponse lookup(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) ||
                Double.isInfinite(lat) || Double.isInfinite(lng)) {
            throw AppException.badRequest("유효하지 않은 좌표값입니다");
        }

        if (lat < 37.41 || lat > 37.72 || lng < 126.73 || lng > 127.27) {
            throw AppException.badRequest("서울 범위를 벗어난 좌표입니다");
        }

        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));

        Optional<GeoJsonFeature> matchingFeature = seoulFeatures.stream()
                .filter(feature -> feature.geometry().contains(point))
                .findFirst();

        if (matchingFeature.isEmpty()) {
            throw AppException.notFound("해당 좌표에 대한 행정동 정보를 찾을 수 없습니다");
        }

        GeoJsonFeature feature = matchingFeature.get();
        String dongName = extractDongName(feature.admName());

        return LookupResponse.of(
                feature.admCode(),
                dongName,
                feature.districtCode(),
                feature.districtName()
        );
    }

    private String extractDongName(String admName) {
        String[] parts = admName.split(" ");
        if (parts.length >= 3) {
            return parts[2];
        }
        return admName;
    }
}
