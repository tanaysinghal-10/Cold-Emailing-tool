package com.coldbot.job.repository;

import com.coldbot.common.enums.ApplicationStatus;
import com.coldbot.job.entity.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<JobApplication> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<JobApplication> findByUserTelegramChatIdOrderByCreatedAtDesc(Long telegramChatId);

    List<JobApplication> findByStatus(ApplicationStatus status);

    long countByUserIdAndStatus(Long userId, ApplicationStatus status);
}
