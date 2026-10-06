package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D015 (03/10/2026): a R07 só conclui CONFORME quando o catálogo afirmou algo sobre os campos exigidos daquele código. Percorre todos os caminhos de CaminhoDaR07 e todos os valores de ResultadoAvaliacao.
class R07NaoConcluiSemDeclaracaoTest {

    @Test
    void nenhumCaminhoConcluiConformeSemQueOCatalogoTenhaAfirmadoAlgo() {
        Map<ResultadoAvaliacao, Set<CaminhoDaR07>> porResultado = rodarTodos();

        for (CaminhoDaR07 caminho : porResultado.get(ResultadoAvaliacao.CONFORME)) {
            assertThat(caminho.catalogoAfirmou())
                    .as("o caminho %s concluiu CONFORME sem o catálogo ter declarado os campos", caminho)
                    .isTrue();
        }
    }

    @Test
    void cadaCaminhoDeveDarOResultadoEsperado() {
        for (CaminhoDaR07 caminho : CaminhoDaR07.values()) {
            assertThat(caminho.avaliar().resultado()).as(caminho.name()).isEqualTo(caminho.esperado());
        }
    }

    @Test
    void celulaEmBrancoDeveSerNaoAvaliadaComOMotivoEscrito() {
        Avaliacao avaliacao = CaminhoDaR07.CAMPOS_NAO_DECLARADOS.avaliar();

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(((Avaliacao.NaoAvaliada) avaliacao).motivo())
                .contains("não declara")
                .contains("NENHUM");
    }

    // Sem esta conferência, um caminho a menos no enum, ou um resultado que nenhum caminho produz, passaria em silêncio.
    @Test
    void osCaminhosDevemCobrirOsTresResultadosEOsDoisLadosDaDeclaracao() {
        Map<ResultadoAvaliacao, Set<CaminhoDaR07>> porResultado = rodarTodos();

        for (ResultadoAvaliacao resultado : ResultadoAvaliacao.values()) {
            assertThat(porResultado.get(resultado)).as("nenhum caminho produz %s", resultado).isNotEmpty();
        }
        assertThat(EnumSet.allOf(CaminhoDaR07.class)).anyMatch(CaminhoDaR07::catalogoAfirmou);
        assertThat(EnumSet.allOf(CaminhoDaR07.class)).anyMatch(caminho -> !caminho.catalogoAfirmou());
        int contados = porResultado.values().stream().mapToInt(Set::size).sum();
        assertThat(contados).isEqualTo(CaminhoDaR07.values().length);
    }

    // Método auxiliar que roda todos os caminhos e agrupa pelo resultado, com os três resultados presentes no mapa.
    private static Map<ResultadoAvaliacao, Set<CaminhoDaR07>> rodarTodos() {
        Map<ResultadoAvaliacao, Set<CaminhoDaR07>> porResultado = new EnumMap<>(ResultadoAvaliacao.class);
        for (ResultadoAvaliacao resultado : ResultadoAvaliacao.values()) {
            porResultado.put(resultado, EnumSet.noneOf(CaminhoDaR07.class));
        }
        for (CaminhoDaR07 caminho : CaminhoDaR07.values()) {
            porResultado.get(caminho.avaliar().resultado()).add(caminho);
        }
        return porResultado;
    }
}
