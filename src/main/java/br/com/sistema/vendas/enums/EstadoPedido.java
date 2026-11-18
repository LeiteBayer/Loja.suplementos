package br.com.sistema.vendas.enums;

import br.com.sistema.vendas.exception.TransicaoEstadoInvalidaException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de um pedido. Cada estado conhece os estados para os quais pode avançar.
 *
 * <pre>
 * ABERTO ──► AGUARDANDO_PAGAMENTO ──► PAGO ──► CONCLUIDO
 *   │                │                 │
 *   └────────────────┴─────────────────┴──► CANCELADO
 * </pre>
 */
public enum EstadoPedido {

    ABERTO("Aberto"),
    AGUARDANDO_PAGAMENTO("Aguardando pagamento"),
    PAGO("Pago"),
    CANCELADO("Cancelado"),
    CONCLUIDO("Concluído");

    private final String descricao;

    EstadoPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Estados alcançáveis a partir deste. */
    public Set<EstadoPedido> proximosPermitidos() {
        Set<EstadoPedido> permitidos = switch (this) {
            case ABERTO -> EnumSet.of(AGUARDANDO_PAGAMENTO, CANCELADO);
            case AGUARDANDO_PAGAMENTO -> EnumSet.of(PAGO, CANCELADO);
            case PAGO -> EnumSet.of(CONCLUIDO, CANCELADO);
            case CANCELADO, CONCLUIDO -> EnumSet.noneOf(EstadoPedido.class);
        };
        return Collections.unmodifiableSet(permitidos);
    }

    public boolean podeTransicionarPara(EstadoPedido destino) {
        return destino != null && proximosPermitidos().contains(destino);
    }

    /**
     * Retorna o estado destino se a transição for válida; caso contrário lança exceção.
     */
    public EstadoPedido transicionarPara(EstadoPedido destino) {
        if (!podeTransicionarPara(destino)) {
            throw new TransicaoEstadoInvalidaException(this, destino);
        }
        return destino;
    }

    public boolean isFinal() {
        return proximosPermitidos().isEmpty();
    }
}
