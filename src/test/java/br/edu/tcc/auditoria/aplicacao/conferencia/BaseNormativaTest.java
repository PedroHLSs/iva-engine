package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.auditoria.CatalogoParaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioAliquota;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O tratamento identificado, resolvido na carga daquela análise.
 *
 * <p>Todo dado aqui é fictício e assim declarado: NCM de oito zeros, anexo e
 * tratamento em caixa alta dizendo que são fictícios, vigência em 1900,
 * percentuais de dígitos repetidos. Nada pode ser lido como afirmação sobre a
 * legislação.</p>
 */
@DisplayName("Base normativa: IBS e CBS separados, e ausência sempre explicada")
class BaseNormativaTest {

    private static final String VERSAO = "carga-ficticia-de-teste";
    private static final String FONTE = "FONTE FICTICIA PARA TESTE";
    private static final LocalDate NA_VIGENCIA = LocalDate.of(1900, 6, 15);
    private static final LocalDate FORA_DA_VIGENCIA = LocalDate.of(1901, 6, 15);
    private static final String NCM = "00000000";
    private static final String CLASSTRIB = "FICT-001";

    private static final BigDecimal IBS_UF = new BigDecimal("99.99");
    private static final BigDecimal IBS_MUN = new BigDecimal("11.11");
    private static final BigDecimal SOMA_PROIBIDA = IBS_UF.add(IBS_MUN);

    @Test
    void deveApresentarOsTresTributosSempreEIbsSeparadoDeCbs() {
        TratamentoIdentificado tratamento = tratamentoDe(itemCompleto(), NA_VIGENCIA);

        assertThat(tratamento.porTributo())
                .extracting(TratamentoDeTributo::tributo)
                .describedAs("tributo omitido é lido como tributo que não incide")
                .containsExactly(Tributo.IBS_UF, Tributo.IBS_MUN, Tributo.CBS);

        assertThat(tratamento.porTributo())
                .extracting(TratamentoDeTributo::familia)
                .containsExactly("IBS", "IBS", "CBS");
    }

    @Test
    void naoDeveSomarAsParcelasDoIbs() {
        TratamentoIdentificado tratamento = tratamentoDe(itemCompleto(), NA_VIGENCIA);

        List<BigDecimal> percentuais = new ArrayList<>();
        for (TratamentoDeTributo doTributo : tratamento.porTributo()) {
            doTributo.aliquotas().encontrado()
                    .forEach(aliquota -> percentuais.add(aliquota.percentual()));
        }

        assertThat(percentuais)
                .describedAs("as duas parcelas aparecem inteiras, cada uma com a fonte dela")
                .contains(IBS_UF, IBS_MUN);
        assertThat(percentuais)
                .describedAs("somar produziria um percentual que nenhuma linha da carga declara")
                .doesNotContain(SOMA_PROIBIDA);
    }

    @Test
    void deveDizerPorQueNaoHaAliquotaDoTributoQueACargaNaoAlcanca() {
        TratamentoIdentificado tratamento = tratamentoDe(itemCompleto(), NA_VIGENCIA);

        TratamentoDeTributo cbs = tratamento.porTributo().stream()
                .filter(doTributo -> doTributo.tributo() == Tributo.CBS)
                .findFirst()
                .orElseThrow();

        assertThat(cbs.aliquotas().respondido()).isFalse();
        assertThat(cbs.aliquotas().motivoDaAusencia().orElseThrow())
                .contains(VERSAO)
                .contains(NA_VIGENCIA.toString());
    }

    @Test
    void deveResolverNaDataDoDocumentoENaoEmOutra() {
        TratamentoIdentificado naVigencia = tratamentoDe(itemCompleto(), NA_VIGENCIA);
        TratamentoIdentificado fora = tratamentoDe(itemCompleto(), FORA_DA_VIGENCIA);

        assertThat(naVigencia.descricaoDoNcm().respondido()).isTrue();
        assertThat(fora.descricaoDoNcm().respondido())
                .describedAs("o registro fictício vale só em 1900")
                .isFalse();
        assertThat(fora.descricaoDoNcm().motivoDaAusencia().orElseThrow())
                .contains(FORA_DA_VIGENCIA.toString());
        assertThat(fora.dataDeReferencia()).isEqualTo(FORA_DA_VIGENCIA);
    }

    @Test
    void deveDizerQueOItemNaoDeclarouNcmEmVezDeDizerQueACargaNaoTem() {
        ItemDocumento semNcm = ConferenciaFicticia.item(1, null, CLASSTRIB, "10.00");

        TratamentoIdentificado tratamento = tratamentoDe(semNcm, NA_VIGENCIA);

        assertThat(tratamento.descricaoDoNcm().motivoDaAusencia().orElseThrow())
                .describedAs("a culpa é do documento, não da carga, e a frase precisa distinguir")
                .contains("não declarou NCM");
        assertThat(tratamento.enquadramentos().motivoDaAusencia().orElseThrow())
                .contains("não declarou NCM");
        assertThat(tratamento.classificacao().respondido())
                .describedAs("o cClassTrib veio, então esse bloco responde")
                .isTrue();
    }

