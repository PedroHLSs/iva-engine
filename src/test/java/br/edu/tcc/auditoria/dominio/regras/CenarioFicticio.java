package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Uf;
import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Documentos e registros de catálogo para os testes de regra.
 *
 * <p><strong>Todos os valores são deliberadamente fictícios e nenhum deles deve
 * ser lido como afirmação sobre a legislação.</strong> Código "XXX001", CST
 * "AAA", NCM zerado, anexo "ANEXO-XX", percentual 99,99, datas em 1900: nada
 * disso existe na norma, e é exatamente por isso que está aqui. As constantes de
 * catálogo são reaproveitadas de {@link CatalogoFicticio}, para que exista um
 * único lugar onde os valores falsos são definidos.</p>
 */
public final class CenarioFicticio {

    /** Chaves fictícias, escolhidas para ter ordem entre si nos testes de determinismo. */
    public static final String CHAVE_PRIMEIRA = "1".repeat(44);
    public static final String CHAVE_SEGUNDA = "2".repeat(44);

    public static final String PSEUDONIMO = "a".repeat(64);

    public static final String CODIGO = CatalogoFicticio.CODIGO;
    public static final String CODIGO_ALTERNATIVO = CatalogoFicticio.CODIGO_ALTERNATIVO;
    public static final String NCM = CatalogoFicticio.NCM;
    public static final String NCM_ALTERNATIVO = CatalogoFicticio.NCM_ALTERNATIVO;
    public static final String CST = CatalogoFicticio.CST;
    public static final String CST_ALTERNATIVO = "BBB";
    public static final String ANEXO = CatalogoFicticio.ANEXO;
    public static final String ANEXO_ALTERNATIVO = CatalogoFicticio.ANEXO_ALTERNATIVO;
    public static final String ABRANGENCIA = CatalogoFicticio.ABRANGENCIA;
    public static final String ABRANGENCIA_ALTERNATIVA = "ABRANGENCIA-YY";

    /** Data de emissão usada nos testes; cai dentro de {@link #coberturaTotal()}. */
    public static final LocalDate DATA_EMISSAO = CatalogoFicticio.INICIO;

    private CenarioFicticio() {
    }

    /** Documento fictício emitido em {@link #DATA_EMISSAO}. */
    public static Documento documento() {
        return documento(CHAVE_PRIMEIRA, DATA_EMISSAO);
    }

    public static Documento documento(String chaveAcesso, LocalDate dataEmissao) {
        return new Documento(
                new ChaveAcesso(chaveAcesso),
                "99",
                "999",
                "999999",
                dataEmissao,
                Uf.SP,
                Optional.of(Uf.MG),
                Optional.of("9"),
                Optional.of("9"),
                new IdentificadorPseudonimizado(PSEUDONIMO),
                Optional.empty());
    }

    /** Cobertura que alcança {@link #DATA_EMISSAO}: o catálogo carregado responde pela data. */
    public static CoberturaDoCatalogo coberturaTotal() {
        ProcedenciaNormativa procedencia =
                CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM);
        return new CoberturaDoCatalogo(procedencia, procedencia, procedencia);
    }

    /**
     * Cobertura que não alcança {@link #DATA_EMISSAO}.
     *
     * <p>Representa a carga que não vale para a data do documento — nenhuma
     * tabela foi carregada para aquele período, ou foi carregada para outro.</p>
     */
    public static CoberturaDoCatalogo coberturaForaDaData() {
        ProcedenciaNormativa procedencia = CatalogoFicticio.procedencia(
                CatalogoFicticio.INICIO_SEGUINTE, CatalogoFicticio.INICIO_SEGUINTE.plusDays(1));
        return new CoberturaDoCatalogo(procedencia, procedencia, procedencia);
    }

    /** Vigência fictícia usada por todos os registros deste cenário. */
    public static ProcedenciaNormativa procedencia() {
        return CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM);
    }

    /** Classificação sem benefício, sem redução e sem campo condicionado. */
    public static ClassificacaoTributaria classificacao(String codigo, String... cstsCompativeis) {
        return classificacao(codigo, false, Optional.empty(), List.of(), cstsCompativeis);
    }

    /** Classificação marcada como benefício pelo catálogo. */
    public static ClassificacaoTributaria classificacaoComBeneficio(String codigo, String... cstsCompativeis) {
        return classificacao(codigo, true, Optional.empty(), List.of(), cstsCompativeis);
    }

    /** Classificação que condiciona os campos informados. */
    public static ClassificacaoTributaria classificacaoExigindo(String codigo, List<String> camposExigidos) {
        return classificacao(codigo, false, Optional.empty(), camposExigidos, CST);
    }

    public static ClassificacaoTributaria classificacao(
            String codigo,
            boolean indicadorDeBeneficio,
            Optional<BigDecimal> percentualReducao,
            List<String> camposObrigatoriosCondicionados,
            String... cstsCompativeis) {

        Set<CodigoCst> csts = new LinkedHashSet<>();
        Arrays.stream(cstsCompativeis).map(CodigoCst::new).forEach(csts::add);

        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(codigo),
                csts,
                CatalogoFicticio.DISPOSITIVO,
                indicadorDeBeneficio,
                percentualReducao,
                camposObrigatoriosCondicionados,
                procedencia());
    }

    public static RegistroNcm registroNcm(String ncm) {
        return new RegistroNcm(new Ncm(ncm), CatalogoFicticio.DESCRICAO, procedencia());
    }

    public static ItemAnexo itemAnexo(String ncm, String anexo) {
        return new ItemAnexo(
                new Ncm(ncm), new IdentificadorAnexo(anexo), CatalogoFicticio.TRATAMENTO, procedencia());
    }

    public static AliquotaVigente aliquota(Tributo tributo, String percentual) {
        return aliquota(tributo, percentual, ABRANGENCIA);
    }

    public static AliquotaVigente aliquota(Tributo tributo, String percentual, String abrangencia) {
        return new AliquotaVigente(
                tributo, new BigDecimal(percentual), new Abrangencia(abrangencia), procedencia());
    }
}
