package br.com.sistema.vendas.payment;

import br.com.sistema.vendas.interfaces.MetodoPagamento;
import br.com.sistema.vendas.exception.PagamentoRecusadoException;
import br.com.sistema.vendas.model.CartaoCadastrado;
import br.com.sistema.vendas.model.Moeda;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

// O débito consulta o saldo da simulação e é sempre à vista.
public class PagamentoDebito implements MetodoPagamento {
    private final String descricao;
    private BigDecimal saldoDisponivel;

    public PagamentoDebito(BigDecimal saldoDisponivel) {
        this(saldoDisponivel, "Cartão de débito (à vista)");
    }

    public PagamentoDebito(CartaoCadastrado cartao, BigDecimal saldoDisponivel) {
        this(saldoDisponivel, "Cartão de débito final " + cartao.finalNumero() + " (à vista)");
    }

    private PagamentoDebito(BigDecimal saldoDisponivel, String descricao) {
        Objects.requireNonNull(saldoDisponivel, "saldo");
        if (saldoDisponivel.signum() < 0) {
            throw new IllegalArgumentException("Saldo não pode ser negativo.");
        }
        this.saldoDisponivel = Moeda.arredondar(saldoDisponivel);
        this.descricao = descricao;
    }

    @Override
    public ComprovantePagamento processarPagamento(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor deve ser positivo.");
        }
        if (saldoDisponivel.compareTo(valor) < 0) {
            throw new PagamentoRecusadoException("saldo insuficiente para débito.");
        }
        saldoDisponivel = saldoDisponivel.subtract(valor);
        return new ComprovantePagamento("DEB-" + UUID.randomUUID(), descricao(), valor);
    }

    @Override
    public void estornarPagamento(BigDecimal valor) {
        saldoDisponivel = saldoDisponivel.add(valor);
    }

    @Override
    public String descricao() { return descricao; }

    public BigDecimal getSaldoDisponivel() { return saldoDisponivel; }
}
