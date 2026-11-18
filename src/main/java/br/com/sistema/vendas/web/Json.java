package br.com.sistema.vendas.web;

import java.util.Map;

// Escreve respostas JSON. As entradas são formulários, não JSON.
public final class Json {
    private Json() { }

    public static String escrever(Object valor) {
        if (valor == null) return "null";
        if (valor instanceof Number || valor instanceof Boolean) return valor.toString();
        if (valor instanceof Map<?, ?> mapa) {
            StringBuilder texto = new StringBuilder("{");
            for (var entrada : mapa.entrySet()) {
                if (texto.length() > 1) texto.append(',');
                texto.append(escrever(entrada.getKey().toString())).append(':')
                     .append(escrever(entrada.getValue()));
            }
            return texto.append('}').toString();
        }
        if (valor instanceof Iterable<?> lista) {
            StringBuilder texto = new StringBuilder("[");
            for (Object item : lista) {
                if (texto.length() > 1) texto.append(',');
                texto.append(escrever(item));
            }
            return texto.append(']').toString();
        }
        StringBuilder texto = new StringBuilder("\"");
        for (char c : valor.toString().toCharArray()) {
            switch (c) {
                case '"' -> texto.append("\\\"");
                case '\\' -> texto.append("\\\\");
                case '\n' -> texto.append("\\n");
                case '\r' -> texto.append("\\r");
                case '\t' -> texto.append("\\t");
                default -> {
                    if (c < 32) texto.append(String.format("\\u%04x", (int)c));
                    else texto.append(c);
                }
            }
        }
        return texto.append('"').toString();
    }
}
