package br.com.sistema.vendas;

import br.com.sistema.vendas.web.Servidor;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getProperty("porta", "8080"));
        Path dados = Path.of(System.getProperty("dados", "dados/loja.db"));
        Servidor servidor = new Servidor(porta, dados);
        servidor.iniciar();
        System.out.println("Loja iniciada: http://localhost:" + servidor.getPorta());
        System.out.println("Pagamentos simulados. Use apenas dados fictícios.");
        System.out.println("Para encerrar, pressione Ctrl+C.");
    }
}
