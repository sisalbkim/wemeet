package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.FavoritePlaceRepository;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.FavoritePlace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class FavoritePlaceService {

    private final AppUserRepository userRepository;
    private final FavoritePlaceRepository favoritePlaceRepository;

    // 즐겨찾기 장소 저장
    @Transactional
    public void addFavorite(
            String userId,
            String name,
            String category,
            String address,
            double latitude,
            double longitude
    ) {
        AppUser user = requireUser(userId);

        FavoritePlace favoritePlace = new FavoritePlace(
                user,
                name,
                category,
                address,
                latitude,
                longitude
        );

        favoritePlaceRepository.save(favoritePlace);
    }

    // userId로 실제 사용자를 찾음
    private AppUser requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                NOT_FOUND,
                                "User not found: " + userId
                        )
                );
    }

    // 즐겨찾기 등록 / 해제
    @Transactional
    public boolean toggleFavorite(
            String userId,
            String name,
            String category,
            String address,
            double latitude,
            double longitude
    ) {
        // 이미 즐겨찾기라면 삭제
        if (favoritePlaceRepository.existsByUserIdAndLatitudeAndLongitude(
                userId, latitude, longitude)) {

            favoritePlaceRepository.deleteByUserIdAndLatitudeAndLongitude(
                    userId, latitude, longitude);

            return false; // ☆ 즐겨찾기 해제
        }

        // 즐겨찾기가 아니라면 새로 저장
        AppUser user = requireUser(userId);

        FavoritePlace favoritePlace = new FavoritePlace(
                user,
                name,
                category,
                address,
                latitude,
                longitude
        );

        favoritePlaceRepository.save(favoritePlace);

        return true; // ★ 즐겨찾기 등록
    }
    // 현재 장소가 즐겨찾기에 등록되어 있는지 확인
    @Transactional(readOnly = true)
    public boolean isFavorite(
            String userId,
            double latitude,
            double longitude
    ) {
        return favoritePlaceRepository.existsByUserIdAndLatitudeAndLongitude(
                userId,
                latitude,
                longitude
        );
    }
}