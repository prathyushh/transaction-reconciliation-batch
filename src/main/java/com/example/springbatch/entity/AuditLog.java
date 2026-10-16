package com.example.springbatch.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
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
