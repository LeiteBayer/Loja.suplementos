package br.com.sistema.vendas.storage;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;

public class ConexaoBanco {
    private final String url;

    public ConexaoBanco(Path arquivo) throws IOException {
        Path caminho = arquivo.toAbsolutePath();
        Files.createDirectories(caminho.getParent());
        url = "jdbc:sqlite:" + caminho;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IOException("Driver SQLite ausente. Mantenha a pasta lib ao lado do JAR.", e);
        }
    }

    public Connection abrir() throws SQLException {
        Connection conexao = DriverManager.getConnection(url);
        try (Statement comando = conexao.createStatement()) {
            comando.execute("PRAGMA foreign_keys = ON");
            comando.execute("PRAGMA busy_timeout = 3000");
            return conexao;
        } catch (SQLException e) {
            conexao.close();
            throw e;
        }
    }
}
