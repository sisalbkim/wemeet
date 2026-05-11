package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {
    long deleteByUserId(String userId);
}
