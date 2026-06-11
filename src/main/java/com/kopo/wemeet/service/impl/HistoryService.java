package com.kopo.wemeet.service.impl;

import com.kopo.wemeet.dto.HistoryDTO;
import com.kopo.wemeet.repository.AppUserRepository;
import com.kopo.wemeet.repository.SearchHistoryRepository;
import com.kopo.wemeet.repository.entity.AppUser;
import com.kopo.wemeet.repository.entity.SearchHistory;
import com.kopo.wemeet.service.IHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class HistoryService implements IHistoryService {
    // 검색 기록의 저장, 조회, 삭제를 담당한다.

    private final AppUserRepository userRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    @Transactional
    @Override
    public void appendHistory(String userId, String query, String category) {
        if (query == null || query.isBlank() || category == null || category.isBlank()) {
            return;
        }
        searchHistoryRepository.save(new SearchHistory(requireUser(userId), query.trim(), category.trim()));
    }

    @Transactional(readOnly = true)
    @Override
    public List<HistoryDTO.SearchHistoryEntry> listHistory(String userId) {
        return searchHistoryRepository.findAllByUserIdOrderBySearchedAtDesc(userId).stream()
                .map(entry -> new HistoryDTO.SearchHistoryEntry(entry.getId(), entry.getQuery(), entry.getCategory(), entry.getSearchedAt()))
                .toList();
    }

    @Transactional
    @Override
    public void clearHistory(String userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }

    @Transactional
    @Override
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
}

