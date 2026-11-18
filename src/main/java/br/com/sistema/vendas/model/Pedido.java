package br.com.sistema.vendas.model;

import br.com.sistema.vendas.enums.EstadoPedido;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pedido consolidado a partir dos itens do carrinho. Os valores são calculados uma única vez
 * na criação; a partir daí apenas o estado evolui, sempre por transições validadas no enum.
 */
public class Pedido {

    private final String identificacao;
    private final Cliente cliente;
    private final EnderecoEntrega enderecoEntrega;
    private final List<ItemPedido> itens;
    private final BigDecimal subtotal;
    private final BigDecimal desconto;
    private final BigDecimal valorTotal;
    private EstadoPedido estado;

    public Pedido(Cliente cliente, List<ItemPedido> itens) {
        this(cliente, itens, null);
    }

    public Pedido(Cliente cliente, List<ItemPedido> itens, EnderecoEntrega enderecoEntrega) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.enderecoEntrega = enderecoEntrega;
        Objects.requireNonNull(itens, "itens");
        if (itens.isEmpty()) {
            throw new IllegalArgumentException("Pedido deve conter ao menos um item.");
        }
        List<ItemPedido> copia = new ArrayList<>(itens);
        copia.forEach(item -> Objects.requireNonNull(item, "item do pedido"));

        this.identificacao = UUID.randomUUID().toString();
        this.itens = Collections.unmodifiableList(copia);
        this.subtotal = copia.stream()
                .map(ItemPedido::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(Moeda.ESCALA, Moeda.ARREDONDAMENTO);
        this.desconto = cliente.calcularDesconto(subtotal).min(subtotal);
        this.valorTotal = subtotal.subtract(desconto);
        this.estado = EstadoPedido.ABERTO;
    }

    public void aguardarPagamento() {
        transicionarPara(EstadoPedido.AGUARDANDO_PAGAMENTO);
    }

    public void confirmarPagamento() {
        transicionarPara(EstadoPedido.PAGO);
    }

    public void concluir() {
        transicionarPara(EstadoPedido.CONCLUIDO);
    }

    public void cancelar() {
        transicionarPara(EstadoPedido.CANCELADO);
    }

    private void transicionarPara(EstadoPedido destino) {
        this.estado = estado.transicionarPara(destino);
    }

    public String getIdentificacao() {
        return identificacao;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public EnderecoEntrega getEnderecoEntrega() {
        return enderecoEntrega;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    @Override
    public String toString() {
        return String.format("Pedido %s [%s] %s - total %s", identificacao, estado.getDescricao(),
                cliente.getNome(), Moeda.formatar(valorTotal));
    }
}
