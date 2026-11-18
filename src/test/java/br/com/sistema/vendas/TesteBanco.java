package br.com.sistema.vendas;

import br.com.sistema.vendas.model.*;
import br.com.sistema.vendas.payment.*;
import br.com.sistema.vendas.service.VendaService;
import br.com.sistema.vendas.storage.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class TesteBanco {
    private static int verificacoes;
    private static void conferir(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
        verificacoes++;
    }
    private static long consultar(Connection conexao, String sql) throws SQLException {
        try (Statement comando = conexao.createStatement(); ResultSet resultado = comando.executeQuery(sql)) {
            if (!resultado.next()) throw new SQLException("Consulta sem resultado");
            return resultado.getLong(1);
        }
    }
    public static void main(String[] args) throws Exception {
        Path pasta = Files.createTempDirectory("loja-jdbc-");
        Path arquivo = pasta.resolve("teste.db");
        try {
            RepositorioBanco repositorio = new RepositorioBanco(arquivo);
            List<Produto> produtos = new ArrayList<>();
            produtos.add(new Produto("P1", "Creatina", new BigDecimal("89.90"), 10));
            conferir(repositorio.carregar(produtos).isEmpty(), "Banco novo sem recibos");
            ConexaoBanco banco = new ConexaoBanco(arquivo);
            Cliente cliente = new ClienteVip("Cliente D'Ávila", "DEMO-1", "teste@exemplo.com");
            EnderecoEntrega endereco = new EnderecoEntrega("60000000", "Rua das Flores", "25",
                    "", "Centro", "Fortaleza", "CE");
            VendaService vendas = new VendaService();
            // Falha no último INSERT: o banco deve desfazer cliente, estoque, venda e itens.
            CarrinhoDeCompras carrinho = new CarrinhoDeCompras();
            carrinho.adicionarItem(produtos.get(0), 2);
            PagamentoDebito debito = new PagamentoDebito(new BigDecimal("1000"));
            try {
                vendas.finalizarCompra(carrinho, cliente, endereco, debito, r -> repositorio.salvarVenda(r, "invalido", 1));
                throw new AssertionError("Deveria recusar tipo de pagamento no SQL");
            } catch (IllegalStateException esperado) {
                conferir(esperado.getCause() instanceof SQLException, "Erro veio da restrição SQL");
            }
            conferir(produtos.get(0).getQuantidadeEstoque() == 10, "Rollback do estoque em memória");
            conferir(!carrinho.estaVazio(), "Carrinho preservado após erro SQL");
            conferir(debito.getSaldoDisponivel().compareTo(new BigDecimal("1000")) == 0, "Saldo fictício estornado");
            try (Connection conexao = banco.abrir()) {
                conferir(consultar(conexao, "SELECT estoque FROM produtos WHERE codigo='P1'") == 10, "Rollback do estoque no banco");
                for (String tabela : List.of("clientes", "vendas", "itens_venda", "pagamentos", "enderecos_entrega")) {
                    conferir(consultar(conexao, "SELECT COUNT(*) FROM " + tabela) == 0, "Nenhum registro parcial: " + tabela);
                }
            }
            Recibo recibo = vendas.finalizarCompra(carrinho, cliente, endereco, debito, r -> repositorio.salvarVenda(r, "debito", 1));
            try (Connection conexao = banco.abrir()) {
                conferir(consultar(conexao, "SELECT COUNT(*) FROM clientes") == 1, "Cliente salvo");
                conferir(consultar(conexao, "SELECT COUNT(*) FROM vendas") == 1, "Venda salva");
                conferir(consultar(conexao, "SELECT COUNT(*) FROM enderecos_entrega e JOIN vendas v ON e.venda_id=v.id "
                        + "WHERE e.cep='60000-000' AND e.logradouro='Rua das Flores'") == 1, "Endereço armazenado na venda");
                conferir(consultar(conexao, "SELECT quantidade FROM itens_venda") == 2, "Quantidade dos itens salva");
                conferir(consultar(conexao, "SELECT preco_unitario_centavos FROM itens_venda") == 8990, "Preço exato em centavos");
                conferir(consultar(conexao, "SELECT total_centavos FROM vendas") == 16182, "Total VIP exato");
                conferir(consultar(conexao, "SELECT valor_centavos FROM pagamentos") == 16182, "Pagamento igual ao total");
                conferir(consultar(conexao, "SELECT COUNT(*) FROM vendas v JOIN clientes c ON c.id=v.cliente_id JOIN pagamentos p ON p.venda_id=v.id") == 1, "Relacionamentos funcionam");
                conferir(consultar(conexao, "SELECT estoque FROM produtos") == 8, "Estoque final salvo");
                try (Statement comando = conexao.createStatement()) {
                    try {
                        comando.executeUpdate("INSERT INTO itens_venda VALUES('inexistente','P1','Teste',1,100,100)");
                        throw new AssertionError("Deveria impedir item sem venda");
                    } catch (SQLException esperado) { verificacoes++; }
                }
                conferir(consultar(conexao, "SELECT COUNT(*) FROM pragma_foreign_key_check") == 0, "Sem violações de chave estrangeira");
            }
            CarrinhoDeCompras segundo = new CarrinhoDeCompras(); segundo.adicionarItem(produtos.get(0), 1);
            vendas.finalizarCompra(segundo, cliente, new PagamentoPix("teste@exemplo.com"), r -> repositorio.salvarVenda(r, "pix", 1));
            try (Connection conexao = banco.abrir()) {
                conferir(consultar(conexao, "SELECT COUNT(*) FROM clientes") == 1, "Identificação não duplica cliente");
                conferir(consultar(conexao, "SELECT COUNT(*) FROM vendas") == 2, "Segunda venda independente");
            }
            List<Produto> recarregados = new ArrayList<>(List.of(
                    new Produto("P1", "Creatina", new BigDecimal("99.90"), 10),
                    new Produto("P2", "Proteína Vegetal", new BigDecimal("109.90"), 18)));
            RepositorioBanco reaberto = new RepositorioBanco(arquivo);
            List<String> recibos = reaberto.carregar(recarregados);
            conferir(recarregados.get(0).getQuantidadeEstoque() == 7
                    && recarregados.get(0).getPreco().equals(new BigDecimal("89.90")), "Estoque e preço anteriores preservados");
            conferir(recarregados.size() == 2 && recarregados.get(1).getCodigo().equals("P2")
                    && recarregados.get(1).getQuantidadeEstoque() == 18, "Novo produto incluído no banco existente");
            reaberto.carregar(recarregados);
            conferir(recarregados.size() == 2 && recarregados.get(0).getQuantidadeEstoque() == 7,
                    "Reabrir o catálogo conserva os mesmos itens e o estoque");
            conferir(recibos.size() == 2 && recibos.get(1).equals(recibo.formatar()), "Recibos recuperados");
            System.out.println("OK: " + verificacoes + " verificações JDBC/SQLite, incluindo transação, rollback, chaves e centavos.");
        } finally {
            try (var caminhos = Files.walk(pasta)) {
                for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) Files.delete(caminho);
            }
        }
    }
}
