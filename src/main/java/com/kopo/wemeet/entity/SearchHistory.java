package com.kopo.wemeet.entity;

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

@Entity
@Table(name = "search_history")
public class SearchHistory {
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
