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
}