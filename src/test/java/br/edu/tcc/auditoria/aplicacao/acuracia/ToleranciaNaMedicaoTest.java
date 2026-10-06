package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.LoteDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.OrigemDaTolerancia;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.acuracia.RotuloEsperado;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;
import br.edu.tcc.auditoria.dominio.regras.RegraCstCompativelComClassificacao;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioAliquotaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioClassificacaoTributariaEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioItemAnexoEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.catalogo.RepositorioNcmEmMemoria;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// D023 (04/10/2026): a medição de acurácia diz com que tolerância a R05 foi medida, com a origem. As métricas da R05 dependem dela, e duas medições com tolerâncias diferentes não são comparáveis sem isso. Valores fictícios.
class ToleranciaNaMedicaoTest {

    @Test
    void oRelatorioDeveLevarAToleranciaPadraoDitaComoPadrao() {
        ToleranciaDaExecucao usada = new ToleranciaDaExecucao(
                ToleranciaDeValor.de(new BigDecimal("0.07")), OrigemDaTolerancia.PADRAO);

        RelatorioDeAcuracia relatorio = servico(usada).avaliar(Path.of("origem-ficticia"), Path.of("gabarito-ficticio.csv"));

        assertThat(relatorio.tolerancia()).contains(usada);
    }

    @Test
    void oRelatorioDeveLevarAToleranciaConfiguradaDitaComoConfigurada() {
        ToleranciaDaExecucao usada = new ToleranciaDaExecucao(
                ToleranciaDeValor.de(new BigDecimal("0.99")), OrigemDaTolerancia.CONFIGURADA);

        RelatorioDeAcuracia relatorio = servico(usada).avaliar(Path.of("origem-ficticia"), Path.of("gabarito-ficticio.csv"));

        assertThat(relatorio.tolerancia()).contains(usada);
    }

    private static ServicoDeAvaliacaoDeAcuracia servico(ToleranciaDaExecucao tolerancia) {
        LoteDeDocumentos lote = new LoteDeDocumentos("a".repeat(64), List.of(new DocumentoComItens(
                CenarioFicticio.documento(),
                List.of(ConstrutorDeItem.item()
                        .numero(1)
                        .ncm(CatalogoFicticio.NCM)
                        .classificacao(CenarioFicticio.CODIGO)
                        .cstIbs(CenarioFicticio.CST_ALTERNATIVO)
                        .construir()))));
        Gabarito gabarito = new Gabarito(List.of(new LinhaDeGabarito(2,
                new EnderecoDaAvaliacao(new ChaveAcesso(CenarioFicticio.CHAVE_PRIMEIRA), 1,
                        RegraCstCompativelComClassificacao.ID),
                RotuloEsperado.ACHADO)));

        return new ServicoDeAvaliacaoDeAcuracia(
                origem -> lote,
                ToleranciaNaMedicaoTest::catalogo,
                arquivo -> gabarito,
                new MotorAuditoria(),
                new ComparadorDeGabarito(),
                new EscritorDeRelatorioDeAcuracia() {
                    @Override
                    public void escrever(RelatorioDeAcuracia relatorio, Path destino) {
                    }

                    @Override
                    public String extensao() {
                        return "csv";
                    }
                },
                tolerancia);
    }

    private static CatalogoParaAuditoria catalogo() {
        return new CatalogoParaAuditoria(
                "catalogo-ficticio-0",
                CenarioFicticio.coberturaTotal(),
                NaturezaDaCarga.deUmaSoProcedencia(Natureza.FICTICIO,
                        List.of("uma classificacao ficticia"), List.of("um NCM ficticio"), List.of(), List.of()),
                new RepositorioClassificacaoTributariaEmMemoria(List.of(
                        CenarioFicticio.classificacao(CenarioFicticio.CODIGO, CenarioFicticio.CST))),
                new RepositorioNcmEmMemoria(List.of(CenarioFicticio.registroNcm(CenarioFicticio.NCM))),
                new RepositorioItemAnexoEmMemoria(List.of()),
                new RepositorioAliquotaEmMemoria(List.of()));
    }
}