    @Test
    void deveDizerQueOItemNaoDeclarouClassTrib() {
        ItemDocumento semClassTrib = ConferenciaFicticia.item(1, NCM, null, "10.00");

        TratamentoIdentificado tratamento = tratamentoDe(semClassTrib, NA_VIGENCIA);

        assertThat(tratamento.classificacao().motivoDaAusencia().orElseThrow())
                .contains("não declarou cClassTrib");
        assertThat(tratamento.descricaoDoNcm().respondido()).isTrue();
    }

    @Test
    void deveResponderComMotivoEmTodosOsBlocosQuandoACargaNaoEstaMaisGravada() {
        BaseNormativa base = BaseNormativa.daVersao(versao -> Optional.empty(), VERSAO);

        TratamentoIdentificado tratamento = base.tratamentoDe(NA_VIGENCIA, itemCompleto());

        assertThat(base.cargaDisponivel()).isFalse();
        assertThat(tratamento.algoFoiDeterminado()).isFalse();
        assertThat(tratamento.porTributo())
                .describedAs("a forma da resposta é a mesma, para a tela não ter caso especial")
                .hasSize(Tributo.values().length);
        assertThat(tratamento.descricaoDoNcm().motivoDaAusencia().orElseThrow())
                .contains("não está mais gravada")
                .contains("Importar uma carga nova não recompõe esta");
    }

    @Test
    void deveDizerQueNenhumCatalogoFoiImportadoQuandoENaoENenhum() {
        BaseNormativa base = BaseNormativa.nenhumaCargaImportada();

        assertThat(base.cargaDisponivel()).isFalse();
        assertThat(base.cobertura()).isEmpty();
        assertThat(base.aliquotasEm(NA_VIGENCIA))
                .allSatisfy(doTributo -> assertThat(
                        doTributo.aliquotas().motivoDaAusencia().orElseThrow())
                        .contains("nenhum catálogo foi importado ainda"));
    }

    @Test
    void deveRecusarTratamentoQueOmitaUmTributo() {
        assertThatThrownBy(() -> new TratamentoIdentificado(
                VERSAO,
                NA_VIGENCIA,
                LeituraDoCatalogo.ausente("motivo fictício"),
                LeituraDoCatalogo.ausente("motivo fictício"),
                LeituraDoCatalogo.ausente("motivo fictício"),
                List.of(TratamentoDeTributo.de(
                        Tributo.CBS, LeituraDoCatalogo.ausente("motivo fictício")))))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("Tributo omitido é lido como tributo que não incide");
    }

    @Test
    void deveTrazerADescricaoDoNcmParaFicarAoLadoDaDescricaoDaNota() {
        TratamentoIdentificado tratamento = tratamentoDe(itemCompleto(), NA_VIGENCIA);

        DescricaoDeNcm descricao = tratamento.descricaoDoNcm().encontrado().get(0);
        assertThat(descricao.ncm()).isEqualTo(NCM);
        assertThat(descricao.descricao()).isEqualTo("DESCRICAO FICTICIA DO NCM DE TESTE");
        assertThat(descricao.referencia().fonteNormativa()).isEqualTo(FONTE);
        assertThat(descricao.referencia().vigenciaAberta()).isFalse();
    }

    @Test
    void deveTrazerODispositivoEOsCstsAdmitidosDaCarga() {
        TratamentoIdentificado tratamento = tratamentoDe(itemCompleto(), NA_VIGENCIA);

        ClassificacaoDoCatalogo classificacao = tratamento.classificacao().encontrado().get(0);
        assertThat(classificacao.dispositivoLegal()).isEqualTo("DISPOSITIVO FICTICIO DE TESTE");
        assertThat(classificacao.cstsAdmitidos())
                .describedAs("ordem estável, ou duas aberturas da mesma nota listariam diferente")
                .containsExactly("000", "999");
        assertThat(classificacao.percentualReducao())
                .describedAs("a carga fictícia não declara redução, e isso não vira zero")
                .isEmpty();
    }

    @Test
    void deveConsultarPontualmenteNaDataPedidaParaATelaDaBaseTributaria() {
        BaseNormativa base = BaseNormativa.daVersao(provedor(), VERSAO);

        assertThat(base.ncmEm(NA_VIGENCIA, new Ncm(NCM)).respondido()).isTrue();
        assertThat(base.anexosEm(NA_VIGENCIA, new Ncm(NCM)).respondido()).isTrue();
        assertThat(base.classificacaoEm(
                NA_VIGENCIA, new CodigoClassificacaoTributaria(CLASSTRIB)).respondido()).isTrue();
        assertThat(base.ncmEm(FORA_DA_VIGENCIA, new Ncm(NCM)).respondido()).isFalse();
    }

