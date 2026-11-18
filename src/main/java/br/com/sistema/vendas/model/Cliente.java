package br.com.sistema.vendas.model;

import br.com.sistema.vendas.interfaces.PoliticaDesconto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base abstrata de clientes. Cada especialização define sua própria política de desconto
 * (template method: a base garante os limites, a subclasse define o percentual).
 */
public abstract class Cliente implements PoliticaDesconto {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final String nome;
    private final String identificacao;
    private final String email;

    protected Cliente(String nome, String identificacao, String email) {
        this.nome = Produto.exigirTexto(nome, "nome");
        this.identificacao = Produto.exigirTexto(identificacao, "identificação");
        String emailNormalizado = Produto.exigirTexto(email, "e-mail");
        if (!EMAIL.matcher(emailNormalizado).matches()) {
            throw new IllegalArgumentException("E-mail inválido: " + emailNormalizado);
        }
        this.email = emailNormalizado;
    }

    /**
     * Percentual de desconto (0 a 1) que o tipo de cliente concede para o subtotal informado.
     */
    protected abstract BigDecimal percentualDesconto(BigDecimal subtotal);

    /** Rótulo do tipo de cliente, para exibição. */
    public abstract String getTipo();

    @Override
    public final BigDecimal calcularDesconto(BigDecimal subtotal) {
        Objects.requireNonNull(subtotal, "subtotal");
        if (subtotal.signum() < 0) {
            throw new IllegalArgumentException("Subtotal não pode ser negativo.");
        }
        BigDecimal percentual = percentualDesconto(subtotal);
        if (percentual.signum() < 0 || percentual.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Percentual de desconto fora do intervalo [0, 1]: " + percentual);
        }
        return Moeda.arredondar(subtotal.multiply(percentual));
    }

    public String getNome() {
        return nome;
    }

    public String getIdentificacao() {
        return identificacao;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cliente outro)) return false;
        return identificacao.equals(outro.identificacao);
    }

    @Override
    public int hashCode() {
        return identificacao.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s [%s] - %s <%s>", nome, getTipo(), identificacao, email);
    }
}
