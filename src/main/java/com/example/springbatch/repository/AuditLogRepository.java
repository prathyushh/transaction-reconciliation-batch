package com.example.springbatch.repository;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.springbatch.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
	Optional<AuditLog> findByJobExecutionIdAndEventType(Long jobExecutionId, String eventType);

	Optional<AuditLog> findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(Collection<Long> jobExecutionIds,
			String eventType);
}