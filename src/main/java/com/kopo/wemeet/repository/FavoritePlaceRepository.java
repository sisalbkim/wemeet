package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.FavoritePlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoritePlaceRepository extends JpaRepository<FavoritePlace, Long> {

    // 특정 사용자의 즐겨찾기 목록을 최근 저장순으로 조회
    List<FavoritePlace> findAllByUserIdOrderByCreatedAtDesc(String userId);

    // 특정 즐겨찾기를 사용자 기준으로 삭제
    long deleteByIdAndUserId(Long id, String userId);
}