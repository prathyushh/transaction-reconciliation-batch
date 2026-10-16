package com.example.springbatch.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaction_records")
@IdClass(TransactionRecord.TransactionRecordId.class)
public class TransactionRecord {
	@Id
	@Column(name = "run_id", nullable = false)
	private Long runId;
	@Id
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

	public static class TransactionRecordId implements Serializable {
		private static final long serialVersionUID = 1L;
		private Long runId;
		private String transactionId;

		public TransactionRecordId() {
		}

		public TransactionRecordId(Long runId, String transactionId) {
			this.runId = runId;
			this.transactionId = transactionId;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof TransactionRecordId other)) {
				return false;
			}
			return Objects.equals(runId, other.runId) && Objects.equals(transactionId, other.transactionId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(runId, transactionId);
		}
	}
}