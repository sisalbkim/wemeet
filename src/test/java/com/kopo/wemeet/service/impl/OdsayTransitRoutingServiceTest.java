package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.config.OpenApiProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OdsayTransitRoutingServiceTest {
    // ODSAY 대중교통 라우팅 서비스의 응답 파싱과 실패 처리를 검증하는 테스트다.

    @Test
    void parseTransitPathExtractsFirstRouteInfo() {
        OdsayTransitRoutingService service = new OdsayTransitRoutingService(new OpenApiProperties(), new ObjectMapper());
        String responseBody = """
                {
                  "result": {
                    "path": [
                      {
                        "pathType": 1,
                        "info": {
                          "mapObj": "2:2:210:204@868:1:28:31",
                          "totalTime": 34
                        }
                      }
                    ]
                  }
                }
                """;

        var searchPathInfo = service.parseTransitPath(responseBody);

        assertNotNull(searchPathInfo);
        assertEquals(34, searchPathInfo.totalTime());
        assertEquals("2:2:210:204@868:1:28:31", searchPathInfo.mapObj());
    }

    @Test
    void parseLanePathSupportsSingleLaneObjectResponse() {
        OdsayTransitRoutingService service = new OdsayTransitRoutingService(new OpenApiProperties(), new ObjectMapper());
        String responseBody = """
                {
                  "result": {
                    "lane": {
                      "class": 2,
                      "type": 2,
                      "section": [
                        {
                          "graphPos": [
                            { "x": 126.902677, "y": 37.534871 },
                            { "x": 126.914520, "y": 37.549938 }
                          ]
                        }
                      ]
                    }
                  }
                }
                """;

        var routePath = service.parseLanePath(responseBody);

        assertEquals(2, routePath.size());
        assertEquals(37.534871, routePath.get(0).latitude());
        assertEquals(126.902677, routePath.get(0).longitude());
        assertEquals(37.549938, routePath.get(1).latitude());
        assertEquals(126.914520, routePath.get(1).longitude());
    }
}
