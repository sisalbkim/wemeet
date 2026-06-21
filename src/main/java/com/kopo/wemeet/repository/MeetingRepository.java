package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.Meeting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * MeetingRepository는 엔티티 조회와 저장에 필요한 Spring Data JPA 접근 메서드를 제공합니다.
 */
public interface MeetingRepository extends JpaRepository<Meeting, String> {
    // 참가자 기준으로 모임을 조회할 때 host/participants까지 한 번에 가져오도록 EntityGraph를 사용한다.

    @EntityGraph(attributePaths = {"host", "participants", "participants.user"})
    @Query("""
            select distinct m
            from Meeting m
            left join m.participants p
            where exists (
                select 1
                from MeetingParticipant mp
                where mp.meeting = m and mp.user.id = :userId
            )
            order by m.meetingDate, m.meetingTime, m.createdAt
            """)
    List<Meeting> findAllParticipatingByUserId(String userId);

    @EntityGraph(attributePaths = {"host", "participants", "participants.user"})
    @Query("""
            select distinct m
            from Meeting m
            left join m.participants p
            where m.host.id = :userId
            order by m.meetingDate desc, m.meetingTime desc, m.createdAt desc
            """)
    List<Meeting> findAllCreatedByUserId(String userId);
}
