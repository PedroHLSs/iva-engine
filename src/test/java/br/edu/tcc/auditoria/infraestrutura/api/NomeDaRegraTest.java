package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O nome por extenso de cada regra.
 *
 * <p>O primeiro teste é o que torna aceitável uma tabela por identificador: sem
 * ele, regra nova sairia na tela com o motivo de ausência, e quem a criou só
 * descobriria olhando a interface. Com ele, descobre no build.</p>
 */
class NomeDaRegraTest {

    private static final ConjuntoRegras PADRAO =
            ConjuntoRegras.padrao(CenarioFicticio.coberturaTotal(), ToleranciaDeValor.exata());

    private static final String REGRA_FORA_DO_CONJUNTO = "RX9-ficticia";

    @Test
    void deveTerNomeParaExatamenteAsRegrasDoConjuntoPadrao() {
        assertThat(NomeDaRegra.identificadoresComNome())
                .as("regra nova sem nome quebra aqui; nome de regra removida também")
                .containsExactlyInAnyOrderElementsOf(PADRAO.identificadores());
    }

    @Test
    void deveDarNomeATodaRegraDoConjuntoPadraoSemMotivoDeAusencia() {
        for (String regraId : PADRAO.identificadores()) {
            NomeDaRegra nome = NomeDaRegra.de(regraId);

            assertThat(nome.nome()).as("nome de %s", regraId).isNotBlank();
            assertThat(nome.motivoDaAusencia()).as("motivo de %s", regraId).isNull();
        }
    }

    @Test
    void naoDeveUsarOProprioCodigoComoNome() {
        for (String regraId : PADRAO.identificadores()) {
            assertThat(NomeDaRegra.de(regraId).nome())
                    .as("repetir o código no lugar do nome é voltar ao que a tela já fazia")
                    .doesNotContain(regraId);
        }
    }

    @Test
    void deveDarUmNomeDiferenteACadaRegra() {
        List<String> nomes = PADRAO.identificadores().stream()
                .map(regraId -> NomeDaRegra.de(regraId).nome())
                .toList();
        Set<String> distintos = new HashSet<>(nomes);

        assertThat(distintos)
                .as("dois nomes iguais tornariam duas regras indistinguíveis na tela")
                .hasSameSizeAs(nomes);
    }

    @Test
    void deveEscreverOMotivoQuandoARegraNaoEstaNoConjuntoDeHoje() {
        NomeDaRegra nome = NomeDaRegra.de(REGRA_FORA_DO_CONJUNTO);

        assertThat(nome.nome())
                .as("nome deduzido do código seria inventado")
                .isNull();
        assertThat(nome.motivoDaAusencia())
                .contains(REGRA_FORA_DO_CONJUNTO)
                .contains("não está no conjunto de regras que o código de hoje monta");
    }

    @Test
    void deveRecusarNomeAusenteSemMotivo() {
        assertThatThrownBy(() -> new NaoAvaliadaExposta(
                documentoFicticio(), 1, REGRA_FORA_DO_CONJUNTO, null, null,
                "1.0.0", "NAO_AVALIADO", "Motivo ficticio de teste."))
                .as("sem nome e sem motivo, a tela voltaria a escrever só o código")
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("sem dizer por quê");
    }

    @Test
    void deveRecusarNomePresenteEAusenteAoMesmoTempo() {
        assertThatThrownBy(() -> new RespostaDaExecucao.PorRegra(
                REGRA_FORA_DO_CONJUNTO,
                "Nome ficticio",
                "Motivo ficticio de teste.",
                0,
                0,
                ContagemDerivada.conformesDe(10, 0, 0, "quantidadeItens"),
                List.of()))
                .isInstanceOf(RespostaInvalida.class)
                .hasMessageContaining("presente e ausente ao mesmo tempo");
    }

    private static DocumentoExposto documentoFicticio() {
        return new DocumentoExposto(
                "a".repeat(64), null, "Motivo ficticio de teste.", "55", "1", "999",
                java.time.LocalDate.of(2026, 1, 15), "MG");
    }
}
