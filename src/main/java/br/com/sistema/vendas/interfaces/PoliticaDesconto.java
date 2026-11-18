package br.com.sistema.vendas.interfaces;

import java.math.BigDecimal;

/**
 * Regra de desconto aplicada sobre o subtotal de um pedido.
 */
public interface PoliticaDesconto {

    /**
     * @param subtotal valor bruto dos itens (não negativo)
     * @return valor do desconto, entre zero e o próprio subtotal
     */
    BigDecimal calcularDesconto(BigDecimal subtotal);

    /** Descrição legível da regra, usada no recibo. */
    String descreverDesconto();
}
