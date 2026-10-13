
package com.kopo.wemeet.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MeetingWeatherService {

    private final ApiNaverPlaceSearchService naverPlaceSearchService;
    private final KmaShortForecastService shortForecastService;
    private final KmaMidForecastService midForecastService;

    // 모임까지 남은 날짜 계산
    public long calculateDDay(LocalDate meetingDate) {
        return ChronoUnit.DAYS.between(
                LocalDate.now(),
                meetingDate
        );
    }

    // D-7부터 당일까지 날씨 조회 대상인지 확인
    public boolean isWeatherAvailable(LocalDate meetingDate) {
        if (meetingDate == null) {
            return false;
        }

        long dDay = calculateDDay(meetingDate);

        return dDay >= 0 && dDay <= 7;
    }

    // 모임 장소 주소를 위도, 경도로 변환
    public Optional<double[]> getMeetingCoordinates(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        return naverPlaceSearchService.geocodeAddress(address);
    }


    public Optional<KmaGridConverter.Grid> getMeetingGrid(String address) {
        return getMeetingCoordinates(address)
                .map(coordinates -> KmaGridConverter.convert(
                        coordinates[0],
                        coordinates[1]
                ));
    }


    public Optional<String> getMeetingWeather(
            LocalDate meetingDate,
            LocalTime meetingTime,
            String meetingAddress
    ) {
        if (meetingDate == null
                || meetingAddress == null
                || meetingAddress.isBlank()) {
            return Optional.empty();
        }

        long dDay = calculateDDay(meetingDate);

        // D-7 이전이거나 이미 지난 모임은 조회하지 않음
        if (dDay < 0 || dDay > 7) {
            return Optional.empty();
        }

        // 모임 시간이 없으면 오후 12시 기준
        LocalTime targetTime = meetingTime == null
                ? LocalTime.NOON
                : meetingTime;

        // D-3부터 당일까지 단기예보 우선 조회
        if (dDay <= 3) {
            Optional<KmaGridConverter.Grid> grid =
                    getMeetingGrid(meetingAddress);

            if (grid.isEmpty()) {
                return Optional.empty();
            }

            try {
                Optional<KmaShortForecastService.WeatherInfo> weather =
                        shortForecastService.getWeather(
                                grid.get().nx(),
                                grid.get().ny(),
                                meetingDate,
                                targetTime
                        );

                if (weather.isPresent()) {
                    return Optional.of(
                            shortForecastService.getWeatherDescription(
                                    weather.get()
                            )
                    );
                }
            } catch (RuntimeException exception) {
                // 날씨 조회 실패가 모임 목록 전체 오류로 이어지지 않도록 처리
            }

            return Optional.empty();
        }

        // D-4 ~ D-7: 중기예보

        Optional<String> regionId =
                getMidForecastRegionId(meetingAddress);

        if (regionId.isEmpty()) {
            return Optional.empty();
        }

        String regId = regionId.get();


        try {
            return midForecastService.getWeather(
                    regId,
                    meetingDate,
                    targetTime
            ).map(weather ->
                    weather.weather()
                            + " · 강수확률 "
                            + weather.rainProbability()
                            + "%"
            );
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }



    private Optional<String> getMidForecastRegionId(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        String region = address.trim();

        if (region.startsWith("서울")
                || region.startsWith("인천")
                || region.startsWith("경기")) {
            return Optional.of("11B00000");
        }

        if (region.startsWith("강원")) {
            return Optional.of("11D10000");
        }

        if (region.startsWith("대전")
                || region.startsWith("세종")
                || region.startsWith("충남")) {
            return Optional.of("11C20000");
        }

        if (region.startsWith("충북")) {
            return Optional.of("11C10000");
        }

        if (region.startsWith("광주")
                || region.startsWith("전남")) {
            return Optional.of("11F20000");
        }

        if (region.startsWith("전북")) {
            return Optional.of("11F10000");
        }

        if (region.startsWith("대구")
                || region.startsWith("경북")) {
            return Optional.of("11H10000");
        }

        if (region.startsWith("부산")
                || region.startsWith("울산")
                || region.startsWith("경남")) {
            return Optional.of("11H20000");
        }

        if (region.startsWith("제주")) {
            return Optional.of("11G00000");
        }

        return Optional.empty();
    }

}
