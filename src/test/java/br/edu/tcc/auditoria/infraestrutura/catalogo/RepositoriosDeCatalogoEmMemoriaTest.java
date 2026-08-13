package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoriosDeCatalogoEmMemoriaTest {

    private static final CodigoClassificacaoTributaria CODIGO =
            new CodigoClassificacaoTributaria(CatalogoFicticio.CODIGO);
    private static final Ncm NCM = new Ncm(CatalogoFicticio.NCM);
    private static final Abrangencia ABRANGENCIA = new Abrangencia(CatalogoFicticio.ABRANGENCIA);

    @Test
    void deveRecusarACargaDeDuasVersoesDoMesmoCodigoComVigenciasSobrepostas() {
        List<ClassificacaoTributaria> sobrepostas = List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.INICIO_SEGUINTE)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM)));

        assertThatThrownBy(() -> new RepositorioClassificacaoTributariaEmMemoria(sobrepostas))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("sobrepostas");
    }

    @Test
    void deveRecusarACargaInteiraQuandoUmaUnicaSerieEContraditoria() {
        // Catálogo meio carregado responderia vazio para o que faltou, e vazio
        // significa "o catálogo nada diz" — o erro de carga viraria "não avaliado".
        List<ClassificacaoTributaria> comUmaSerieRuim = List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO_ALTERNATIVO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE)));

        assertThatThrownBy(() -> new RepositorioClassificacaoTributariaEmMemoria(comUmaSerieRuim))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void deveAceitarACargaDeVersoesSucessivasDoMesmoCodigo() {
        RepositorioClassificacaoTributariaEmMemoria repositorio =
                new RepositorioClassificacaoTributariaEmMemoria(List.of(
                        CatalogoFicticio.classificacao(
                                CatalogoFicticio.CODIGO,
                                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                        CatalogoFicticio.classificacao(
                                CatalogoFicticio.CODIGO,
                                CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE))));

        assertThat(repositorio.quantidadeDeCodigos()).isEqualTo(1);
        assertThat(repositorio.buscarVigenteEm(CODIGO, CatalogoFicticio.INICIO)).isPresent();
        assertThat(repositorio.buscarVigenteEm(CODIGO, CatalogoFicticio.INICIO_SEGUINTE)).isPresent();
    }

    @Test
    void naoDeveRetornarClassificacaoForaDaVigencia() {
        RepositorioClassificacaoTributariaEmMemoria repositorio =
                new RepositorioClassificacaoTributariaEmMemoria(List.of(
                        CatalogoFicticio.classificacao(
                                CatalogoFicticio.CODIGO,
                                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));

        assertThat(repositorio.buscarVigenteEm(CODIGO, CatalogoFicticio.INICIO.minusDays(1))).isEmpty();
        assertThat(repositorio.buscarVigenteEm(CODIGO, CatalogoFicticio.FIM.plusDays(1))).isEmpty();
    }

    @Test
    void deveResponderVazioParaCodigoQueNaoEstaNoCatalogo() {
        RepositorioClassificacaoTributariaEmMemoria repositorio =
                new RepositorioClassificacaoTributariaEmMemoria(List.of());

        assertThat(repositorio.buscarVigenteEm(CODIGO, CatalogoFicticio.INICIO)).isEmpty();
    }

    @Test
    void deveRecusarACargaDeDoisRegistrosDoMesmoNcmComVigenciasSobrepostas() {
        List<RegistroNcm> sobrepostos = List.of(
                CatalogoFicticio.registroNcm(
                        CatalogoFicticio.NCM, CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.registroNcm(
                        CatalogoFicticio.NCM, CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM)));

        assertThatThrownBy(() -> new RepositorioNcmEmMemoria(sobrepostos))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void naoDeveRetornarRegistroDeNcmForaDaVigencia() {
        RepositorioNcmEmMemoria repositorio = new RepositorioNcmEmMemoria(List.of(
                CatalogoFicticio.registroNcm(
                        CatalogoFicticio.NCM,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));

        assertThat(repositorio.buscarVigenteEm(NCM, CatalogoFicticio.FIM)).isPresent();
        assertThat(repositorio.buscarVigenteEm(NCM, CatalogoFicticio.FIM.plusDays(1))).isEmpty();
    }

    @Test
    void deveAceitarOMesmoNcmEmDoisAnexosNaMesmaData() {
        // A chave é o par NCM e anexo. Tratar isso como sobreposição seria afirmar
        // que um NCM só pode pertencer a um anexo de cada vez.
        RepositorioItemAnexoEmMemoria repositorio = new RepositorioItemAnexoEmMemoria(List.of(
                CatalogoFicticio.itemAnexo(
                        CatalogoFicticio.NCM,
                        CatalogoFicticio.ANEXO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.itemAnexo(
                        CatalogoFicticio.NCM,
                        CatalogoFicticio.ANEXO_ALTERNATIVO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO))));

        assertThat(repositorio.quantidadeDeVinculos()).isEqualTo(2);
        assertThat(repositorio.buscarVigentesEm(NCM, CatalogoFicticio.INICIO)).hasSize(2);
    }

    @Test
    void deveRecusarOMesmoParNcmEAnexoComVigenciasSobrepostas() {
        List<ItemAnexo> sobrepostos = List.of(
                CatalogoFicticio.itemAnexo(
                        CatalogoFicticio.NCM,
                        CatalogoFicticio.ANEXO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.itemAnexo(
                        CatalogoFicticio.NCM,
                        CatalogoFicticio.ANEXO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM)));

        assertThatThrownBy(() -> new RepositorioItemAnexoEmMemoria(sobrepostos))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void naoDeveRetornarVinculoDeAnexoForaDaVigencia() {
        RepositorioItemAnexoEmMemoria repositorio = new RepositorioItemAnexoEmMemoria(List.of(
                CatalogoFicticio.itemAnexo(
                        CatalogoFicticio.NCM,
                        CatalogoFicticio.ANEXO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));

        assertThat(repositorio.buscarVigentesEm(NCM, CatalogoFicticio.INICIO)).hasSize(1);
        assertThat(repositorio.buscarVigentesEm(NCM, CatalogoFicticio.FIM.plusDays(1))).isEmpty();
    }

    @Test
    void deveAceitarAbrangenciasDiferentesDoMesmoTributoNaMesmaData() {
        RepositorioAliquotaEmMemoria repositorio = new RepositorioAliquotaEmMemoria(List.of(
                CatalogoFicticio.aliquota(
                        Tributo.IBS_UF,
                        CatalogoFicticio.ABRANGENCIA,
                        "99.99",
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.aliquota(
                        Tributo.IBS_UF,
                        "ABRANGENCIA-YY",
                        "88.88",
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO))));

        assertThat(repositorio.buscarVigentesEm(Tributo.IBS_UF, CatalogoFicticio.INICIO)).hasSize(2);
    }

    @Test
    void deveRecusarOMesmoParTributoEAbrangenciaComVigenciasSobrepostas() {
        List<AliquotaVigente> sobrepostas = List.of(
                CatalogoFicticio.aliquota(
                        Tributo.CBS,
                        CatalogoFicticio.ABRANGENCIA,
                        "99.99",
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO)),
                CatalogoFicticio.aliquota(
                        Tributo.CBS,
                        CatalogoFicticio.ABRANGENCIA,
                        "88.88",
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.FIM)));

        assertThatThrownBy(() -> new RepositorioAliquotaEmMemoria(sobrepostas))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void naoDeveRetornarAliquotaForaDaVigencia() {
        RepositorioAliquotaEmMemoria repositorio = new RepositorioAliquotaEmMemoria(List.of(
                CatalogoFicticio.aliquota(
                        Tributo.CBS,
                        CatalogoFicticio.ABRANGENCIA,
                        "99.99",
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));

        assertThat(repositorio.buscarVigenteEm(Tributo.CBS, ABRANGENCIA, CatalogoFicticio.INICIO)).isPresent();
        assertThat(repositorio.buscarVigenteEm(Tributo.CBS, ABRANGENCIA, CatalogoFicticio.INICIO.minusDays(1)))
                .isEmpty();
        assertThat(repositorio.buscarVigenteEm(Tributo.CBS, ABRANGENCIA, CatalogoFicticio.FIM.plusDays(1)))
                .isEmpty();
    }

    @Test
    void deveRecusarCargaComRegistroNulo() {
        List<ClassificacaoTributaria> comNulo = new java.util.ArrayList<>();
        comNulo.add(null);

        assertThatThrownBy(() -> new RepositorioClassificacaoTributariaEmMemoria(comNulo))
                .isInstanceOf(CatalogoInvalido.class);
    }
}
