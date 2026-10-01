package br.com.telemicro.api.budget;

import jakarta.validation.constraints.*;

public record CreateBudgetRequest(
        @NotBlank(message = "Informe seu nome.")
        @Size(max = 100, message = "Use até 100 caracteres.") String customerName,
        @NotBlank(message = "Informe um telefone com DDD.")
        @Size(max = 24, message = "Use até 24 caracteres.")
        @Pattern(regexp = "[+0-9\\s().-]+", message = "Informe um telefone válido com DDD.") String phone,
        @NotBlank(message = "Selecione um serviço.")
        @Size(max = 50, message = "Serviço inválido.") String serviceCode,
        @Size(max = 2000, message = "Use até 2.000 caracteres.") String message) {
}
