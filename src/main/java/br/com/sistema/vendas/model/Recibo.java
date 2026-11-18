package br.com.sistema.vendas.model;

import br.com.sistema.vendas.payment.ComprovantePagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Comprovante imutável de uma venda concluída. Copia os dados relevantes do pedido
 * (snapshot), de modo que o recibo não depende de alterações futuras em outros objetos.
 */
public final class Recibo {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int LARGURA = 60;

    private final String identificacaoPedido;
    private final String dadosCliente;
    private final Cliente cliente;
    private final EnderecoEntrega enderecoEntrega;
    private final List<ItemPedido> itens;
    private final List<String> resumoItens;
    private final BigDecimal subtotal;
    private final BigDecimal desconto;
    private final String regraDesconto;
    private final BigDecimal valorPago;
    private final String metodoPagamento;
    private final String codigoAutorizacao;
    private final LocalDateTime dataHora;

    public Recibo(Pedido pedido, ComprovantePagamento comprovante, LocalDateTime dataHora) {
        Objects.requireNonNull(pedido, "pedido");
        Objects.requireNonNull(comprovante, "comprovante");
        this.dataHora = Objects.requireNonNull(dataHora, "dataHora");
        if (comprovante.valor().compareTo(pedido.getValorTotal()) != 0) {
            throw new IllegalArgumentException("Valor pago difere do total do pedido.");
        }
        this.cliente = pedido.getCliente();
        this.enderecoEntrega = pedido.getEnderecoEntrega();
        this.itens = List.copyOf(pedido.getItens());
        this.identificacaoPedido = pedido.getIdentificacao();
        this.dadosCliente = String.format("%s (%s) - %s - %s",
                cliente.getNome(), cliente.getTipo(), cliente.getIdentificacao(), cliente.getEmail());
        this.resumoItens = pedido.getItens().stream().map(Recibo::linhaItem).toList();
        this.subtotal = pedido.getSubtotal();
        this.desconto = pedido.getDesconto();
        this.regraDesconto = cliente.descreverDesconto();
        this.valorPago = comprovante.valor();
        this.metodoPagamento = comprovante.descricaoMetodo();
        this.codigoAutorizacao = comprovante.codigoAutorizacao();
    }

    private static String linhaItem(ItemPedido item) {
        String descricao = String.format("%3dx %s", item.getQuantidadeAdquirida(), item.getProduto().getNome());
        String valores = String.format("%s  %s",
                Moeda.formatar(item.getPrecoUnitario()), Moeda.formatar(item.calcularSubtotal()));
        return alinhar(descricao, valores);
    }

    private static String alinhar(String esquerda, String direita) {
        int espacos = Math.max(1, LARGURA - esquerda.length() - direita.length());
        return esquerda + " ".repeat(espacos) + direita;
    }

    public String formatar() {
        String linha = "=".repeat(LARGURA);
        String separador = "-".repeat(LARGURA);
        StringBuilder sb = new StringBuilder();
        sb.append(linha).append('\n');
        sb.append(centralizar("RECIBO DE VENDA")).append('\n');
        sb.append(linha).append('\n');
        sb.append("Pedido:    ").append(identificacaoPedido).append('\n');
        sb.append("Data/hora: ").append(FORMATO_DATA.format(dataHora)).append('\n');
        sb.append("Cliente:   ").append(dadosCliente).append('\n');
        if (enderecoEntrega != null) {
            sb.append("Entrega:   ").append(enderecoEntrega.formatar()).append('\n');
        }
        sb.append(separador).append('\n');
        resumoItens.forEach(l -> sb.append(l).append('\n'));
        sb.append(separador).append('\n');
        sb.append(alinhar("Subtotal", Moeda.formatar(subtotal))).append('\n');
        sb.append(alinhar("Desconto", "- " + Moeda.formatar(desconto))).append('\n');
        sb.append("  (").append(regraDesconto).append(")\n");
        sb.append(alinhar("TOTAL PAGO", Moeda.formatar(valorPago))).append('\n');
        sb.append(separador).append('\n');
        sb.append("Pagamento:   ").append(metodoPagamento).append('\n');
        sb.append("Autorização: ").append(codigoAutorizacao).append('\n');
        sb.append(linha);
        return sb.toString();
    }

    private static String centralizar(String texto) {
        int margem = Math.max(0, (LARGURA - texto.length()) / 2);
        return " ".repeat(margem) + texto;
    }

    public String getIdentificacaoPedido() {
        return identificacaoPedido;
    }

    public Cliente getCliente() { return cliente; }
    public EnderecoEntrega getEnderecoEntrega() { return enderecoEntrega; }
    public List<ItemPedido> getItens() { return itens; }
    public BigDecimal getSubtotal() { return subtotal; }
    public String getCodigoAutorizacao() { return codigoAutorizacao; }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public String getMetodoPagamento() {
        return metodoPagamento;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    @Override
    public String toString() {
        return formatar();
    }
}
