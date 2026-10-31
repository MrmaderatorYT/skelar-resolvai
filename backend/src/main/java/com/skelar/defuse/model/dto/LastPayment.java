package com.skelar.defuse.model.dto;

public record LastPayment(String id, double amount, String status, long daysAgo) {
}
