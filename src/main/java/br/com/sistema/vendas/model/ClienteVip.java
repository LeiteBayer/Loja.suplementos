package br.com.sistema.vendas.model;

import java.math.BigDecimal;

/**
 * Cliente VIP com desconto de 10% até R$ 700,00 e de 15% acima desse valor.
 */
public class ClienteVip extends Cliente {

    private static final BigDecimal LIMITE_FAIXA_BASE = new BigDecimal("700.00");
    private static final BigDecimal PERCENTUAL_BASE = new BigDecimal("0.10");
    private static final BigDecimal PERCENTUAL_SUPERIOR = new BigDecimal("0.15");

    public ClienteVip(String nome, String identificacao, String email) {
        super(nome, identificacao, email);
    }

    @Override
    protected BigDecimal percentualDesconto(BigDecimal subtotal) {
        return subtotal.compareTo(LIMITE_FAIXA_BASE) > 0 ? PERCENTUAL_SUPERIOR : PERCENTUAL_BASE;
    }

    @Override
    public String descreverDesconto() {
        return "Cliente VIP com 10% até " + Moeda.formatar(LIMITE_FAIXA_BASE) + " e 15% acima desse valor";
    }

    @Override
    public String getTipo() {
        return "VIP";
    }
}
