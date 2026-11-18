package br.com.sistema.vendas.model;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

public record CartaoCadastrado(String id, String titular, String finalNumero, YearMonth validade) {

    public CartaoCadastrado {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(validade, "validade");
        if (titular == null || titular.isBlank() || titular.strip().length() > 100
                || titular.strip().chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Informe o nome impresso no cartão.");
        }
        if (finalNumero == null || !finalNumero.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Final do cartão inválido.");
        }
        titular = titular.strip();
    }

    public static CartaoCadastrado cadastrar(String titular, String numero, String vencimento) {
        YearMonth validade;
        try {
            validade = YearMonth.parse(vencimento);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Informe o mês e o ano de validade do cartão.");
        }
        CartaoCadastrado cartao = new CartaoCadastrado(UUID.randomUUID().toString(),
                titular, finalDoNumero(numero), validade);
        cartao.validarValidade();
        return cartao;
    }

    public static String finalDoNumero(String numero) {
        String digitos = Objects.requireNonNull(numero, "número do cartão").replaceAll("[\\s-]", "");
        if (!digitos.matches("[0-9]{13,19}")) {
            throw new IllegalArgumentException("Informe um número de cartão com 13 a 19 dígitos.");
        }
        return digitos.substring(digitos.length() - 4);
    }

    public void validarValidade() {
        if (validade.isBefore(YearMonth.now())) {
            throw new IllegalArgumentException("A validade do cartão está vencida.");
        }
    }
}
