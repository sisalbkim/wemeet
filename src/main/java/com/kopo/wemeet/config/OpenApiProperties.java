package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenApiProperties는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@ConfigurationProperties(prefix = "app.openapi")
public class OpenApiProperties {
    // 외부 지도 API 호출에 필요한 설정값을 application.properties에서 바인딩한다.

    private boolean enabled = true;
    private int connectTimeoutMillis = 3000;
    private int readTimeoutMillis = 5000;
    private String userAgent = "WeMeet/1.0";
    private String nominatimBaseUrl = "https://nominatim.openstreetmap.org";
    private String osrmBaseUrl = "https://router.project-osrm.org";
    private String routeProfile = "driving";
    private String walkingRouteProfile = "foot";
    private NaverSearch naverSearch = new NaverSearch();
    private NaverMaps naverMaps = new NaverMaps();
    private Odsay odsay = new Odsay();
    private Tmap tmap = new Tmap();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getNominatimBaseUrl() {
        return nominatimBaseUrl;
    }

    public void setNominatimBaseUrl(String nominatimBaseUrl) {
        this.nominatimBaseUrl = nominatimBaseUrl;
    }

    public String getOsrmBaseUrl() {
        return osrmBaseUrl;
    }

    public void setOsrmBaseUrl(String osrmBaseUrl) {
        this.osrmBaseUrl = osrmBaseUrl;
    }

    public String getRouteProfile() {
        return routeProfile;
    }

    public void setRouteProfile(String routeProfile) {
        this.routeProfile = routeProfile;
    }

    public String getWalkingRouteProfile() {
        return walkingRouteProfile;
    }

    public void setWalkingRouteProfile(String walkingRouteProfile) {
        this.walkingRouteProfile = walkingRouteProfile;
    }

    public NaverSearch getNaverSearch() {
        return naverSearch;
    }

    public void setNaverSearch(NaverSearch naverSearch) {
        this.naverSearch = naverSearch;
    }

    public NaverMaps getNaverMaps() {
        return naverMaps;
    }

    public void setNaverMaps(NaverMaps naverMaps) {
        this.naverMaps = naverMaps;
    }

    public Odsay getOdsay() {
        return odsay;
    }

    public void setOdsay(Odsay odsay) {
        this.odsay = odsay;
    }

    public Tmap getTmap() {
        return tmap;
    }

    public void setTmap(Tmap tmap) {
        this.tmap = tmap;
    }

    public boolean isNaverSearchConfigured() {
        return naverSearch != null
                && naverSearch.getClientId() != null
                && !naverSearch.getClientId().isBlank()
                && naverSearch.getClientSecret() != null
                && !naverSearch.getClientSecret().isBlank();
    }

    public boolean isNaverMapsConfigured() {
        return naverMaps != null
                && naverMaps.getApiKeyId() != null
                && !naverMaps.getApiKeyId().isBlank()
                && naverMaps.getApiKey() != null
                && !naverMaps.getApiKey().isBlank();
    }

    public boolean isOdsayConfigured() {
        return odsay != null
                && odsay.isEnabled()
                && odsay.getApiKey() != null
                && !odsay.getApiKey().isBlank();
    }

    public boolean isTmapConfigured() {
        return tmap != null
                && tmap.isEnabled()
                && tmap.getAppKey() != null
                && !tmap.getAppKey().isBlank();
    }

    public static class NaverSearch {
        // 네이버 지역검색(Local Search) 호출용 설정이다.
        private String baseUrl = "https://openapi.naver.com";
        private String clientId = "";
        private String clientSecret = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    public static class NaverMaps {
        // 네이버 지도 Directions/Geocode/ReverseGeocode 호출용 설정이다.
        private String baseUrl = "https://maps.apigw.ntruss.com";
        private String apiKeyId = "";
        private String apiKey = "";
        private String directionsPath = "/map-direction/v1/driving";
        private String geocodePath = "/map-geocode/v2/geocode";
        private String reverseGeocodePath = "/map-reversegeocode/v2/gc";
        private String routeOption = "traoptimal";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKeyId() {
            return apiKeyId;
        }

        public void setApiKeyId(String apiKeyId) {
            this.apiKeyId = apiKeyId;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getDirectionsPath() {
            return directionsPath;
        }

        public void setDirectionsPath(String directionsPath) {
            this.directionsPath = directionsPath;
        }

        public String getGeocodePath() {
            return geocodePath;
        }

        public void setGeocodePath(String geocodePath) {
            this.geocodePath = geocodePath;
        }

        public String getReverseGeocodePath() {
            return reverseGeocodePath;
        }

        public void setReverseGeocodePath(String reverseGeocodePath) {
            this.reverseGeocodePath = reverseGeocodePath;
        }

        public String getRouteOption() {
            return routeOption;
        }

        public void setRouteOption(String routeOption) {
            this.routeOption = routeOption;
        }
    }

    public static class Odsay {
        private boolean enabled = true;
        private String baseUrl = "https://api.odsay.com";
        private String apiKey = "";
        private String transitPath = "/v1/api/searchPubTransPathT";
        private String loadLanePath = "/v1/api/loadLane";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getTransitPath() {
            return transitPath;
        }

        public void setTransitPath(String transitPath) {
            this.transitPath = transitPath;
        }

        public String getLoadLanePath() {
            return loadLanePath;
        }

        public void setLoadLanePath(String loadLanePath) {
            this.loadLanePath = loadLanePath;
        }
    }

    public static class Tmap {
        private boolean enabled = true;
        private String baseUrl = "https://apis.openapi.sk.com";
        private String appKey = "";
        private String pedestrianPath = "/tmap/routes/pedestrian";
        private String version = "1";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getPedestrianPath() {
            return pedestrianPath;
        }

        public void setPedestrianPath(String pedestrianPath) {
            this.pedestrianPath = pedestrianPath;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
