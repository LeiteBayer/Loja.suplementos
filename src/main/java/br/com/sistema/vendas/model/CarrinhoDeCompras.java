package br.com.sistema.vendas.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Carrinho de compras. Mantém no máximo um {@link ItemPedido} por produto, agrupando quantidades.
 * A disponibilidade em estoque é checada ao adicionar (feedback antecipado) e novamente no fechamento.
 */
public class CarrinhoDeCompras {

    private final List<ItemPedido> itens = new ArrayList<>();

    public void adicionarItem(Produto produto, int quantidade) {
        Objects.requireNonNull(produto, "produto");
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        int indice = indiceDe(produto);
        if (indice >= 0) {
            ItemPedido existente = itens.get(indice);
            int novaQuantidade = Math.addExact(existente.getQuantidadeAdquirida(), quantidade);
            produto.verificarDisponibilidade(novaQuantidade);
            itens.set(indice, existente.comQuantidade(novaQuantidade));
        } else {
            produto.verificarDisponibilidade(quantidade);
            itens.add(new ItemPedido(produto, quantidade));
        }
    }

    public void removerItem(Produto produto) {
        itens.remove(indiceObrigatorio(produto));
    }

    /** Define a nova quantidade do item; zero remove o item do carrinho. */
    public void atualizarQuantidade(Produto produto, int novaQuantidade) {
        if (novaQuantidade < 0) {
            throw new IllegalArgumentException("Quantidade não pode ser negativa.");
        }
        int indice = indiceObrigatorio(produto);
        if (novaQuantidade == 0) {
            itens.remove(indice);
            return;
        }
        produto.verificarDisponibilidade(novaQuantidade);
        itens.set(indice, itens.get(indice).comQuantidade(novaQuantidade));
    }

    public BigDecimal calcularTotalParcial() {
        return itens.stream()
                .map(ItemPedido::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(Moeda.ESCALA, Moeda.ARREDONDAMENTO);
    }

    public void limpar() {
        itens.clear();
    }

    public boolean estaVazio() {
        return itens.isEmpty();
    }

    /** Visão somente leitura; os itens em si são imutáveis. */
    public List<ItemPedido> getItens() {
        return Collections.unmodifiableList(itens);
    }

    private int indiceDe(Produto produto) {
        Objects.requireNonNull(produto, "produto");
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i).referencia(produto)) {
                return i;
            }
        }
        return -1;
    }

    private int indiceObrigatorio(Produto produto) {
        int indice = indiceDe(produto);
        if (indice < 0) {
            throw new NoSuchElementException("Produto não está no carrinho: " + produto.getCodigo());
        }
        return indice;
    }

    @Override
    public String toString() {
        return "Carrinho" + itens + " total parcial: " + Moeda.formatar(calcularTotalParcial());
    }
}
