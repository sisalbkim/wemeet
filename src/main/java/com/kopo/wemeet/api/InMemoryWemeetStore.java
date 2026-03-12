package com.kopo.wemeet.api;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
public class InMemoryWemeetStore {

    private final Map<String, UserAccount> usersById = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByLoginId = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByFriendCode = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> friendIdsByUserId = new ConcurrentHashMap<>();
    private final Map<String, String> sessionsByToken = new ConcurrentHashMap<>();
    private final Map<String, List<SearchHistoryEntry>> historyByUserId = new ConcurrentHashMap<>();
    private final Map<String, MeetingRecord> meetingsById = new ConcurrentHashMap<>();

    public InMemoryWemeetStore() {
        seed();
    }

    public synchronized UserAccount createUser(String nickname, String loginId, String password, String baseAddress) {
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "loginId and password are required");
        }
        if (userIdByLoginId.containsKey(loginId)) {
            throw new ResponseStatusException(CONFLICT, "loginId already exists");
        }

        String userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        UserAccount user = new UserAccount(
                userId,
                nickname == null || nickname.isBlank() ? loginId : nickname,
                loginId,
                password,
                generateFriendCode(loginId),
                baseAddress == null || baseAddress.isBlank() ? "서울특별시 중구 명동길 74" : baseAddress,
                LocalDate.now()
        );

        saveUser(user);
        friendIdsByUserId.putIfAbsent(user.id(), new LinkedHashSet<>());
        historyByUserId.putIfAbsent(user.id(), new ArrayList<>());
        return user;
    }

    public UserAccount authenticate(String loginId, String password) {
        UserAccount user = findByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

        if (!user.password().equals(password)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }
        return user;
    }

    public String createSession(String userId) {
        String token = UUID.randomUUID().toString();
        sessionsByToken.put(token, userId);
        return token;
    }

    public UserAccount requireUserByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing access token");
        }

        String userId = sessionsByToken.get(token);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid access token");
        }

        return findById(userId).orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "User not found"));
    }

    public Optional<UserAccount> findById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public Optional<UserAccount> findByLoginId(String loginId) {
        if (loginId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userIdByLoginId.get(loginId)).flatMap(this::findById);
    }

    public List<UserAccount> listFriends(String userId) {
        return friendIdsByUserId.getOrDefault(userId, Set.of()).stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(UserAccount::nickname))
                .toList();
    }

    public UserAccount addFriendByCode(String userId, String friendCode) {
        if (friendCode == null || friendCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "friendCode is required");
        }

        String friendId = userIdByFriendCode.get(friendCode);
        if (friendId == null) {
            throw new ResponseStatusException(NOT_FOUND, "Friend code not found");
        }
        if (userId.equals(friendId)) {
            throw new ResponseStatusException(BAD_REQUEST, "You cannot add yourself");
        }

        friendIdsByUserId.computeIfAbsent(userId, ignored -> new LinkedHashSet<>()).add(friendId);
        friendIdsByUserId.computeIfAbsent(friendId, ignored -> new LinkedHashSet<>()).add(userId);
        return usersById.get(friendId);
    }

    public MeetingRecord createMeeting(
            String hostUserId,
            String title,
            String description,
            LocalDate meetingDate,
            String category,
            List<String> participantIds
    ) {
        if (title == null || title.isBlank() || meetingDate == null || category == null || category.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "title, meetingDate, and category are required");
        }

        LinkedHashSet<String> uniqueParticipantIds = new LinkedHashSet<>();
        uniqueParticipantIds.add(hostUserId);
        if (participantIds != null) {
            uniqueParticipantIds.addAll(participantIds);
        }

        List<String> resolvedParticipants = uniqueParticipantIds.stream()
                .peek(id -> {
                    if (!usersById.containsKey(id)) {
                        throw new ResponseStatusException(NOT_FOUND, "Participant not found: " + id);
                    }
                })
                .toList();

        MeetingRecord meeting = new MeetingRecord(
                "meeting-" + UUID.randomUUID().toString().substring(0, 8),
                title,
                description == null ? "" : description,
                meetingDate,
                category,
                hostUserId,
                resolvedParticipants,
                LocalDateTime.now()
        );
        meetingsById.put(meeting.id(), meeting);
        return meeting;
    }

    public List<MeetingRecord> listMeetingsForUser(String userId) {
        return meetingsById.values().stream()
                .filter(meeting -> meeting.participantIds().contains(userId))
                .sorted(java.util.Comparator.comparing(MeetingRecord::meetingDate))
                .toList();
    }

    public void appendHistory(String userId, String query, String category) {
        historyByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>())
                .add(0, new SearchHistoryEntry(query, category, LocalDateTime.now()));
    }

    public List<SearchHistoryEntry> listHistory(String userId) {
        return List.copyOf(historyByUserId.getOrDefault(userId, List.of()));
    }

    public void syncUserSnapshot(String userId, String nickname, String loginId, String friendCode, String baseAddress) {
        UserAccount snapshot = new UserAccount(
                userId,
                nickname,
                loginId,
                "",
                friendCode,
                baseAddress,
                LocalDate.now()
        );
        saveUser(snapshot);
        friendIdsByUserId.putIfAbsent(userId, new LinkedHashSet<>());
        historyByUserId.putIfAbsent(userId, new ArrayList<>());
    }

    private void seed() {
        UserAccount owner = createSeedUser("user-123", "김철수", "user123", "pass1234", "FRIEND123", "서울특별시 중구 명동길 74");
        UserAccount friendA = createSeedUser("friend-lee", "이영희", "user456", "pass1234", "FRIEND456", "서울특별시 성동구 성수동1가");
        UserAccount friendB = createSeedUser("friend-park", "박민수", "user789", "pass1234", "FRIEND789", "서울특별시 마포구 공덕동");

        addFriendLink(owner.id(), friendA.id());
        addFriendLink(owner.id(), friendB.id());

        appendHistory(owner.id(), "강남 맛집", "맛집");
        appendHistory(owner.id(), "홍대 카페", "카페");

        createMeeting(
                owner.id(),
                "주말 맛집 탐방",
                "주말에 새로운 맛집 찾아가요!",
                LocalDate.of(2026, 3, 15),
                "맛집",
                List.of(friendA.id(), friendB.id())
        );
    }

    private void addFriendLink(String userId, String friendId) {
        friendIdsByUserId.computeIfAbsent(userId, ignored -> new LinkedHashSet<>()).add(friendId);
        friendIdsByUserId.computeIfAbsent(friendId, ignored -> new LinkedHashSet<>()).add(userId);
    }

    private UserAccount createSeedUser(
            String userId,
            String nickname,
            String loginId,
            String password,
            String friendCode,
            String baseAddress
    ) {
        UserAccount user = new UserAccount(userId, nickname, loginId, password, friendCode, baseAddress, LocalDate.of(2026, 3, 1));
        saveUser(user);
        historyByUserId.putIfAbsent(user.id(), new ArrayList<>());
        friendIdsByUserId.putIfAbsent(user.id(), new LinkedHashSet<>());
        return user;
    }

    private void saveUser(UserAccount user) {
        usersById.put(user.id(), user);
        userIdByLoginId.put(user.loginId(), user.id());
        userIdByFriendCode.put(user.friendCode(), user.id());
    }

    private String generateFriendCode(String loginId) {
        String base = loginId.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return (base.isBlank() ? "FRIEND" : base) + suffix;
    }

    record UserAccount(
            String id,
            String nickname,
            String loginId,
            String password,
            String friendCode,
            String baseAddress,
            LocalDate joinedOn
    ) {
    }

    record MeetingRecord(
            String id,
            String title,
            String description,
            LocalDate meetingDate,
            String category,
            String hostUserId,
            List<String> participantIds,
            LocalDateTime createdAt
    ) {
    }

    record SearchHistoryEntry(
            String query,
            String category,
            LocalDateTime searchedAt
    ) {
    }
}
