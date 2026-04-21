package com.kopo.wemeet.repository;

import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.FriendRelation;
import com.kopo.wemeet.repository.entity.FriendRelation.FriendStatus;
import com.kopo.wemeet.repository.entity.Meeting;
import com.kopo.wemeet.repository.entity.MeetingParticipant;
import com.kopo.wemeet.repository.entity.SearchHistory;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class WemeetDataStore {
    // 친구, 기록, 모임 데이터를 DB에 읽고 쓰는 저장소 facade다.

    private final AppUserRepository userRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final MeetingRepository meetingRepository;
    private final PasswordEncoder passwordEncoder;

    public WemeetDataStore(
            AppUserRepository userRepository,
            FriendRelationRepository friendRelationRepository,
            SearchHistoryRepository searchHistoryRepository,
            MeetingRepository meetingRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.meetingRepository = meetingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    @Transactional
    void ensureDemoFriendExists() {
        // 친구 코드 테스트를 바로 해볼 수 있도록 데모 친구 1명을 보장한다.
        if (userRepository.existsByFriendCode("AAAAAA")) {
            return;
        }

        AppUser demoFriend = new AppUser(
                "user-demo-aaaaaa",
                "demo_friend_aaaaaa",
                "테스트 친구",
                "aaaaaa@wemeet.local",
                passwordEncoder.encode("Passw0rd!"),
                "AAAAAA",
                "서울특별시 강남구 테헤란로 212"
        );
        userRepository.save(demoFriend);
    }

    public Optional<UserAccount> findById(String userId) {
        return userRepository.findById(userId).map(this::toUserAccount);
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listFriends(String userId) {
        // 친구 엔티티를 화면/API 공용으로 쓰는 간단한 읽기 모델로 바꿔서 반환한다.
        return friendRelationRepository.findAllByUserIdAndStatusOrderByFriend_NicknameAsc(userId, FriendStatus.ACCEPTED).stream()
                .sorted(Comparator
                        .comparing(FriendRelation::isFavorite).reversed()
                        .thenComparing(relation -> relation.getFriend().getNickname()))
                .map(relation -> toUserAccount(relation.getFriend(), relation.isFavorite()))
                .toList();
    }

    @Transactional
    public UserAccount addFriendByCode(String userId, String friendCode) {
        // 친구코드는 상대방을 쉽게 찾기 위한 사용자 입력용 키다. 실제 친구 관계는 상대가 승인한 뒤 만들어진다.
        if (friendCode == null || friendCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "friendCode is required");
        }

        AppUser user = requireUser(userId);
        AppUser friend = userRepository.findByFriendCode(friendCode.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend code not found"));

        if (user.getId().equals(friend.getId())) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot add yourself");
        }

        if (friendRelationRepository.existsByUserIdAndFriendIdAndStatus(user.getId(), friend.getId(), FriendStatus.ACCEPTED)
                || friendRelationRepository.existsByUserIdAndFriendIdAndStatus(friend.getId(), user.getId(), FriendStatus.ACCEPTED)) {
            throw new ResponseStatusException(CONFLICT, "Friend already added");
        }

        if (friendRelationRepository.existsByUserIdAndFriendIdAndStatus(user.getId(), friend.getId(), FriendStatus.PENDING)) {
            throw new ResponseStatusException(CONFLICT, "Friend request already sent");
        }

        if (friendRelationRepository.existsByUserIdAndFriendIdAndStatus(friend.getId(), user.getId(), FriendStatus.PENDING)) {
            throw new ResponseStatusException(CONFLICT, "Friend request already received");
        }

        friendRelationRepository.save(FriendRelation.pending(user, friend));
        return toUserAccount(friend);
    }

    @Transactional(readOnly = true)
    public List<FriendRequestEntry> listIncomingFriendRequests(String userId) {
        return friendRelationRepository.findAllByFriendIdAndStatusOrderByUser_NicknameAsc(userId, FriendStatus.PENDING).stream()
                .map(relation -> toFriendRequestEntry(relation.getUser(), relation.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestEntry> listOutgoingFriendRequests(String userId) {
        return friendRelationRepository.findAllByUserIdAndStatusOrderByFriend_NicknameAsc(userId, FriendStatus.PENDING).stream()
                .map(relation -> toFriendRequestEntry(relation.getFriend(), relation.getCreatedAt()))
                .toList();
    }

    @Transactional
    public UserAccount respondFriendRequest(String recipientUserId, String requesterUserId, boolean approve) {
        if (requesterUserId == null || requesterUserId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "requesterId is required");
        }

        AppUser recipient = requireUser(recipientUserId);
        AppUser requester = requireUser(requesterUserId);
        FriendRelation request = friendRelationRepository
                .findByUserIdAndFriendIdAndStatus(requester.getId(), recipient.getId(), FriendStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend request not found"));

        if (!approve) {
            friendRelationRepository.delete(request);
            return toUserAccount(requester);
        }

        request.accept();
        if (!friendRelationRepository.existsByUserIdAndFriendIdAndStatus(recipient.getId(), requester.getId(), FriendStatus.ACCEPTED)) {
            friendRelationRepository.save(new FriendRelation(recipient, requester));
        }
        return toUserAccount(requester);
    }

    @Transactional
    public void updateFriendFavorite(String userId, String friendId, boolean favorite) {
        FriendRelation relation = friendRelationRepository.findByUserIdAndFriendIdAndStatus(userId, friendId, FriendStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Friend relation not found"));
        relation.changeFavorite(favorite);
    }

    @Transactional
    public MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            List<String> participantIds
    ) {
        // 모임 생성 시 host는 항상 참가자 목록에 포함되도록 강제한다.
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
                host
        );
        for (AppUser participant : participants) {
            String role = participant.getId().equals(host.getId()) ? "HOST" : "PARTICIPANT";
            meeting.addParticipant(participant, role);
        }

        Meeting saved = meetingRepository.save(meeting);
        return toMeetingRecord(saved);
    }

    @Transactional(readOnly = true)
    public List<MeetingRecord> listMeetingsForUser(String userId) {
        return meetingRepository.findAllParticipatingByUserId(userId).stream()
                .map(this::toMeetingRecord)
                .toList();
    }

    @Transactional
    public void appendHistory(String userId, String query, String category) {
        // 빈 검색어는 저장하지 않아 목록이 의미 없는 데이터로 채워지지 않게 한다.
        if (query == null || query.isBlank() || category == null || category.isBlank()) {
            return;
        }
        searchHistoryRepository.save(new SearchHistory(requireUser(userId), query.trim(), category.trim()));
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryEntry> listHistory(String userId) {
        return searchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(userId).stream()
                .map(entry -> new SearchHistoryEntry(entry.getId(), entry.getQuery(), entry.getCategory(), entry.getSearchedAt()))
                .toList();
    }

    @Transactional
    public void clearHistory(String userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }

    @Transactional
    public void removeHistory(String userId, Long historyId) {
        if (historyId == null) {
            return;
        }
        searchHistoryRepository.deleteByIdAndUserId(historyId, userId);
    }

    private AppUser requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found: " + userId));
    }

    private UserAccount toUserAccount(AppUser user) {
        return toUserAccount(user, false);
    }

    private UserAccount toUserAccount(AppUser user, boolean favorite) {
        // 영속 엔티티 전체 대신 외부에 노출해도 되는 읽기 전용 값만 남긴다.
        LocalDate joinedOn = user.getCreatedAt() == null ? LocalDate.now() : user.getCreatedAt().toLocalDate();
        return new UserAccount(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                "",
                user.getFriendCode(),
                user.getBaseAddress(),
                joinedOn,
                favorite
        );
    }

    private FriendRequestEntry toFriendRequestEntry(AppUser user, LocalDateTime requestedAt) {
        LocalDate requestedOn = requestedAt == null ? LocalDate.now() : requestedAt.toLocalDate();
        return new FriendRequestEntry(
                user.getId(),
                user.getNickname(),
                user.getLoginId(),
                user.getBaseAddress(),
                requestedOn
        );
    }

    private MeetingRecord toMeetingRecord(Meeting meeting) {
        // HOST를 먼저 보여주고 나머지 참가자는 가입 순서대로 유지하기 위한 정렬이다.
        List<String> participantIds = meeting.getParticipants().stream()
                .sorted(Comparator
                        .comparing((MeetingParticipant participant) -> !"HOST".equals(participant.getRole()))
                        .thenComparing(MeetingParticipant::getCreatedAt))
                .map(participant -> participant.getUser().getId())
                .collect(Collectors.toList());

        return new MeetingRecord(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getMeetingDate(),
                meeting.getMeetingTime(),
                meeting.getCategory(),
                meeting.getHost().getId(),
                participantIds,
                meeting.getCreatedAt()
        );
    }

    public record UserAccount(
            String id,
            String nickname,
            String loginId,
            String password,
            String friendCode,
            String baseAddress,
            LocalDate joinedOn,
            boolean favorite
    ) {
        // 인증/추천/화면 서비스가 공통으로 쓰는 사용자 읽기 모델이다.
    }

    public record MeetingRecord(
            String id,
            String title,
            String description,
            LocalDate meetingDate,
            LocalTime meetingTime,
            String category,
            String hostUserId,
            List<String> participantIds,
            LocalDateTime createdAt
    ) {
        // 모임 엔티티를 화면/API 응답 직전에 가볍게 옮겨 담은 record다.
    }

    public record SearchHistoryEntry(
            Long id,
            String query,
            String category,
            LocalDateTime searchedAt
    ) {
        // 히스토리 엔티티에서 목록 출력에 필요한 값만 뽑은 record다.
    }

    public record FriendRequestEntry(
            String id,
            String nickname,
            String loginId,
            String baseAddress,
            LocalDate requestedOn
    ) {
        // 친구 요청 목록 출력에 필요한 상대 사용자 값이다.
    }
}
