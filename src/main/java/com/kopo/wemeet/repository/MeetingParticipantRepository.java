package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * MeetingParticipantRepository는 엔티티 조회와 저장에 필요한 Spring Data JPA 접근 메서드를 제공합니다.
 */
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {
    long deleteByUserId(String userId);
}
