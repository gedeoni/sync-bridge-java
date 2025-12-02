package com.syncbridge.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncbridge.entity.SyncHistory;
import com.syncbridge.entity.SyncStatus;
import com.syncbridge.repository.SyncHistoryRepository;

@Service
public class SyncHistoryService {
    private final SyncHistoryRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public SyncHistoryService(SyncHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SyncHistory createPending(Object payload) {
        SyncHistory sh = new SyncHistory();
        try {
            sh.setPayload(mapper.writeValueAsString(payload));
        } catch (Exception e) {
            sh.setPayload("{}");
        }
        sh.setStatus(SyncStatus.PENDING_RETRY);
        return repository.save(sh);
    }

    @Transactional
    public void markSuccess(SyncHistory sh) {
        sh.setStatus(SyncStatus.SUCCESSFUL);
        repository.save(sh);
    }

    @Transactional
    public void markFailure(SyncHistory sh, String reason) {
        sh.setStatus(SyncStatus.FAILED);
        sh.setFailureReason(reason);
        repository.save(sh);
    }

    public Map<String, Integer> aggregateStats() {
        List<Object[]> raw = repository.countByStatus();
        Map<String, Integer> stats = new HashMap<>();
        stats.put("successful", 0);
        stats.put("failed", 0);
        stats.put("invalid", 0);
        stats.put("pending_retry", 0);
        int total = 0;
        for (Object[] row : raw) {
            String status = String.valueOf(row[0]);
            int cnt = ((Number) row[1]).intValue();
            stats.put(status, cnt);
            total += cnt;
        }
        stats.put("total", total);
        return stats;
    }
}

