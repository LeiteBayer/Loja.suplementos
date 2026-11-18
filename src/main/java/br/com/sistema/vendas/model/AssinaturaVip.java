package br.com.sistema.vendas.model;

import java.time.Instant;
import java.time.ZoneId;

public record AssinaturaVip(String identificacao, String nome, String email, Instant validaAte) {
    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    public boolean ativaEm(Instant agora) {
        return validaAte != null && agora.isBefore(validaAte);
    }

    public Instant validadeAposPagamento(Instant agora) {
        Instant inicio = ativaEm(agora) ? validaAte : agora;
        return inicio.atZone(FUSO).plusMonths(1).toInstant();
    }
}
