package com.example.springbatch.dto;

import lombok.Data;

@Data
public class TransactionRequestDto {
	private String transactionId;
	private String accountNumber;
	private String transactionDate;
	private String amount;
	private String currency;
	private String transactionType;
	private String description;
	private Long lineNumber;
}