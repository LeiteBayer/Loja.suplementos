package br.com.sistema.vendas.exception;

/**
 * Lançada quando a quantidade solicitada de um produto excede o estoque disponível.
 */
public class EstoqueInsuficienteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String codigoProduto;
    private final int quantidadeSolicitada;
    private final int quantidadeDisponivel;

    public EstoqueInsuficienteException(String codigoProduto, String nomeProduto,
                                        int quantidadeSolicitada, int quantidadeDisponivel) {
        super(String.format("Estoque insuficiente para '%s' (%s): solicitado %d, disponível %d.",
                nomeProduto, codigoProduto, quantidadeSolicitada, quantidadeDisponivel));
        this.codigoProduto = codigoProduto;
        this.quantidadeSolicitada = quantidadeSolicitada;
        this.quantidadeDisponivel = quantidadeDisponivel;
    }

    public String getCodigoProduto() {
        return codigoProduto;
    }

    public int getQuantidadeSolicitada() {
        return quantidadeSolicitada;
    }

    public int getQuantidadeDisponivel() {
        return quantidadeDisponivel;
    }
}
