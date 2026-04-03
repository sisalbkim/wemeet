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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "meeting_participant",
        uniqueConstraints = @UniqueConstraint(name = "uk_meeting_participant_meeting_user", columnNames = {"meeting_id", "user_id"})
)
public class MeetingParticipant {
    // 특정 사용자가 어떤 모임에 어떤 역할로 참여하는지 나타내는 연결 엔티티다.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(length = 20, nullable = false)
    private String role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected MeetingParticipant() {
    }

    public MeetingParticipant(Meeting meeting, AppUser user, String role) {
        this.meeting = meeting;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        // 참가 시점을 저장해 HOST를 먼저, 이후 참가자를 생성순으로 정렬할 때 활용한다.
        createdAt = LocalDateTime.now();
    }

    public AppUser getUser() {
        return user;
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
