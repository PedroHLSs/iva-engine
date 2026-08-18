package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.excecao.ConjuntoRegrasInvalido;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O conjunto de regras e a versão que identifica a auditoria inteira. */
class ConjuntoRegrasTest {

    private static final ConjuntoRegras PADRAO =
            ConjuntoRegras.padrao(CenarioFicticio.coberturaTotal(), ToleranciaDeValor.exata());

    @Test
    void deveFixarOInventarioDoConjuntoPadrao() {
        // Este teste é o mecanismo que faz valer a regra "mudar qualquer regra
        // obriga a subir a versão do conjunto". Ele não tem como verificar a
        // intenção de quem editou; o que ele faz é impedir que a alteração passe
        // despercebida — mexeu em regra, este teste falha, e a decisão de
        // versionamento tem de ser tomada na hora, e não depois.
        Map<String, String> versaoPorRegra = PADRAO.regras().stream()
                .collect(Collectors.toMap(RegraAuditoria::id, RegraAuditoria::versao));

        assertThat(PADRAO.versao()).isEqualTo("2026.1");
        assertThat(versaoPorRegra).containsExactlyInAnyOrderEntriesOf(Map.of(
                "R01", "1.0.0",
                "R02", "1.0.0",
                "R03", "1.0.0",
                "R04", "1.0.0",
                "R05", "1.0.0",
                "R06", "1.0.0",
                "R07", "1.0.0"));
    }

    @Test
    void devePreservarAOrdemDeclaradaDasRegras() {
        // A ordem é parte do conjunto: é dela que sai a sequência das avaliações
        // de um mesmo item no relatório.
        assertThat(PADRAO.identificadores())
                .containsExactly("R01", "R02", "R03", "R04", "R05", "R06", "R07");
    }

    @Test
    void deveRecusarVersaoEmBranco() {
        assertThatThrownBy(() -> new ConjuntoRegras("  ", PADRAO.regras()))
                .isInstanceOf(ConjuntoRegrasInvalido.class)
                .hasMessageContaining("versão");
    }

    @Test
    void deveRecusarConjuntoSemRegraNenhuma() {
        assertThatThrownBy(() -> new ConjuntoRegras("2026.1", List.of()))
                .isInstanceOf(ConjuntoRegrasInvalido.class);
    }

    @Test
    void deveRecusarIdentificadorRepetido() {
        assertThatThrownBy(() -> new ConjuntoRegras("2026.1", List.of(
                new RegraCstCompativelComClassificacao(),
                new RegraCstCompativelComClassificacao())))
                .isInstanceOf(ConjuntoRegrasInvalido.class)
                .hasMessageContaining(RegraCstCompativelComClassificacao.ID);
    }

    @Test
    void deveSerImuneAAlteracaoDaListaRecebida() {
        List<RegraAuditoria> regras = new java.util.ArrayList<>(PADRAO.regras());
        ConjuntoRegras conjunto = new ConjuntoRegras("2026.1", regras);
        regras.clear();

        assertThat(conjunto.regras()).hasSize(7);
    }
}
