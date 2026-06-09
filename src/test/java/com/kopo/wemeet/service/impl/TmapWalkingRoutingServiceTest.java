package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.OpenApiProperties;
import com.kopo.wemeet.config.OpenApiRestClientFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TmapWalkingRoutingServiceTest {
    @Test
    void parsePedestrianRouteExtractsMinutesAndPolyline() {
        OpenApiProperties properties = new OpenApiProperties();
        TmapWalkingRoutingService service = new TmapWalkingRoutingService(
                properties,
                new ObjectMapper(),
                new OpenApiRestClientFactory(properties)
        );
        String responseBody = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [126.97833770468128, 37.56646579854465]
                      },
                      "properties": {
                        "totalDistance": 744,
                        "totalTime": 713,
                        "pointType": "SP"
                      }
                    },
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [
                          [126.97833770468128, 37.56646579854465],
                          [126.97849880213307, 37.56642969452213],
                          [126.97905986211187, 37.56642970458751]
                        ]
                      },
                      "properties": {
                        "distance": 66,
                        "time": 55
                      }
                    },
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [
                          [126.97905986211187, 37.56642970458751],
                          [126.97926260466089, 37.56702130609981]
                        ]
                      },
                      "properties": {
                        "distance": 53,
                        "time": 68
                      }
                    }
                  ]
                }
                """;

        var route = service.parsePedestrianRoute(responseBody);

        assertNotNull(route);
        assertEquals(12, route.minutes());
        assertEquals(4, route.routePath().size());
        assertEquals(37.56646579854465, route.routePath().get(0).latitude());
        assertEquals(126.97833770468128, route.routePath().get(0).longitude());
        assertEquals(37.56702130609981, route.routePath().get(3).latitude());
        assertEquals(126.97926260466089, route.routePath().get(3).longitude());
    }
}
