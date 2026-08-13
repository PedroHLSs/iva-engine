package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os repositórios usados aqui são falsos, montados no próprio teste. O contexto
 * é domínio puro e seu teste não precisa de adaptador nenhum.
 */
class ContextoNormativoNaDataTest {

    private static final CodigoClassificacaoTributaria CODIGO =
            new CodigoClassificacaoTributaria(CatalogoFicticio.CODIGO);
    private static final Ncm NCM = new Ncm(CatalogoFicticio.NCM);
    private static final Abrangencia ABRANGENCIA = new Abrangencia(CatalogoFicticio.ABRANGENCIA);

    @Test
    void deveResponderComORegistroVigenteNaDataDeReferencia() {
        ContextoNormativo contexto = contextoEm(CatalogoFicticio.INICIO);

        assertThat(contexto.classificacaoTributaria(CODIGO)).isPresent();
        assertThat(contexto.registroNcm(NCM)).isPresent();
        assertThat(contexto.anexosDoNcm(NCM)).hasSize(1);
        assertThat(contexto.aliquota(Tributo.CBS, ABRANGENCIA)).isPresent();
    }

    @Test
    void naoDeveRetornarRegistroEmDataAnteriorAoInicioDaVigencia() {
        ContextoNormativo contexto = contextoEm(CatalogoFicticio.INICIO.minusDays(1));

        assertThat(contexto.classificacaoTributaria(CODIGO)).isEmpty();
        assertThat(contexto.registroNcm(NCM)).isEmpty();
        assertThat(contexto.anexosDoNcm(NCM)).isEmpty();
        assertThat(contexto.aliquota(Tributo.CBS, ABRANGENCIA)).isEmpty();
        assertThat(contexto.aliquotas(Tributo.CBS)).isEmpty();
    }

    @Test
    void naoDeveRetornarRegistroEmDataPosteriorAoFimDaVigencia() {
        ContextoNormativo contexto = contextoEm(CatalogoFicticio.FIM.plusDays(1));

        assertThat(contexto.classificacaoTributaria(CODIGO)).isEmpty();
        assertThat(contexto.registroNcm(NCM)).isEmpty();
        assertThat(contexto.anexosDoNcm(NCM)).isEmpty();
        assertThat(contexto.aliquota(Tributo.CBS, ABRANGENCIA)).isEmpty();
    }

    @Test
    void deveIncluirOsDoisExtremosDaVigencia() {
        assertThat(contextoEm(CatalogoFicticio.INICIO).classificacaoTributaria(CODIGO)).isPresent();
        assertThat(contextoEm(CatalogoFicticio.FIM).classificacaoTributaria(CODIGO)).isPresent();
    }

