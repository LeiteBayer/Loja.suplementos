package br.com.sistema.vendas.model;

import java.util.Set;

public record EnderecoEntrega(String cep, String logradouro, String numero, String complemento,
        String bairro, String cidade, String uf) {

    private static final Set<String> ESTADOS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF",
            "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS",
            "RO", "RR", "SC", "SP", "SE", "TO");

    public EnderecoEntrega {
        if (cep == null || !cep.strip().matches("[0-9]{5}-?[0-9]{3}")) {
            throw new IllegalArgumentException("Informe um CEP com oito dígitos.");
        }
        String digitos = cep.strip().replace("-", "");
        cep = digitos.substring(0, 5) + "-" + digitos.substring(5);
        logradouro = campo(logradouro, "rua ou avenida", 100, true);
        numero = campo(numero, "número", 20, true);
        complemento = campo(complemento, "complemento", 100, false);
        bairro = campo(bairro, "bairro", 60, true);
        cidade = campo(cidade, "cidade", 60, true);
        if (uf == null || !ESTADOS.contains(uf)) {
            throw new IllegalArgumentException("Selecione o estado da entrega.");
        }
    }

    private static String campo(String valor, String nome, int tamanhoMaximo, boolean obrigatorio) {
        String texto = valor == null ? "" : valor.strip();
        if ((obrigatorio && texto.isEmpty()) || texto.length() > tamanhoMaximo
                || texto.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Preencha corretamente o campo de entrega: " + nome + ".");
        }
        return texto;
    }

    public String formatar() {
        String detalhes = complemento.isEmpty() ? "" : ", " + complemento;
        return logradouro + ", " + numero + detalhes + " - " + bairro + "\n"
                + cidade + " - " + uf + " | CEP " + cep;
    }
}
