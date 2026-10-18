package com.example.springbatch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.springbatch.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
	Optional<AuditLog> findByJobInstanceId(Long jobInstanceId);

	@Modifying
	@Query("""
			UPDATE AuditLog a
			SET a.totalRecords = a.totalRecords + :total,
			    a.loadedRecords = a.loadedRecords + :loaded,
			    a.matchedRecords = a.matchedRecords + :matched,
			    a.mismatchedRecords = a.mismatchedRecords + :mismatched,
			    a.duplicateRecords = a.duplicateRecords + :duplicate,
			    a.invalidRecords = a.invalidRecords + :invalid,
			    a.missingInDatabaseRecords =
			        a.missingInDatabaseRecords + :missing,
			    a.eventTimestamp = CURRENT_TIMESTAMP
			WHERE a.jobInstanceId = :jobInstanceId
			""")
	int incrementCounts(@Param("jobInstanceId") Long jobInstanceId, @Param("total") long total,
			@Param("loaded") long loaded, @Param("matched") long matched, @Param("mismatched") long mismatched,
			@Param("duplicate") long duplicate, @Param("invalid") long invalid, @Param("missing") long missing);
}
