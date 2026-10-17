package com.example.springbatch.enums;

public enum ReconciliationStatus {
    VALID,
    INVALID_RECORD,
    DUPLICATE_IN_CSV1,
    MATCH,
    MISMATCH,
    MISSING_IN_DATABASE
}