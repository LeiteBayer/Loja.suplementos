package br.com.sistema.vendas.model;

import java.math.BigDecimal;

/**
 * Cliente comum: 5% de desconto apenas em compras a partir de R$ 500,00.
 */
public class ClientePadrao extends Cliente {

    private static final BigDecimal VALOR_MINIMO = new BigDecimal("500.00");
    private static final BigDecimal PERCENTUAL = new BigDecimal("0.05");

    public ClientePadrao(String nome, String identificacao, String email) {
        super(nome, identificacao, email);
    }

    @Override
    protected BigDecimal percentualDesconto(BigDecimal subtotal) {
        return subtotal.compareTo(VALOR_MINIMO) >= 0 ? PERCENTUAL : BigDecimal.ZERO;
    }

    @Override
    public String descreverDesconto() {
        return "Cliente padrão: 5% em compras a partir de " + Moeda.formatar(VALOR_MINIMO);
    }

    @Override
    public String getTipo() {
        return "Padrão";
    }
}
