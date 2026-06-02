package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.MeetingDTO;
import com.kopo.wemeet.dto.UserDTO;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.MeetingRepository;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.Meeting;
import com.kopo.wemeet.repository.entity.MeetingParticipant;
import com.kopo.wemeet.service.IMeetingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MeetingService implements IMeetingService {
    // 모임 저장과 조회 규칙을 모아두는 도메인 서비스다.

    private final AppUserRepository userRepository;
    private final MeetingRepository meetingRepository;

    public MeetingService(
            AppUserRepository userRepository,
            MeetingRepository meetingRepository
    ) {
        this.userRepository = userRepository;
        this.meetingRepository = meetingRepository;
    }

    @Transactional
    @Override
    public MeetingDTO.MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            List<String> participantIds
    ) {
        return createMeeting(hostUserId, title, description, meetingDate, meetingTime, category, "", "", "CENTER", hostUserId, null, null, participantIds);
    }

    @Transactional
    @Override
    public MeetingDTO.MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            List<String> participantIds
    ) {
        return createMeeting(
                hostUserId,
                title,
                description,
                meetingDate,
                meetingTime,
                category,
                meetingPlaceName,
                meetingPlaceAddress,
                "CENTER",
                hostUserId,
                null,
                null,
                participantIds
        );
    }

    @Transactional
    @Override
    public MeetingDTO.MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            String meetingPlaceName,
            String meetingPlaceAddress,
            String recommendationMode,
            String anchorParticipantId,
            String recommendationSnapshotJson,
            LocalDateTime recommendationSnapshotExpiresAt,
            List<String> participantIds
    ) {
        // host는 항상 참가자로 포함하고, 참가자 중 존재하지 않는 사용자는 허용하지 않는다.
        if (title == null || title.isBlank() || meetingDate == null || category == null || category.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "title, meetingDate, and category are required");
        }

        AppUser host = requireUser(hostUserId);
        LinkedHashSet<String> uniqueParticipantIds = new LinkedHashSet<>();
        uniqueParticipantIds.add(hostUserId);
        if (participantIds != null) {
            uniqueParticipantIds.addAll(participantIds);
        }

        List<AppUser> participants = uniqueParticipantIds.stream()
                .map(this::requireUser)
                .toList();

        Meeting meeting = new Meeting(
                "meeting-" + UUID.randomUUID().toString().substring(0, 8),
                title.trim(),
                description == null ? "" : description.trim(),
                meetingDate,
                meetingTime,
                category.trim(),
                meetingPlaceName == null ? "" : meetingPlaceName.trim(),
                meetingPlaceAddress == null ? "" : meetingPlaceAddress.trim(),
                host,
                recommendationMode == null || recommendationMode.isBlank() ? "CENTER" : recommendationMode.trim(),
                anchorParticipantId == null || anchorParticipantId.isBlank() ? hostUserId : anchorParticipantId.trim(),
                recommendationSnapshotJson == null || recommendationSnapshotJson.isBlank() ? null : recommendationSnapshotJson,
                recommendationSnapshotExpiresAt
        );
        for (AppUser participant : participants) {
            String role = participant.getId().equals(host.getId()) ? "HOST" : "PARTICIPANT";
            meeting.addParticipant(participant, role);
        }

        return toMeetingRecord(meetingRepository.save(meeting));
    }

    @Transactional(readOnly = true)
    @Override
    public List<MeetingDTO.MeetingRecord> listMeetingsForUser(String userId) {
        return meetingRepository.findAllParticipatingByUserId(userId).stream()
                .map(this::toMeetingRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<MeetingDTO.MeetingRecord> listMeetingsCreatedByUser(String userId) {
        return meetingRepository.findAllCreatedByUserId(userId).stream()
                .map(this::toMeetingRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<MeetingDTO.MeetingRecord> findMeetingCreatedByUser(String userId, String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return Optional.empty();
        }

        return meetingRepository.findById(meetingId)
                .filter(meeting -> meeting.getHost().getId().equals(userId))
                .map(this::toMeetingRecord);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<MeetingDTO.MeetingRecord> findMeetingForUser(String userId, String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return Optional.empty();
        }

        return meetingRepository.findById(meetingId)
                .filter(meeting -> meeting.getParticipants().stream()
                        .anyMatch(participant -> participant.getUser().getId().equals(userId)))
                .map(this::toMeetingRecord);
    }

    @Transactional
    @Override
    public void deleteMeetingCreatedByUser(String userId, String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "meetingId is required");
        }
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Meeting not found"));
        if (!meeting.getHost().getId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Only the host can delete this meeting");
        }
        meetingRepository.delete(meeting);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MeetingDTO.MeetingResponse> getApiMeetings(String userId) {
        return listMeetingsForUser(userId).stream()
                .map(this::toMeetingResponse)
                .toList();
    }

    @Transactional
    @Override
    public MeetingDTO.MeetingResponse createApiMeeting(String requesterId, MeetingDTO.MeetingCreateRequest request) {
        LocalDate meetingDate = parseMeetingDate(request.meetingDate());
        LocalTime meetingTime = parseMeetingTime(request.meetingTime());
        MeetingDTO.MeetingRecord meeting = createMeeting(
                requesterId,
                request.title(),
                request.description(),
                meetingDate,
                meetingTime,
                request.category(),
                request.meetingPlaceName(),
                request.meetingPlaceAddress(),
                request.participantIds()
        );
        return toMeetingResponse(meeting);
    }

    private MeetingDTO.MeetingResponse toMeetingResponse(MeetingDTO.MeetingRecord meeting) {
        AppUser host = requireUser(meeting.hostUserId());
        List<UserDTO.UserResponse> participants = meeting.participantIds().stream()
                .map(this::requireUser)
                .map(this::toUserResponse)
                .toList();

        return new MeetingDTO.MeetingResponse(
                meeting.id(),
                meeting.title(),
                meeting.description(),
                meeting.meetingDate().toString(),
                meeting.meetingTime() == null ? "" : meeting.meetingTime().toString(),
                meeting.category(),
                meeting.meetingPlaceName(),
                meeting.meetingPlaceAddress(),
                toUserResponse(host),
                participants
        );
    }

    private UserDTO.UserResponse toUserResponse(AppUser user) {
        return new UserDTO.UserResponse(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                user.getEmail(),
                user.getFriendCode(),
                user.getBaseAddress()
        );
    }

    private LocalDate parseMeetingDate(String meetingDate) {
        try {
            return LocalDate.parse(meetingDate);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "meetingDate must be ISO-8601 format (yyyy-MM-dd)");
        }
    }

    private LocalTime parseMeetingTime(String meetingTime) {
        if (meetingTime == null || meetingTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(meetingTime);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "meetingTime must be ISO-8601 format (HH:mm)");
        }
    }

    private AppUser requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + userId));
    }

    private MeetingDTO.MeetingRecord toMeetingRecord(Meeting meeting) {
        List<String> participantIds = meeting.getParticipants().stream()
                .sorted(Comparator
                        .comparing((MeetingParticipant participant) -> !"HOST".equals(participant.getRole()))
                        .thenComparing(MeetingParticipant::getCreatedAt))
                .map(participant -> participant.getUser().getId())
                .collect(Collectors.toList());

        return new MeetingDTO.MeetingRecord(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getMeetingDate(),
                meeting.getMeetingTime(),
                meeting.getCategory(),
                meeting.getMeetingPlaceName(),
                meeting.getMeetingPlaceAddress(),
                meeting.getRecommendationMode(),
                meeting.getAnchorParticipantId(),
                meeting.getRecommendationSnapshotJson(),
                meeting.getRecommendationSnapshotExpiresAt(),
                meeting.getHost().getId(),
                participantIds,
                meeting.getCreatedAt()
        );
    }
}
