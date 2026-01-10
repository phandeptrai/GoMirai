package com.gomirai.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.map.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapboxService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mapbox.api.token}")
    private String accessToken;

    @Value("${mapbox.api.base-url}")
    private String baseUrl;

    /**
     * Get directions between two points
     */
    public RouteResponse getDirections(RouteRequest request) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/directions/v5/mapbox/{profile}/{coordinates}")
                    .buildAndExpand(request.getProfile(), formatCoordinates(request.getOrigin(), request.getDestination()))
                    .toUriString();

            url += "?access_token=" + accessToken + "&geometries=geojson&steps=true&language=vi";

            log.debug("Calling Mapbox Directions API: {}", url.replace(accessToken, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseDirectionsResponse(response.getBody());
            }

            throw new BusinessException("Failed to get directions from Mapbox API");

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Mapbox Directions API error: {}", e.getMessage());
            throw new BusinessException("Map service temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error calling Mapbox Directions API", e);
            throw new BusinessException("Failed to calculate route.");
        }
    }

    /**
     * Geocode an address to coordinates
     */
    public GeocodeResponse geocode(String address) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/geocoding/v5/mapbox.places/{address}.json")
                    .buildAndExpand(address)
                    .toUriString();

            url += "?access_token=" + accessToken + "&limit=1";

            log.debug("Calling Mapbox Geocoding API: {}", url.replace(accessToken, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseGeocodeResponse(response.getBody());
            }

            throw new BusinessException("Failed to geocode address");

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Mapbox Geocoding API error: {}", e.getMessage());
            throw new BusinessException("Map service temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error calling Mapbox Geocoding API", e);
            throw new BusinessException("Failed to geocode address.");
        }
    }

    /**
     * Reverse geocode coordinates to address
     */
    public ReverseGeocodeResponse reverseGeocode(GeoPoint location) {
        try {
            String coordinates = location.getLongitude() + "," + location.getLatitude();
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/geocoding/v5/mapbox.places/{coordinates}.json")
                    .buildAndExpand(coordinates)
                    .toUriString();

            url += "?access_token=" + accessToken + "&limit=1";

            log.debug("Calling Mapbox Reverse Geocoding API: {}", url.replace(accessToken, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseReverseGeocodeResponse(response.getBody());
            }

            throw new BusinessException("Failed to reverse geocode coordinates");

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Mapbox Reverse Geocoding API error: {}", e.getMessage());
            throw new BusinessException("Map service temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error calling Mapbox Reverse Geocoding API", e);
            throw new BusinessException("Failed to reverse geocode coordinates.");
        }
    }

    private String formatCoordinates(GeoPoint origin, GeoPoint destination) {
        return String.format("%s,%s;%s,%s",
                origin.getLongitude(), origin.getLatitude(),
                destination.getLongitude(), destination.getLatitude());
    }

    private RouteResponse parseDirectionsResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode route = root.path("routes").get(0);
        JsonNode leg = route.path("legs").get(0);

        double distance = leg.path("distance").asDouble();
        int duration = leg.path("duration").asInt();
        JsonNode geometry = route.path("geometry");
        JsonNode coordinates = geometry.path("coordinates");

        List<GeoPoint> geometryPoints = new ArrayList<>();
        for (JsonNode coord : coordinates) {
            geometryPoints.add(new GeoPoint(coord.get(1).asDouble(), coord.get(0).asDouble()));
        }

        List<RouteStep> steps = new ArrayList<>();
        JsonNode stepsNode = leg.path("steps");
        for (JsonNode step : stepsNode) {
            JsonNode maneuver = step.path("maneuver");
            JsonNode stepLocation = maneuver.path("location");
            GeoPoint stepGeoPoint = new GeoPoint(stepLocation.get(1).asDouble(), stepLocation.get(0).asDouble());

            steps.add(new RouteStep(
                    step.path("maneuver").path("instruction").asText(""),
                    step.path("distance").asDouble(),
                    step.path("duration").asInt(),
                    stepGeoPoint
            ));
        }

        return new RouteResponse(
                distance,
                duration,
                geometryPoints,
                route.path("geometry").toString(),
                steps
        );
    }

    private GeocodeResponse parseGeocodeResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode feature = root.path("features").get(0);
        if (feature == null || feature.isNull()) {
            throw new BusinessException("Address not found");
        }

        JsonNode center = feature.path("center");
        GeoPoint location = new GeoPoint(center.get(1).asDouble(), center.get(0).asDouble());
        String formattedAddress = feature.path("place_name").asText("");

        return new GeocodeResponse(location, formattedAddress);
    }

    /**
     * Search places by query
     */
    public List<PlaceResponse> searchPlaces(PlaceSearchRequest request) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/geocoding/v5/mapbox.places/{query}.json")
                    .buildAndExpand(request.getQuery())
                    .toUriString();

            url += "?access_token=" + accessToken + "&limit=" + request.getLimit();
            
            if (request.getProximity() != null && !request.getProximity().isEmpty()) {
                url += "&proximity=" + request.getProximity();
            }

            log.debug("Calling Mapbox Places Search API: {}", url.replace(accessToken, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parsePlacesSearchResponse(response.getBody());
            }

            throw new BusinessException("Failed to search places");

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Mapbox Places Search API error: {}", e.getMessage());
            throw new BusinessException("Map service temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error calling Mapbox Places Search API", e);
            throw new BusinessException("Failed to search places.");
        }
    }

    /**
     * Get distance matrix between multiple origins and destinations
     */
    public DistanceMatrixResponse getDistanceMatrix(DistanceMatrixRequest request) {
        try {
            // Format coordinates: origin1,origin2;destination1,destination2
            StringBuilder coordinatesBuilder = new StringBuilder();
            
            // Add origins
            for (int i = 0; i < request.getOrigins().size(); i++) {
                if (i > 0) coordinatesBuilder.append(";");
                GeoPoint origin = request.getOrigins().get(i);
                coordinatesBuilder.append(origin.getLongitude()).append(",").append(origin.getLatitude());
            }
            
            // Add destinations
            coordinatesBuilder.append(";");
            for (int i = 0; i < request.getDestinations().size(); i++) {
                if (i > 0) coordinatesBuilder.append(";");
                GeoPoint dest = request.getDestinations().get(i);
                coordinatesBuilder.append(dest.getLongitude()).append(",").append(dest.getLatitude());
            }

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/directions-matrix/v1/mapbox/{profile}/{coordinates}")
                    .buildAndExpand(request.getProfile(), coordinatesBuilder.toString())
                    .toUriString();

            url += "?access_token=" + accessToken + "&sources=" + getSourcesString(request.getOrigins().size()) 
                    + "&destinations=" + getDestinationsString(request.getOrigins().size(), request.getDestinations().size());

            log.debug("Calling Mapbox Distance Matrix API: {}", url.replace(accessToken, "***"));

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseDistanceMatrixResponse(response.getBody(), request.getOrigins().size(), request.getDestinations().size());
            }

            throw new BusinessException("Failed to get distance matrix");

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Mapbox Distance Matrix API error: {}", e.getMessage());
            throw new BusinessException("Map service temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Error calling Mapbox Distance Matrix API", e);
            throw new BusinessException("Failed to calculate distance matrix.");
        }
    }

    private String getSourcesString(int originsCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < originsCount; i++) {
            if (i > 0) sb.append(";");
            sb.append(i);
        }
        return sb.toString();
    }

    private String getDestinationsString(int originsCount, int destinationsCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = originsCount; i < originsCount + destinationsCount; i++) {
            if (i > originsCount) sb.append(";");
            sb.append(i);
        }
        return sb.toString();
    }

    private List<PlaceResponse> parsePlacesSearchResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode features = root.path("features");
        
        List<PlaceResponse> places = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode center = feature.path("center");
            GeoPoint location = new GeoPoint(center.get(1).asDouble(), center.get(0).asDouble());
            
            String id = feature.path("id").asText("");
            String name = feature.path("text").asText("");
            String address = feature.path("place_name").asText("");
            String category = "";
            String type = feature.path("place_type").get(0).asText("");
            
            // Try to get category from properties
            JsonNode properties = feature.path("properties");
            if (properties.has("category")) {
                category = properties.path("category").asText("");
            }

            places.add(new PlaceResponse(id, name, address, location, category, type));
        }

        return places;
    }

    private DistanceMatrixResponse parseDistanceMatrixResponse(String json, int originsCount, int destinationsCount) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode durations = root.path("durations");
        JsonNode distances = root.path("distances");

        List<List<DistanceMatrixResponse.DistanceMatrixElement>> matrix = new ArrayList<>();

        for (int i = 0; i < originsCount; i++) {
            List<DistanceMatrixResponse.DistanceMatrixElement> row = new ArrayList<>();
            JsonNode durationRow = durations.get(i);
            JsonNode distanceRow = distances.get(i);

            for (int j = 0; j < destinationsCount; j++) {
                double distance = distanceRow.get(j).asDouble();
                int duration = durationRow.get(j).asInt();
                row.add(new DistanceMatrixResponse.DistanceMatrixElement(distance, duration));
            }
            matrix.add(row);
        }

        return new DistanceMatrixResponse(matrix);
    }

    private ReverseGeocodeResponse parseReverseGeocodeResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode feature = root.path("features").get(0);
        if (feature == null || feature.isNull()) {
            throw new BusinessException("Location not found");
        }

        JsonNode context = feature.path("context");
        String address = feature.path("place_name").asText("");
        String street = "";
        String district = "";
        String city = "";
        String country = "";

        for (JsonNode ctx : context) {
            String id = ctx.path("id").asText("");
            String text = ctx.path("text").asText("");
            if (id.startsWith("place")) {
                city = text;
            } else if (id.startsWith("district")) {
                district = text;
            } else if (id.startsWith("country")) {
                country = text;
            }
        }

        return new ReverseGeocodeResponse(address, street, district, city, country);
    }
}

