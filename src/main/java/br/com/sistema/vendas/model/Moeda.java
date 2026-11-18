package br.com.sistema.vendas.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utilitário para arredondamento e formatação de valores monetários em reais.
 */
public final class Moeda {

    public static final int ESCALA = 2;
    public static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_EVEN;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Moeda() {
    }

    public static BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(ESCALA, ARREDONDAMENTO);
    }

    public static String formatar(BigDecimal valor) {
        // NumberFormat não é thread-safe; cria-se uma instância por chamada.
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor);
    }
}
