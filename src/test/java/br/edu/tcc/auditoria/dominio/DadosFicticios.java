package br.edu.tcc.auditoria.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Instâncias válidas para uso nos testes.
 *
 * <p><strong>Todos os valores aqui são deliberadamente fictícios e nenhum deles
 * deve ser lido como afirmação sobre a legislação.</strong> Chaves de nove,
 * NCM zerado, CFOP 9999, CST "AAA", modelo "99", datas em 1900: nada disso
 * existe na norma, e é exatamente por isso que está aqui. Se algum dia um dado
 * de teste deste arquivo puder ser confundido com a regra real, ele está
 * errado.</p>
 */
final class DadosFicticios {

    static final String CHAVE = "9".repeat(44);
    static final String PSEUDONIMO_EMITENTE = "a".repeat(64);
    static final String PSEUDONIMO_DESTINATARIO = "b".repeat(64);

    static final String NCM = "00000000";
    static final String CFOP = "9999";
    static final String CST = "AAA";
    static final String CLASSIFICACAO_TRIBUTARIA = "999999";

    static final String MODELO = "99";
    static final String SERIE = "999";
    static final String NUMERO = "999999";
    static final String CRT = "9";
    static final String INDICADOR_DESTINATARIO = "9";

    static final LocalDate DATA_EMISSAO = LocalDate.of(1900, 1, 1);
    static final LocalDate INICIO_VIGENCIA = LocalDate.of(1900, 1, 1);
    static final LocalDate FIM_VIGENCIA = LocalDate.of(1900, 12, 31);

    static final String REGRA_ID = "REGRA-FICTICIA-000";
    static final String REGRA_VERSAO = "0.0.0-ficticia";
    static final String FUNDAMENTO = "FUNDAMENTO FICTICIO PARA TESTE";

    private DadosFicticios() {
    }

    static ChaveAcesso chave() {
        return new ChaveAcesso(CHAVE);
    }

    static IdentificadorPseudonimizado pseudonimoEmitente() {
        return new IdentificadorPseudonimizado(PSEUDONIMO_EMITENTE);
    }

    static IdentificadorPseudonimizado pseudonimoDestinatario() {
        return new IdentificadorPseudonimizado(PSEUDONIMO_DESTINATARIO);
    }

    static PeriodoVigencia vigencia() {
        return PeriodoVigencia.de(INICIO_VIGENCIA, FIM_VIGENCIA);
    }

    static Documento documento() {
        return new Documento(
                chave(),
                MODELO,
                SERIE,
                NUMERO,
                DATA_EMISSAO,
                Uf.SP,
                Optional.of(Uf.MG),
                Optional.of(CRT),
                Optional.of(INDICADOR_DESTINATARIO),
                pseudonimoEmitente(),
                Optional.of(pseudonimoDestinatario()));
    }

    /** Item sem nenhum campo do grupo de IBS/CBS informado. */
    static ItemDocumento itemSemCamposDeIbsCbs() {
        return new ItemDocumento(
                1,
                Optional.of(new Ncm(NCM)),
                Optional.of(new Cfop(CFOP)),
                new BigDecimal("99.99"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    static Evidencia evidencia() {
        return new Evidencia(
                "campoFicticio",
                Optional.of("99,99"),
                Optional.of("00,00"),
                new OrigemEvidencia.DaRegra("derivação fictícia para teste"));
    }

    static Achado achadoDeItem() {
        return new Achado(
                REGRA_ID,
                REGRA_VERSAO,
                Severidade.GRAVE,
                chave(),
                java.util.OptionalInt.of(1),
                List.of(evidencia()),
                FUNDAMENTO,
                vigencia(),
                ValorEmRisco.calculado(new BigDecimal("99.99")));
    }
}
