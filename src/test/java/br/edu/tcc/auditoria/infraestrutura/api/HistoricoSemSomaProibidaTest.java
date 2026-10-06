package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;
import br.edu.tcc.auditoria.aplicacao.historico.LinhaDoHistorico;
import br.edu.tcc.auditoria.aplicacao.historico.PaginaDoHistorico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhum número do histórico soma não concluído a sem divergência.
 *
 * <p>O caso é o que a regra de colapso existe para evitar: seis produtos sem
 * divergência e quatro não concluídos. A resposta inteira é serializada e todo
 * número dela é recolhido; o seis e o quatro precisam estar lá, e o dez, não.</p>
 */
class HistoricoSemSomaProibidaTest {

    @Test
    void nenhumNumeroDaRespostaDoHistoricoEASomaDeNaoConcluidoComSemDivergencia() throws Exception {
        Map<EstadoDeConferencia, Integer> porEstado = new EnumMap<>(EstadoDeConferencia.class);
        porEstado.put(EstadoDeConferencia.POSSIVEL_DIVERGENCIA, 0);
        porEstado.put(EstadoDeConferencia.REQUER_CONFERENCIA, 0);
        porEstado.put(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR, 4);
        porEstado.put(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA, 6);
        LinhaDoHistorico linha = new LinhaDoHistorico(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), Instant.parse("1900-01-01T00:00:00Z"),
                "carga-ficticia", "2026.1", 1, 3, Optional.empty(), porEstado, 4,
                Optional.of(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR), Optional.empty());

        RespostaDoHistorico resposta = RespostaDoHistorico.de(
                new PaginaDoHistorico(List.of(linha), 1, 0, 20, List.of(), 0), versao -> NaturezaDaCarga.naoDeclarada(),
                execucaoId -> Optional.empty());
        JsonNode arvore = new ObjectMapper().findAndRegisterModules().valueToTree(resposta);
        List<Integer> numeros = new ArrayList<>();
        recolher(arvore, numeros);

        assertThat(numeros).as("autoverificação: os dois números estão na resposta").contains(6, 4);
        assertThat(numeros).as("nenhum campo soma não concluído a sem divergência").doesNotContain(10);
        assertThat(arvore.at("/linhas/0/situacaoMaisGrave").asText())
                .as("seis sem divergência e quatro não concluídos é \"não foi possível concluir\", e não limpo")
                .isEqualTo("NAO_FOI_POSSIVEL_CONCLUIR");
    }

    private static void recolher(JsonNode no, List<Integer> numeros) {
        if (no.isInt() || no.isLong()) {
            numeros.add(no.asInt());
        }
        no.forEach(filho -> recolher(filho, numeros));
    }
}
