package com.kopo.wemeet.repository.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorite_place")
public class FavoritePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 장소를 즐겨찾기한 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // 장소 이름
    @Column(length = 255, nullable = false)
    private String name;

    // 장소 카테고리
    @Column(length = 255)
    private String category;

    // 지번 주소
    @Column(length = 500)
    private String address;


    // 위도
    @Column(nullable = false)
    private double latitude;

    // 경도
    @Column(nullable = false)
    private double longitude;

    // 즐겨찾기에 추가한 시간
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected FavoritePlace() {
    }

    public FavoritePlace(
            AppUser user,
            String name,
            String category,
            String address,
            double latitude,
            double longitude
    ) {
        this.user = user;
        this.name = name;
        this.category = category;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getAddress() {
        return address;
    }



    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}