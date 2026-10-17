package com.example.springbatch.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "transaction_records", uniqueConstraints = {
		@UniqueConstraint(name = "uk_transaction_instance_transaction", columnNames = { "job_instance_id",
				"transaction_id" }) })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRecord {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "job_instance_id", nullable = false)
	private Long jobInstanceId;
	@Column(name = "transaction_id", nullable = false, length = 50)
	private String transactionId;
	@Column(name = "account_number", length = 50)
	private String accountNumber;
	@Column(name = "transaction_date")
	private LocalDate transactionDate;
	@Column(name = "amount", precision = 15, scale = 2)
	private BigDecimal amount;
	@Column(name = "currency", length = 3)
	private String currency;
	@Column(name = "transaction_type", length = 30)
	private String transactionType;
	@Column(name = "description")
	private String description;
	@Column(name = "created_at")
	private LocalDateTime createdAt;
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}