package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Correção de 04/10/2026 (D025): o lado da tabela da evidência de R01 e R06 diz o que a tabela encontrou — nenhum registro com o código —, em vez de vir vazio. Pelo contrato de Evidencia, encontrado vazio quer dizer "o campo não veio na nota", e a planilha escrevia "(não informado)" sobre um código que a nota informou. O critério e a versão das duas regras não mudaram. Valores fictícios.
class EvidenciaDoLadoDaTabelaTest {

    @Test
    void naR01OLadoDaTabelaDeveDizerQueNaoHaRegistro() {
        ItemDocumento item = ConstrutorDeItem.item().classificacao(CenarioFicticio.CODIGO).construir();
        RegraClassificacaoTributariaExiste regra =
                new RegraClassificacaoTributariaExiste(CenarioFicticio.coberturaTotal().classificacoesTributarias());

        List<Evidencia> evidencias = regra.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio())
                .achado().orElseThrow().evidencias();

        assertThat(daTabela(evidencias).valorEncontrado()).contains(Evidencia.NENHUM_REGISTRO_NA_TABELA);
        assertThat(doDocumento(evidencias).valorEncontrado()).contains(CenarioFicticio.CODIGO);
    }

    @Test
    void naR06OLadoDaTabelaDeveDizerQueNaoHaRegistro() {
        ItemDocumento item = ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).construir();
        RegraNcmExiste regra = new RegraNcmExiste(CenarioFicticio.coberturaTotal().ncm());

        List<Evidencia> evidencias = regra.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio())
                .achado().orElseThrow().evidencias();

        assertThat(daTabela(evidencias).valorEncontrado()).contains(Evidencia.NENHUM_REGISTRO_NA_TABELA);
        assertThat(doDocumento(evidencias).valorEncontrado()).contains(CenarioFicticio.NCM);
    }

    // As versões não mudaram: só o texto da evidência, e nenhuma tratativa reabre.
    @Test
    void asVersoesDeR01ER06NaoDevemMudar() {
        assertThat(new RegraClassificacaoTributariaExiste(CenarioFicticio.coberturaTotal().classificacoesTributarias())
                .versao()).isEqualTo("1.0.0");
        assertThat(new RegraNcmExiste(CenarioFicticio.coberturaTotal().ncm()).versao()).isEqualTo("1.0.0");
    }

    private static Evidencia daTabela(List<Evidencia> evidencias) {
        return evidencias.stream().filter(e -> e.origem() instanceof OrigemEvidencia.DeTabelaNormativa)
                .findFirst().orElseThrow();
    }

    private static Evidencia doDocumento(List<Evidencia> evidencias) {
        return evidencias.stream().filter(e -> e.origem() instanceof OrigemEvidencia.DoDocumento)
                .findFirst().orElseThrow();
    }
}
