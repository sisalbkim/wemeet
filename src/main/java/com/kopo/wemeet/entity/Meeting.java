package com.kopo.wemeet.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "meeting")
public class Meeting {
    // 모임의 기본 정보와 참가자 목록을 함께 관리하는 엔티티다.

    @Id
    @Column(length = 40, nullable = false)
    private String id;

    @Column(length = 120, nullable = false)
    private String title;

    @Column(length = 1000, nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDate meetingDate;

    @Column(length = 30, nullable = false)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private AppUser host;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<MeetingParticipant> participants = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Meeting() {
    }

    public Meeting(String id, String title, String description, LocalDate meetingDate, String category, AppUser host) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.meetingDate = meetingDate;
        this.category = category;
        this.host = host;
    }

    @PrePersist
    void onCreate() {
        // 생성 시각은 모임 목록 정렬이나 최근 생성 확인에 사용한다.
        createdAt = LocalDateTime.now();
    }

    public void addParticipant(AppUser user, String role) {
        // 참가자를 엔티티 내부에서 추가해 meeting 참조가 빠지지 않게 한다.
        participants.add(new MeetingParticipant(this, user, role));
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getMeetingDate() {
        return meetingDate;
    }

    public String getCategory() {
        return category;
    }

    public AppUser getHost() {
        return host;
    }

    public List<MeetingParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
