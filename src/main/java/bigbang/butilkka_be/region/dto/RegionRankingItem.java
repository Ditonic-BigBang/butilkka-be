package bigbang.butilkka_be.region.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegionRankingItem(
        int rank,
        String regionCode,
        String regionName,
        @JsonProperty("decline_grade") String declineGrade,
        String direction
) {}
