package br.com.sistema.vendas.payment;

import br.com.sistema.vendas.interfaces.MetodoPagamento;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Confirmação simulada de um pagamento Pix com valor positivo.
 */
public class PagamentoPix implements MetodoPagamento {

    private final String chavePix;
    private final String referencia;

    public PagamentoPix(String chavePix) {
        this(chavePix, null);
    }

    public PagamentoPix(String chavePix, String referencia) {
        Objects.requireNonNull(chavePix, "chavePix");
        if (chavePix.isBlank()) {
            throw new IllegalArgumentException("Chave Pix não pode ser vazia.");
        }
        this.chavePix = chavePix.strip();
        this.referencia = referencia;
    }

    @Override
    public ComprovantePagamento processarPagamento(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor do pagamento deve ser positivo.");
        }
        String txid = "PIX-" + (referencia == null
                ? UUID.randomUUID().toString().substring(0, 8).toUpperCase() : referencia);
        return new ComprovantePagamento(txid, descricao(), valor);
    }

    @Override
    public String descricao() {
        return "Pix (chave " + chavePix + ")";
    }
}
