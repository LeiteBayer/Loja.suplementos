package br.com.sistema.vendas.storage;

import java.nio.file.*;
import java.sql.*;

// Consulta de apoio para demonstrar que os dados estão em tabelas relacionais.
public class ConsultarBanco {
    public static void main(String[] args) throws Exception {
        Path arquivo = Path.of(args.length == 0 ? "dados/loja.db" : args[0]);
        if (!Files.isRegularFile(arquivo)) {
            System.out.println("Banco ainda não existe. Inicie a loja primeiro.");
            return;
        }
        try (Connection conexao = new ConexaoBanco(arquivo).abrir()) {
            for (String tabela : new String[]{"produtos", "clientes", "vendas", "itens_venda", "pagamentos"}) {
                try (Statement comando = conexao.createStatement();
                     ResultSet resultado = comando.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
                    resultado.next();
                    System.out.printf("%-15s %d registro(s)%n", tabela, resultado.getLong(1));
                }
            }
            System.out.println("\nVendas e pagamentos (valores em centavos):");
            String sql = "SELECT v.id,c.nome,p.tipo,p.parcelas,v.total_centavos FROM vendas v " +
                "JOIN clientes c ON c.id=v.cliente_id JOIN pagamentos p ON p.venda_id=v.id ORDER BY v.rowid DESC";
            try (Statement comando = conexao.createStatement(); ResultSet resultado = comando.executeQuery(sql)) {
                while (resultado.next()) {
                    System.out.printf("%s | %s | %s | %dx | %d%n", resultado.getString(1),
                        resultado.getString(2), resultado.getString(3), resultado.getInt(4), resultado.getLong(5));
                }
            }
        }
    }
}