    @Test
    void doisContextosEmDatasDiferentesDevemResponderVersoesDiferentesDoMesmoCodigo() {
        // É o cenário que justifica a etapa inteira: o mesmo código, auditado em
        // duas datas, tem que produzir dois fundamentos distintos.
        List<ClassificacaoTributaria> duasVersoes = List.of(
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)),
                CatalogoFicticio.classificacao(
                        CatalogoFicticio.CODIGO,
                        CatalogoFicticio.procedenciaAberta(CatalogoFicticio.INICIO_SEGUINTE)));

        ContextoNormativo antes = contextoCom(CatalogoFicticio.INICIO, duasVersoes);
        ContextoNormativo depois = contextoCom(CatalogoFicticio.INICIO_SEGUINTE, duasVersoes);

        assertThat(antes.classificacaoTributaria(CODIGO).orElseThrow().vigenciaFim())
                .contains(CatalogoFicticio.FIM);
        assertThat(depois.classificacaoTributaria(CODIGO).orElseThrow().vigenciaFim())
                .isEmpty();
    }

    @Test
    void deveExigirDataDeReferenciaNaConstrucao() {
        assertThatThrownBy(() -> contextoCom(null, List.of()))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("data do documento");
    }

    @Test
    void deveExigirTodosOsRepositoriosNaConstrucao() {
        assertThatThrownBy(() -> new ContextoNormativoNaData(
                CatalogoFicticio.INICIO, null, null, null, null))
                .isInstanceOf(CatalogoInvalido.class);
    }

    @Test
    void nenhumMetodoDoContextoNormativoDeveAceitarData() {
        // Garantia estrutural: uma regra de auditoria não tem por onde escolher
        // a data de consulta ao catálogo, nem por descuido nem de propósito.
        List<String> metodosQueAceitamData = new ArrayList<>();

        for (Method metodo : ContextoNormativo.class.getMethods()) {
            for (Class<?> tipoDoParametro : metodo.getParameterTypes()) {
                if (ehTipoTemporal(tipoDoParametro)) {
                    metodosQueAceitamData.add("%s(%s)".formatted(metodo.getName(), tipoDoParametro.getName()));
                }
            }
        }

        assertThat(metodosQueAceitamData)
                .as("Nenhum método de ContextoNormativo pode receber data: a data é fixada na construção "
                        + "e as regras não podem sobrepô-la — ver D003 em docs/DECISOES-ARQUITETURA.md.")
                .isEmpty();
    }

    @Test
    void nenhumMetodoDoContextoNormativoDeveDevolverData() {
        // Quem precisar da vigência aplicada num Achado deve tomá-la do registro
        // consultado, e não de uma data solta devolvida pelo contexto.
        List<String> metodosQueDevolvemData = new ArrayList<>();

        for (Method metodo : ContextoNormativo.class.getMethods()) {
            if (ehTipoTemporal(metodo.getReturnType())) {
                metodosQueDevolvemData.add(metodo.getName());
            }
        }

        assertThat(metodosQueDevolvemData).isEmpty();
    }

    @Test
    void aImplementacaoNaoDeveExporADataDeReferencia() {
        List<String> metodosPublicosQueDevolvemData = new ArrayList<>();

        for (Method metodo : ContextoNormativoNaData.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(metodo.getModifiers()) && ehTipoTemporal(metodo.getReturnType())) {
                metodosPublicosQueDevolvemData.add(metodo.getName());
            }
        }

        assertThat(metodosPublicosQueDevolvemData).isEmpty();
    }

    private static boolean ehTipoTemporal(Class<?> tipo) {
        return TemporalAccessor.class.isAssignableFrom(tipo) || tipo.getName().startsWith("java.time.");
    }

    private static ContextoNormativo contextoEm(LocalDate dataDeReferencia) {
        return contextoCom(dataDeReferencia, List.of(CatalogoFicticio.classificacao(
                CatalogoFicticio.CODIGO,
                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM))));
    }

    private static ContextoNormativo contextoCom(
            LocalDate dataDeReferencia, List<ClassificacaoTributaria> classificacoes) {

        List<RegistroNcm> ncms = List.of(CatalogoFicticio.registroNcm(
                CatalogoFicticio.NCM,
                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)));
        List<ItemAnexo> anexos = List.of(CatalogoFicticio.itemAnexo(
                CatalogoFicticio.NCM,
                CatalogoFicticio.ANEXO,
                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)));
        List<AliquotaVigente> aliquotas = List.of(CatalogoFicticio.aliquota(
                Tributo.CBS,
                CatalogoFicticio.ABRANGENCIA,
                "99.99",
                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM)));

        return new ContextoNormativoNaData(
                dataDeReferencia,
                (codigo, data) -> classificacoes.stream()
                        .filter(registro -> registro.codigo().equals(codigo) && registro.vigenteEm(data))
                        .findFirst(),
                (ncm, data) -> ncms.stream()
                        .filter(registro -> registro.ncm().equals(ncm) && registro.vigenteEm(data))
                        .findFirst(),
                (ncm, data) -> anexos.stream()
                        .filter(registro -> registro.ncm().equals(ncm) && registro.vigenteEm(data))
                        .toList(),
                new RepositorioAliquota() {
                    @Override
                    public Optional<AliquotaVigente> buscarVigenteEm(
                            Tributo tributo, Abrangencia abrangencia, LocalDate data) {
                        return buscarVigentesEm(tributo, data).stream()
                                .filter(registro -> registro.abrangencia().equals(abrangencia))
                                .findFirst();
                    }

                    @Override
                    public List<AliquotaVigente> buscarVigentesEm(Tributo tributo, LocalDate data) {
                        return aliquotas.stream()
                                .filter(registro -> registro.tributo() == tributo && registro.vigenteEm(data))
                                .toList();
                    }
                });
    }
}
