package com.example.springbatch.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_log", uniqueConstraints = @UniqueConstraint(name = "uk_audit_log_job_instance", columnNames = "job_instance_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "job_instance_id", nullable = false)
	private Long jobInstanceId;
	private Long jobExecutionId;
	private String eventType;
	private String status;
	private LocalDateTime eventTimestamp;
	private String details;
	private Long totalRecords;
	private Long loadedRecords;
	private Long matchedRecords;
	private Long mismatchedRecords;
	private Long duplicateRecords;
	private Long invalidRecords;
	private Long missingInDatabaseRecords;
}