    @Test
    void deveExigirDataEmTodaConsultaDaBaseTributaria() {
        BaseNormativa base = BaseNormativa.daVersao(provedor(), VERSAO);

        assertThatThrownBy(() -> base.aliquotasEm(null))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("exige data");
    }

    private static TratamentoIdentificado tratamentoDe(ItemDocumento item, LocalDate data) {
        return BaseNormativa.daVersao(provedor(), VERSAO).tratamentoDe(data, item);
    }

    private static ItemDocumento itemCompleto() {
        return ConferenciaFicticia.item(1, NCM, CLASSTRIB, "10.00");
    }

    private static ProvedorDeCatalogoPorVersao provedor() {
        return versao -> VERSAO.equals(versao) ? Optional.of(catalogo()) : Optional.empty();
    }

    /*
     * Catálogo fictício montado com repositórios de teste, e não com os de
     * infraestrutura: este é um teste de aplicação, e importar a infraestrutura
     * aqui inverteria a direção de dependência que o projeto inteiro sustenta.
     */
    private static CatalogoParaAuditoria catalogo() {
        ProcedenciaNormativa procedencia = ProcedenciaNormativa.de(
                LocalDate.of(1900, 1, 1), LocalDate.of(1900, 12, 31), FONTE);

        RegistroNcm registro = new RegistroNcm(
                new Ncm(NCM), "DESCRICAO FICTICIA DO NCM DE TESTE", procedencia);

        ItemAnexo anexo = new ItemAnexo(
                new Ncm(NCM),
                new IdentificadorAnexo("ANEXO-FICTICIO"),
                "TRATAMENTO FICTICIO DE TESTE",
                procedencia);

        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CLASSTRIB),
                java.util.Set.of(new CodigoCst("999"), new CodigoCst("000")),
                "DISPOSITIVO FICTICIO DE TESTE",
                false,
                Optional.empty(),
                List.of(),
                procedencia);

        List<AliquotaVigente> aliquotas = List.of(
                new AliquotaVigente(
                        Tributo.IBS_UF, IBS_UF, new Abrangencia("ZZ-FICTICIA"), procedencia),
                new AliquotaVigente(
                        Tributo.IBS_MUN, IBS_MUN, new Abrangencia("AA-FICTICIA"), procedencia));

        return new CatalogoParaAuditoria(
                VERSAO,
                new CoberturaDoCatalogo(procedencia, procedencia, procedencia),
                NaturezaDaCarga.deUmaSoProcedencia(
                        Natureza.FICTICIO,
                        List.of("uma classificacao ficticia"),
                        List.of("um NCM ficticio"),
                        List.of(),
                        List.of()),
                repositorioDeClassificacoes(classificacao),
                repositorioDeNcm(registro),
                repositorioDeAnexos(anexo),
                repositorioDeAliquotas(aliquotas));
    }

    private static RepositorioClassificacaoTributaria repositorioDeClassificacoes(
            ClassificacaoTributaria unica) {
        return (codigo, data) -> unica.codigo().equals(codigo) && unica.procedencia().vigenteEm(data)
                ? Optional.of(unica)
                : Optional.empty();
    }

    private static RepositorioNcm repositorioDeNcm(RegistroNcm unico) {
        return (ncm, data) -> unico.ncm().equals(ncm) && unico.procedencia().vigenteEm(data)
                ? Optional.of(unico)
                : Optional.empty();
    }

    private static RepositorioItemAnexo repositorioDeAnexos(ItemAnexo unico) {
        return (ncm, data) -> unico.ncm().equals(ncm) && unico.procedencia().vigenteEm(data)
                ? List.of(unico)
                : List.of();
    }

    private static RepositorioAliquota repositorioDeAliquotas(List<AliquotaVigente> todas) {
        return new RepositorioAliquota() {
            @Override
            public Optional<AliquotaVigente> buscarVigenteEm(
                    Tributo tributo, Abrangencia abrangencia, LocalDate data) {
                return buscarVigentesEm(tributo, data).stream()
                        .filter(aliquota -> aliquota.abrangencia().equals(abrangencia))
                        .findFirst();
            }

            @Override
            public List<AliquotaVigente> buscarVigentesEm(Tributo tributo, LocalDate data) {
                return todas.stream()
                        .filter(aliquota -> aliquota.tributo() == tributo)
                        .filter(aliquota -> aliquota.procedencia().vigenteEm(data))
                        .toList();
            }
        };
    }
}
