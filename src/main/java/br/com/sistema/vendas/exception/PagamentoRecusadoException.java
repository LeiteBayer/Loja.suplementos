package br.com.sistema.vendas.exception;

/**
 * Lançada por um {@code MetodoPagamento} quando a transação não é autorizada.
 */
public class PagamentoRecusadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PagamentoRecusadoException(String motivo) {
        super("Pagamento recusado: " + motivo);
    }
}
