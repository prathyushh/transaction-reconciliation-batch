package com.example.springbatch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.springbatch.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
	List<AuditLog> findByJobExecutionId(Long jobExecutionId);

	List<AuditLog> findByJobExecutionIdAndEventType(Long jobExecutionId, String eventType);

	List<AuditLog> findByJobExecutionIdAndStatus(Long jobExecutionId, String status);
}
