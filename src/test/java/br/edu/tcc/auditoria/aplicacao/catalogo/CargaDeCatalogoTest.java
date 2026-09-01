package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CargaDeCatalogoTest {

    private static final ProcedenciaNormativa COBERTURA_PROCEDENCIA =
            CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM);

    private static final CoberturaDoCatalogo COBERTURA = new CoberturaDoCatalogo(
            COBERTURA_PROCEDENCIA, COBERTURA_PROCEDENCIA, COBERTURA_PROCEDENCIA);

    @Test
    void deveAceitarCargaComRegistrosEmUmaSoTabela() {
        CargaDeCatalogo carga = carga(List.of(classificacaoVigenteDeInicioAFim()));

        assertThat(carga.quantidadeDeRegistros()).isEqualTo(1);
        assertThat(carga.registrosDeNcm()).isEmpty();
    }

    @Test
    void deveRecusarCargaSemVersao() {
        assertThatThrownBy(() -> new CargaDeCatalogo(
                "  ", COBERTURA, List.of(classificacaoVigenteDeInicioAFim()),
                List.of(), List.of(), List.of()))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("versão");
    }

    @Test
    void deveRecusarCargaSemCoberturaDeclarada() {
        assertThatThrownBy(() -> new CargaDeCatalogo(
                "carga-ficticia", null, List.of(classificacaoVigenteDeInicioAFim()),
                List.of(), List.of(), List.of()))
                .as("sem cobertura, silêncio do catálogo não se distingue de tabela não carregada")
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void deveRecusarCargaSemNenhumRegistro() {
        assertThatThrownBy(() -> carga(List.of()))
                .as("gravar carga vazia produziria auditoria que não avalia nada")
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void deveRecusarVigenciasSobrepostasNoMesmoCodigo() {
        ClassificacaoTributaria primeira = classificacaoVigenteDeInicioAFim();
        ClassificacaoTributaria sobreposta = CatalogoFicticio.classificacao(
                CatalogoFicticio.CODIGO,
                CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO));

        assertThatThrownBy(() -> carga(List.of(primeira, sobreposta)))
                .as("duas versões do mesmo código valendo na mesma data tornam a consulta ambígua, "
                        + "e a carga precisa parar antes de gravar")
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("Vigências sobrepostas");
    }

    @Test
    void deveAceitarVigenciasSeguidasNoMesmoCodigo() {
        ClassificacaoTributaria primeira = classificacaoVigenteDeInicioAFim();
        ClassificacaoTributaria seguinte = CatalogoFicticio.classificacao(
                CatalogoFicticio.CODIGO,
                CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE));

        assertThat(carga(List.of(primeira, seguinte)).quantidadeDeRegistros()).isEqualTo(2);
    }

    @Test
    void deveRecusarListaNula() {
        assertThatThrownBy(() -> new CargaDeCatalogo(
                "carga-ficticia", COBERTURA, null, List.of(), List.of(), List.of()))
                .isInstanceOf(CatalogoInvalido.class);
    }

    private static CargaDeCatalogo carga(List<ClassificacaoTributaria> classificacoes) {
        return new CargaDeCatalogo(
                "carga-ficticia", COBERTURA, classificacoes, List.of(), List.of(), List.of());
    }

    private static ClassificacaoTributaria classificacaoVigenteDeInicioAFim() {
        return CatalogoFicticio.classificacao(
                CatalogoFicticio.CODIGO,
                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM));
    }
}
