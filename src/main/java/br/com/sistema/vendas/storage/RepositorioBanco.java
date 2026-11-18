package br.com.sistema.vendas.storage;

import br.com.sistema.vendas.model.*;
import br.com.sistema.vendas.payment.ComprovantePagamento;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;

public class RepositorioBanco {
    private final ConexaoBanco banco;

    public RepositorioBanco(Path arquivo) throws IOException {
        banco = new ConexaoBanco(arquivo);
        try (InputStream recurso = getClass().getResourceAsStream("/banco/schema.sql")) {
            if (recurso == null) throw new IOException("Esquema SQL não encontrado.");
            String sql = new String(recurso.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conexao = banco.abrir(); Statement comando = conexao.createStatement()) {
                for (String trecho : sql.split(";")) {
                    if (!trecho.isBlank()) comando.execute(trecho);
                }
            }
        } catch (SQLException e) {
            throw new IOException("Não foi possível abrir o banco SQLite.", e);
        }
    }

    private long centavos(BigDecimal valor) { return valor.movePointRight(2).longValueExact(); }

    // Completa o catálogo com itens novos e conserva os dados dos produtos já cadastrados.
    public List<String> carregar(List<Produto> produtos) throws IOException {
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                try (PreparedStatement comando = conexao.prepareStatement("""
                        INSERT INTO produtos(codigo,nome,preco_centavos,estoque) VALUES(?,?,?,?)
                        ON CONFLICT(codigo) DO NOTHING
                        """)) {
                    for (Produto produto : produtos) {
                        comando.setString(1, produto.getCodigo());
                        comando.setString(2, produto.getNome());
                        comando.setLong(3, centavos(produto.getPreco()));
                        comando.setInt(4, produto.getQuantidadeEstoque());
                        comando.addBatch();
                    }
                    comando.executeBatch();
                }
                List<Produto> carregados = new ArrayList<>();
                try (Statement comando = conexao.createStatement();
                     ResultSet resultado = comando.executeQuery("SELECT * FROM produtos ORDER BY codigo")) {
                    while (resultado.next()) {
                        carregados.add(new Produto(resultado.getString("codigo"), resultado.getString("nome"),
                            BigDecimal.valueOf(resultado.getLong("preco_centavos"), 2), resultado.getInt("estoque")));
                    }
                }
                List<String> recibos = new ArrayList<>();
                try (Statement comando = conexao.createStatement();
                     ResultSet resultado = comando.executeQuery("SELECT recibo FROM vendas ORDER BY rowid DESC")) {
                    while (resultado.next()) recibos.add(resultado.getString("recibo"));
                }
                conexao.commit();
                produtos.clear();
                produtos.addAll(carregados);
                return recibos;
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Erro ao carregar produtos e recibos do banco.", e);
        }
    }

    public void salvarVenda(Recibo recibo, String tipoPagamento, int parcelas) {
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                long clienteId = salvarCliente(conexao, recibo.getCliente());
                // Baixa de estoque, venda, endereço, itens e pagamento são uma única transação.
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE produtos SET estoque=estoque-? WHERE codigo=? AND estoque>=?")) {
                    for (ItemPedido item : recibo.getItens()) {
                        comando.setInt(1, item.getQuantidadeAdquirida());
                        comando.setString(2, item.getProduto().getCodigo());
                        comando.setInt(3, item.getQuantidadeAdquirida());
                        if (comando.executeUpdate() != 1) {
                            throw new SQLException("Estoque no banco não permite concluir a venda.");
                        }
                    }
                }
                try (PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO vendas(id,cliente_id,data_hora,subtotal_centavos,desconto_centavos,total_centavos,estado,recibo) VALUES(?,?,?,?,?,?,'CONCLUIDO',?)")) {
                    comando.setString(1, recibo.getIdentificacaoPedido());
                    comando.setLong(2, clienteId);
                    comando.setString(3, recibo.getDataHora().toString());
                    comando.setLong(4, centavos(recibo.getSubtotal()));
                    comando.setLong(5, centavos(recibo.getDesconto()));
                    comando.setLong(6, centavos(recibo.getValorPago()));
                    comando.setString(7, recibo.formatar());
                    comando.executeUpdate();
                }
                if (recibo.getEnderecoEntrega() != null) {
                    salvarEndereco(conexao, recibo.getIdentificacaoPedido(), recibo.getEnderecoEntrega());
                }
                try (PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO itens_venda(venda_id,produto_codigo,produto_nome,quantidade,preco_unitario_centavos,subtotal_centavos) VALUES(?,?,?,?,?,?)")) {
                    for (ItemPedido item : recibo.getItens()) {
                        comando.setString(1, recibo.getIdentificacaoPedido());
                        comando.setString(2, item.getProduto().getCodigo());
                        comando.setString(3, item.getProduto().getNome());
                        comando.setInt(4, item.getQuantidadeAdquirida());
                        comando.setLong(5, centavos(item.getPrecoUnitario()));
                        comando.setLong(6, centavos(item.calcularSubtotal()));
                        comando.executeUpdate();
                    }
                }
                try (PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO pagamentos(venda_id,tipo,parcelas,valor_centavos,autorizacao,descricao) VALUES(?,?,?,?,?,?)")) {
                    comando.setString(1, recibo.getIdentificacaoPedido());
                    comando.setString(2, tipoPagamento);
                    comando.setInt(3, parcelas);
                    comando.setLong(4, centavos(recibo.getValorPago()));
                    comando.setString(5, recibo.getCodigoAutorizacao());
                    comando.setString(6, recibo.getMetodoPagamento());
                    comando.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível gravar a venda no banco. Nenhuma venda foi confirmada.", e);
        }
    }

    private void salvarEndereco(Connection conexao, String vendaId, EnderecoEntrega endereco) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                INSERT INTO enderecos_entrega(venda_id, cep, logradouro, numero, complemento, bairro, cidade, uf)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            comando.setString(1, vendaId);
            comando.setString(2, endereco.cep());
            comando.setString(3, endereco.logradouro());
            comando.setString(4, endereco.numero());
            comando.setString(5, endereco.complemento());
            comando.setString(6, endereco.bairro());
            comando.setString(7, endereco.cidade());
            comando.setString(8, endereco.uf());
            comando.executeUpdate();
        }
    }

    public String salvarCartao(Cliente cliente, CartaoCadastrado cartao) {
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                long clienteId = salvarCliente(conexao, cliente);
                String id;
                try (PreparedStatement comando = conexao.prepareStatement("""
                        INSERT INTO cartoes(id, cliente_id, titular, final_numero, validade)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(cliente_id, titular, final_numero, validade)
                        DO UPDATE SET titular = excluded.titular
                        RETURNING id
                        """)) {
                    comando.setString(1, cartao.id());
                    comando.setLong(2, clienteId);
                    comando.setString(3, cartao.titular());
                    comando.setString(4, cartao.finalNumero());
                    comando.setString(5, cartao.validade().toString());
                    try (ResultSet resultado = comando.executeQuery()) {
                        if (!resultado.next()) throw new SQLException("Cartão não encontrado após gravação.");
                        id = resultado.getString("id");
                    }
                }
                conexao.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível cadastrar o cartão no banco.", e);
        }
    }

    public List<CartaoCadastrado> listarCartoes(String identificacao) {
        try (Connection conexao = banco.abrir(); PreparedStatement comando = conexao.prepareStatement("""
                SELECT cartoes.id, cartoes.titular, cartoes.final_numero, cartoes.validade
                FROM cartoes JOIN clientes ON clientes.id = cartoes.cliente_id
                WHERE clientes.identificacao = ?
                ORDER BY cartoes.rowid DESC
                """)) {
            comando.setString(1, identificacao);
            List<CartaoCadastrado> cartoes = new ArrayList<>();
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    cartoes.add(new CartaoCadastrado(resultado.getString("id"), resultado.getString("titular"),
                            resultado.getString("final_numero"), YearMonth.parse(resultado.getString("validade"))));
                }
            }
            return cartoes;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível carregar os cartões cadastrados.", e);
        }
    }

    public AssinaturaVip cadastrarVip(Cliente cliente) {
        try (Connection conexao = banco.abrir(); PreparedStatement comando = conexao.prepareStatement("""
                INSERT INTO assinaturas_vip(identificacao,nome,email) VALUES (?,?,?)
                ON CONFLICT(identificacao) DO NOTHING
                """)) {
            comando.setString(1, cliente.getIdentificacao());
            comando.setString(2, cliente.getNome());
            comando.setString(3, cliente.getEmail().toLowerCase(Locale.ROOT));
            comando.executeUpdate();
            return buscarVip(conexao, cliente.getIdentificacao(), cliente.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Use o e-mail vinculado ao cadastro VIP."));
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível cadastrar no Clube VIP.", e);
        }
    }

    public Optional<AssinaturaVip> buscarVip(String identificacao, String email) {
        try (Connection conexao = banco.abrir()) {
            return buscarVip(conexao, identificacao, email);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível consultar o Clube VIP.", e);
        }
    }

    public boolean existeVipAtivoComEmail(String email, Instant agora) {
        try (Connection conexao = banco.abrir(); PreparedStatement comando = conexao.prepareStatement("""
                SELECT m.valido_ate FROM mensalidades_vip m
                JOIN assinaturas_vip a ON a.identificacao=m.assinante
                WHERE a.email=?
                """)) {
            comando.setString(1, email.toLowerCase(Locale.ROOT));
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    if (Instant.parse(linhas.getString("valido_ate")).isAfter(agora)) return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível consultar o Clube VIP.", e);
        }
    }

    private Optional<AssinaturaVip> buscarVip(Connection conexao, String identificacao, String email) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                SELECT a.identificacao,a.nome,a.email,
                    (SELECT m.valido_ate FROM mensalidades_vip m
                     WHERE m.assinante=a.identificacao ORDER BY m.id DESC LIMIT 1) AS valido_ate
                FROM assinaturas_vip a WHERE a.identificacao=? AND a.email=?
                """)) {
            comando.setString(1, identificacao);
            comando.setString(2, email.toLowerCase(Locale.ROOT));
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) return Optional.empty();
                String validade = linha.getString("valido_ate");
                return Optional.of(new AssinaturaVip(linha.getString("identificacao"), linha.getString("nome"),
                        linha.getString("email"), validade == null ? null : Instant.parse(validade)));
            }
        }
    }

    public AssinaturaVip salvarMensalidade(AssinaturaVip assinatura, ComprovantePagamento pagamento, Instant agora) {
        try (Connection conexao = banco.abrir()) {
            conexao.setAutoCommit(false);
            try {
                AssinaturaVip atual = buscarVip(conexao, assinatura.identificacao(), assinatura.email())
                        .orElseThrow(() -> new IllegalStateException("Cadastre-se no Clube VIP antes de pagar a mensalidade."));
                Instant validade = atual.validadeAposPagamento(agora);
                try (PreparedStatement comando = conexao.prepareStatement("""
                        INSERT INTO mensalidades_vip(assinante,autorizacao,valor_centavos,pago_em,valido_ate)
                        VALUES (?,?,?,?,?) ON CONFLICT(autorizacao) DO NOTHING
                        """)) {
                    comando.setString(1, atual.identificacao());
                    comando.setString(2, pagamento.codigoAutorizacao());
                    comando.setLong(3, centavos(pagamento.valor()));
                    comando.setString(4, agora.toString());
                    comando.setString(5, validade.toString());
                    comando.executeUpdate();
                }
                AssinaturaVip resultado = buscarVip(conexao, atual.identificacao(), atual.email()).orElseThrow();
                conexao.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                conexao.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível gravar a mensalidade VIP.", e);
        }
    }

    private long salvarCliente(Connection conexao, Cliente cliente) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO clientes(identificacao,nome,email,tipo) VALUES(?,?,?,?) ON CONFLICT(identificacao) DO UPDATE SET nome=excluded.nome,email=excluded.email,tipo=excluded.tipo")) {
            comando.setString(1, cliente.getIdentificacao());
            comando.setString(2, cliente.getNome());
            comando.setString(3, cliente.getEmail());
            comando.setString(4, cliente.getTipo());
            comando.executeUpdate();
        }
        try (PreparedStatement comando = conexao.prepareStatement("SELECT id FROM clientes WHERE identificacao=?")) {
            comando.setString(1, cliente.getIdentificacao());
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) throw new SQLException("Cliente não encontrado após gravação.");
                return resultado.getLong(1);
            }
        }
    }

    public void reporEstoque(String codigo, int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
        try (Connection conexao = banco.abrir(); PreparedStatement comando = conexao.prepareStatement(
                "UPDATE produtos SET estoque=estoque+? WHERE codigo=?")) {
            comando.setInt(1, quantidade);
            comando.setString(2, codigo);
            if (comando.executeUpdate() != 1) throw new SQLException("Produto não encontrado no banco.");
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível repor o estoque no banco.", e);
        }
    }
}
