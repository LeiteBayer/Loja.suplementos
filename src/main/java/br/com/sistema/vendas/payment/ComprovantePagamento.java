package br.com.sistema.vendas.payment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado imutável de um pagamento autorizado.
 */
public record ComprovantePagamento(String codigoAutorizacao, String descricaoMetodo, BigDecimal valor) {

    public ComprovantePagamento {
        Objects.requireNonNull(codigoAutorizacao, "codigoAutorizacao");
        Objects.requireNonNull(descricaoMetodo, "descricaoMetodo");
        Objects.requireNonNull(valor, "valor");
        if (codigoAutorizacao.isBlank()) {
            throw new IllegalArgumentException("Código de autorização não pode ser vazio.");
        }
    }
}
