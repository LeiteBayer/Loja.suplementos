package br.com.sistema.vendas.interfaces;

import br.com.sistema.vendas.exception.PagamentoRecusadoException;
import br.com.sistema.vendas.payment.ComprovantePagamento;

import java.math.BigDecimal;

/**
 * Contrato para meios de pagamento. O {@code VendaService} depende apenas desta abstração.
 */
public interface MetodoPagamento {

    /**
     * Processa o pagamento do valor informado.
     *
     * @return comprovante da transação autorizada
     * @throws PagamentoRecusadoException se a transação não for autorizada
     */
    ComprovantePagamento processarPagamento(BigDecimal valor);

    // Desfaz o efeito local da simulação caso a gravação da venda falhe.
    default void estornarPagamento(BigDecimal valor) { }

    /** Descrição do meio de pagamento, usada no recibo. */
    String descricao();
}
