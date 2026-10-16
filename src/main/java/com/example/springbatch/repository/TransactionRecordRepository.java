package com.example.springbatch.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.springbatch.entity.TransactionRecord;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, TransactionRecord.TransactionRecordId> {
	Optional<TransactionRecord> findByRunIdAndTransactionId(Long runId, String transactionId);

	boolean existsByRunIdAndTransactionId(Long runId, String transactionId);

	List<TransactionRecord> findByRunIdAndTransactionIdIn(Long runId, Collection<String> transactionIds);

	@Query("""
			SELECT t
			FROM TransactionRecord t
			WHERE t.runId = :runId
			  AND t.transactionId = :transactionId
			""")
	Optional<TransactionRecord> findTransactionForReconciliation(@Param("runId") Long runId,
			@Param("transactionId") String transactionId);
}
