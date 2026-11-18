package br.com.sistema.vendas.exception;

import br.com.sistema.vendas.enums.EstadoPedido;

/**
 * Lançada quando se tenta mover um pedido para um estado não permitido a partir do estado atual.
 */
public class TransicaoEstadoInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final EstadoPedido origem;
    private final EstadoPedido destino;

    public TransicaoEstadoInvalidaException(EstadoPedido origem, EstadoPedido destino) {
        super(String.format("Transição de estado inválida: %s -> %s.", origem, destino));
        this.origem = origem;
        this.destino = destino;
    }

    public EstadoPedido getOrigem() {
        return origem;
    }

    public EstadoPedido getDestino() {
        return destino;
    }
}
