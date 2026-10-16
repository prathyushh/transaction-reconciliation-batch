package com.example.springbatch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.springbatch.entity.TransactionRecord;

public interface TransactionRecordRepository
        extends JpaRepository<TransactionRecord, Long> {

    Optional<TransactionRecord> findByJobInstanceIdAndTransactionId(
            Long jobInstanceId,
            String transactionId);
}