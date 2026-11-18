package br.com.sistema.vendas.payment;

import br.com.sistema.vendas.exception.PagamentoRecusadoException;
import br.com.sistema.vendas.interfaces.MetodoPagamento;
import br.com.sistema.vendas.model.CartaoCadastrado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Pagamento com cartão de crédito, com parcelamento e controle de limite.
 * Guarda apenas os 4 últimos dígitos do cartão — o número completo nunca é retido.
 */
public class PagamentoCartao implements MetodoPagamento {

    private static final int MAX_PARCELAS = 12;

    private final String titular;
    private final String finalCartao;
    private final int parcelas;
    private BigDecimal limiteDisponivel;

    public PagamentoCartao(String titular, String numeroCartao, int parcelas, BigDecimal limiteDisponivel) {
        this(titular, CartaoCadastrado.finalDoNumero(numeroCartao), limiteDisponivel, parcelas);
    }

    public PagamentoCartao(CartaoCadastrado cartao, int parcelas, BigDecimal limiteDisponivel) {
        this(cartao.titular(), cartao.finalNumero(), limiteDisponivel, parcelas);
    }

    private PagamentoCartao(String titular, String finalCartao, BigDecimal limiteDisponivel, int parcelas) {
        Objects.requireNonNull(titular, "titular");
        Objects.requireNonNull(limiteDisponivel, "limiteDisponivel");
        if (titular.isBlank()) {
            throw new IllegalArgumentException("Titular do cartão não pode ser vazio.");
        }
        if (parcelas < 1 || parcelas > MAX_PARCELAS) {
            throw new IllegalArgumentException("Parcelas devem estar entre 1 e " + MAX_PARCELAS + ".");
        }
        if (limiteDisponivel.signum() < 0) {
            throw new IllegalArgumentException("Limite disponível não pode ser negativo.");
        }
        this.titular = titular.strip();
        this.finalCartao = finalCartao;
        this.parcelas = parcelas;
        this.limiteDisponivel = limiteDisponivel;
    }

    @Override
    public ComprovantePagamento processarPagamento(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor");
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException("Valor do pagamento deve ser positivo.");
        }
        if (valor.compareTo(limiteDisponivel) > 0) {
            throw new PagamentoRecusadoException(
                    "limite insuficiente no cartão final " + finalCartao + ".");
        }
        limiteDisponivel = limiteDisponivel.subtract(valor);
        String autorizacao = "CARD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ComprovantePagamento(autorizacao, descricao(), valor);
    }

    @Override
    public void estornarPagamento(BigDecimal valor) {
        limiteDisponivel = limiteDisponivel.add(valor);
    }

    @Override
    public String descricao() {
        return String.format("Cartão de crédito final %s (%s) em %dx", finalCartao, titular, parcelas);
    }

    public BigDecimal valorParcela(BigDecimal total) {
        Objects.requireNonNull(total, "total");
        return total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal getLimiteDisponivel() {
        return limiteDisponivel;
    }
}
