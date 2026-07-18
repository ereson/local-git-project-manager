package com.localprojectmanager.application.git;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ProjectGitLock {

    private final ConcurrentHashMap<UUID, Lock> locks = new ConcurrentHashMap<>();

    Lock get(UUID projectId) {
        return locks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
    }
}
