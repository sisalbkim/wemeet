package com.kopo.wemeet.repository;

import com.kopo.wemeet.entity.Meeting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
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
            order by m.meetingDate, m.createdAt
            """)
    List<Meeting> findAllParticipatingByUserId(String userId);
}
