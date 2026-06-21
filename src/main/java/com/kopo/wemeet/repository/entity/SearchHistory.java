package com.kopo.wemeet.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * SearchHistory는 데이터베이스 테이블과 매핑되는 JPA 엔티티입니다.
 */
@Entity
@Table(name = "search_history")
public class SearchHistory {
    // 사용자가 어떤 조건으로 추천/검색을 했는지 남기는 엔티티다.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(length = 255, nullable = false)
    private String query;

    @Column(length = 30, nullable = false)
    private String category;

    @Column(nullable = false)
    private LocalDateTime searchedAt;

    protected SearchHistory() {
    }

    public SearchHistory(AppUser user, String query, String category) {
        this.user = user;
        this.query = query;
        this.category = category;
    }

    @PrePersist
    void onCreate() {
        // 검색한 시각을 자동 저장해 최신순 목록을 쉽게 만들 수 있게 한다.
        searchedAt = LocalDateTime.now();
    }

    public String getQuery() {
        return query;
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }
}
