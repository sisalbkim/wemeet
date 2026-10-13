
package com.kopo.wemeet.service.impl;

public final class KmaGridConverter {

    private KmaGridConverter() {
    }

    public record Grid(int nx, int ny) {
    }

    public static Grid convert(double latitude, double longitude) {
        double re = 6371.00877 / 5.0;
        double grid = 5.0;
        double slat1 = Math.toRadians(30.0);
        double slat2 = Math.toRadians(60.0);
        double olon = Math.toRadians(126.0);
        double olat = Math.toRadians(38.0);
        double xo = 43.0;
        double yo = 136.0;

        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
                / Math.log(Math.tan(Math.PI / 4 + slat2 / 2)
                / Math.tan(Math.PI / 4 + slat1 / 2));

        double sf = Math.pow(Math.tan(Math.PI / 4 + slat1 / 2), sn)
                * Math.cos(slat1) / sn;

        double ro = re * sf
                / Math.pow(Math.tan(Math.PI / 4 + olat / 2), sn);

        double ra = re * sf
                / Math.pow(Math.tan(Math.PI / 4
                + Math.toRadians(latitude) / 2), sn);

        double theta = Math.toRadians(longitude) - olon;

        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }

        theta *= sn;

        int nx = (int) Math.floor(
                ra * Math.sin(theta) + xo + 0.5
        );

        int ny = (int) Math.floor(
                ro - ra * Math.cos(theta) + yo + 0.5
        );

        return new Grid(nx, ny);
    }
}
