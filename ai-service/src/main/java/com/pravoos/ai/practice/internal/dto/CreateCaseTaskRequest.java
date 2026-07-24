package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCaseTaskRequest(@NotBlank @Size(max = 1000) String text, LocalDate dueDate) {}
