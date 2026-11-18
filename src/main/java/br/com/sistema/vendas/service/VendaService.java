package br.com.sistema.vendas.service;

import br.com.sistema.vendas.interfaces.MetodoPagamento;
import br.com.sistema.vendas.model.CarrinhoDeCompras;
import br.com.sistema.vendas.model.Cliente;
import br.com.sistema.vendas.model.EnderecoEntrega;
import br.com.sistema.vendas.model.ItemPedido;
import br.com.sistema.vendas.model.Pedido;
import br.com.sistema.vendas.model.Recibo;
import br.com.sistema.vendas.payment.ComprovantePagamento;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Orquestra o fechamento de uma compra. Depende apenas de abstrações para pagamento
 * ({@link MetodoPagamento}) e desconto (polimorfismo de {@link Cliente}).
 *
 * <p>Fluxo: valida estoque de todos os itens → cria pedido → AGUARDANDO_PAGAMENTO → debita estoque
 * → processa pagamento → PAGO → recibo e gravação → CONCLUIDO. Se qualquer passo após o débito falhar,
 * o estoque é reposto e o pedido é CANCELADO antes de a exceção ser propagada.</p>
 *
 * <p>O método de fechamento é {@code synchronized} para que verificação e débito de estoque
 * sejam atômicos entre chamadas concorrentes nesta instância.</p>
 */
public class VendaService {

    private final Clock clock;

    public VendaService() {
        this(Clock.systemDefaultZone());
    }

    /** Permite injetar o relógio (útil para testes com data/hora fixa). */
    public VendaService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Recibo finalizarCompra(CarrinhoDeCompras carrinho, Cliente cliente,
                                               MetodoPagamento metodoPagamento) {
        return finalizarCompra(carrinho, cliente, metodoPagamento, recibo -> { });
    }

    public synchronized Recibo finalizarCompra(CarrinhoDeCompras carrinho, Cliente cliente,
            MetodoPagamento metodoPagamento, Consumer<Recibo> salvarRecibo) {
        return finalizarCompra(carrinho, cliente, null, metodoPagamento, salvarRecibo);
    }

    public synchronized Recibo finalizarCompra(CarrinhoDeCompras carrinho, Cliente cliente,
            EnderecoEntrega enderecoEntrega, MetodoPagamento metodoPagamento, Consumer<Recibo> salvarRecibo) {
        Objects.requireNonNull(salvarRecibo, "salvarRecibo");
        Objects.requireNonNull(carrinho, "carrinho");
        Objects.requireNonNull(cliente, "cliente");
        Objects.requireNonNull(metodoPagamento, "metodoPagamento");
        if (carrinho.estaVazio()) {
            throw new IllegalStateException("Não é possível finalizar compra com carrinho vazio.");
        }

        List<ItemPedido> itens = List.copyOf(carrinho.getItens());
        verificarEstoque(itens);

        Pedido pedido = new Pedido(cliente, itens, enderecoEntrega);
        pedido.aguardarPagamento();

        List<ItemPedido> debitados = new ArrayList<>();
        ComprovantePagamento comprovante = null;
        try {
            for (ItemPedido item : itens) {
                item.getProduto().debitarEstoque(item.getQuantidadeAdquirida());
                debitados.add(item);
            }
            comprovante = metodoPagamento.processarPagamento(pedido.getValorTotal());
            pedido.confirmarPagamento();
            Recibo recibo = new Recibo(pedido, comprovante, LocalDateTime.now(clock));
            salvarRecibo.accept(recibo);
            pedido.concluir();
            carrinho.limpar();
            return recibo;
        } catch (RuntimeException e) {
            if (comprovante != null) metodoPagamento.estornarPagamento(comprovante.valor());
            debitados.forEach(item -> item.getProduto().reporEstoque(item.getQuantidadeAdquirida()));
            if (!pedido.getEstado().isFinal()) {
                pedido.cancelar();
            }
            throw e;
        }
    }

    /** Valida todos os itens antes de debitar qualquer um (tudo ou nada). */
    private void verificarEstoque(List<ItemPedido> itens) {
        for (ItemPedido item : itens) {
            item.getProduto().verificarDisponibilidade(item.getQuantidadeAdquirida());
        }
    }
}
