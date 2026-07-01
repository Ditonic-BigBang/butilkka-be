package bigbang.butilkka_be.lookup.model;

import org.locationtech.jts.geom.Geometry;

public record GeoJsonFeature(
        String admCode,
        String admName,
        String districtCode,
        String districtName,
        Geometry geometry
) {}
