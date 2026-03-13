package com.kopo.wemeet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi")
public class OpenApiProperties {

    private boolean enabled = true;
    private String userAgent = "WeMeet/1.0";
    private String nominatimBaseUrl = "https://nominatim.openstreetmap.org";
    private String osrmBaseUrl = "https://router.project-osrm.org";
    private String routeProfile = "driving";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
}
