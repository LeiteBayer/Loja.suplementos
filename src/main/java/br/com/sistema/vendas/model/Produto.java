package br.com.sistema.vendas.model;

import br.com.sistema.vendas.exception.EstoqueInsuficienteException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Produto vendável. A identidade é o código; preço e estoque só mudam por métodos de domínio validados.
 */
public class Produto {

    private final String codigo;
    private final String nome;
    private BigDecimal preco;
    private int quantidadeEstoque;

    public Produto(String codigo, String nome, BigDecimal preco, int quantidadeEstoque) {
        this.codigo = exigirTexto(codigo, "código");
        this.nome = exigirTexto(nome, "nome");
        this.preco = validarPreco(preco);
        if (quantidadeEstoque < 0) {
            throw new IllegalArgumentException("Estoque inicial não pode ser negativo.");
        }
        this.quantidadeEstoque = quantidadeEstoque;
    }

    public boolean temEstoque(int quantidade) {
        validarQuantidadePositiva(quantidade);
        return quantidadeEstoque >= quantidade;
    }

    /**
     * Garante que há estoque suficiente, sem alterá-lo.
     *
     * @throws EstoqueInsuficienteException se não houver
     */
    public void verificarDisponibilidade(int quantidade) {
        if (!temEstoque(quantidade)) {
            throw new EstoqueInsuficienteException(codigo, nome, quantidade, quantidadeEstoque);
        }
    }

    public void debitarEstoque(int quantidade) {
        verificarDisponibilidade(quantidade);
        quantidadeEstoque -= quantidade;
    }

    public void reporEstoque(int quantidade) {
        validarQuantidadePositiva(quantidade);
        quantidadeEstoque = Math.addExact(quantidadeEstoque, quantidade);
    }

    public void alterarPreco(BigDecimal novoPreco) {
        this.preco = validarPreco(novoPreco);
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public int getQuantidadeEstoque() {
        return quantidadeEstoque;
    }

    private static BigDecimal validarPreco(BigDecimal preco) {
        Objects.requireNonNull(preco, "preço");
        if (preco.signum() <= 0) {
            throw new IllegalArgumentException("Preço deve ser maior que zero.");
        }
        return Moeda.arredondar(preco);
    }

    private static void validarQuantidadePositiva(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
    }

    static String exigirTexto(String valor, String campo) {
        Objects.requireNonNull(valor, campo);
        if (valor.isBlank()) {
            throw new IllegalArgumentException("Campo '" + campo + "' não pode ser vazio.");
        }
        return valor.strip();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Produto outro)) return false;
        return codigo.equals(outro.codigo);
    }

    @Override
    public int hashCode() {
        return codigo.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s, estoque: %d)", codigo, nome, Moeda.formatar(preco), quantidadeEstoque);
    }
}
