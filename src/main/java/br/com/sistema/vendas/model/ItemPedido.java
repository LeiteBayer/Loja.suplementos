package br.com.sistema.vendas.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Linha de um carrinho ou pedido. Imutável: o preço unitário é congelado no momento da criação,
 * então alterações posteriores no preço do produto não afetam o item.
 */
public final class ItemPedido {

    private final Produto produto;
    private final int quantidadeAdquirida;
    private final BigDecimal precoUnitario;

    public ItemPedido(Produto produto, int quantidadeAdquirida) {
        this(produto, quantidadeAdquirida, Objects.requireNonNull(produto, "produto").getPreco());
    }

    private ItemPedido(Produto produto, int quantidadeAdquirida, BigDecimal precoUnitario) {
        this.produto = Objects.requireNonNull(produto, "produto");
        if (quantidadeAdquirida <= 0) {
            throw new IllegalArgumentException("Quantidade adquirida deve ser maior que zero.");
        }
        this.quantidadeAdquirida = quantidadeAdquirida;
        this.precoUnitario = Objects.requireNonNull(precoUnitario, "precoUnitario");
    }

    public BigDecimal calcularSubtotal() {
        return Moeda.arredondar(precoUnitario.multiply(BigDecimal.valueOf(quantidadeAdquirida)));
    }

    /** Novo item com outra quantidade, preservando o preço congelado. */
    public ItemPedido comQuantidade(int novaQuantidade) {
        return new ItemPedido(produto, novaQuantidade, precoUnitario);
    }

    public boolean referencia(Produto outro) {
        return produto.equals(outro);
    }

    public Produto getProduto() {
        return produto;
    }

    public int getQuantidadeAdquirida() {
        return quantidadeAdquirida;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    @Override
    public String toString() {
        return String.format("%dx %s @ %s = %s", quantidadeAdquirida, produto.getNome(),
                Moeda.formatar(precoUnitario), Moeda.formatar(calcularSubtotal()));
    }
}
