package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// D016 (03/10/2026): o conjunto padrão entrega à R05 a cobertura da tabela de classificações, e não a de NCM ou a de itens de anexo. As coberturas daqui divergem de propósito, para uma troca entre elas aparecer. Valores fictícios.
class R05NoConjuntoPadraoTest {

    private static final ProcedenciaNormativa COBRE = CenarioFicticio.coberturaTotal().classificacoesTributarias();
    private static final ProcedenciaNormativa NAO_COBRE = CenarioFicticio.coberturaForaDaData().classificacoesTributarias();

    @Test
    void classificacaoForaDaCoberturaComAsOutrasDentroDeveDarNaoAvaliado() {
        assertThat(r05(new CoberturaDoCatalogo(NAO_COBRE, COBRE, COBRE)))
                .isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    @Test
    void classificacaoDentroDaCoberturaComAsOutrasForaDeveSeguirAD4() {
        assertThat(r05(new CoberturaDoCatalogo(COBRE, NAO_COBRE, NAO_COBRE)))
                .isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Método auxiliar que acha a R05 no conjunto padrão e a aplica a um item de código fora da tabela, com valores cheios.
    private static ResultadoAvaliacao r05(CoberturaDoCatalogo cobertura) {
        RegraAuditoria r05 = ConjuntoRegras.padrao(cobertura, ToleranciaDeValor.exata()).regras().stream()
                .filter(regra -> regra.id().equals(RegraValorDeTributoConfere.ID))
                .findFirst()
                .orElseThrow();
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs("1000.00").baseCalculoCbs("1000.00")
                .valorIbsUf("999.90").valorIbsMunicipal("999.90").valorCbs("999.90")
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, "99.99"))
                .com(CenarioFicticio.aliquota(Tributo.IBS_MUN, "99.99"))
                .com(CenarioFicticio.aliquota(Tributo.CBS, "99.99"));
        return r05.avaliar(item, CenarioFicticio.documento(), catalogo).resultado();
    }
}
