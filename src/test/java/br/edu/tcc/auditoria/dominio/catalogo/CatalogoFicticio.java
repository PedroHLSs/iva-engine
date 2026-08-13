package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Registros de catálogo para uso nos testes.
 *
 * <p><strong>Todos os valores aqui são deliberadamente fictícios e nenhum deles
 * deve ser lido como afirmação sobre a legislação.</strong> Códigos "XXX", NCM
 * zerado, percentual 99,99, anos 1900: nada disso existe na norma, e é
 * exatamente por isso que está aqui.</p>
 */
public final class CatalogoFicticio {

    public static final String CODIGO = "XXX001";
    public static final String CODIGO_ALTERNATIVO = "XXX002";
    public static final String NCM = "00000000";
    public static final String NCM_ALTERNATIVO = "99999999";
    public static final String CST = "AAA";
    public static final String ANEXO = "ANEXO-XX";
    public static final String ANEXO_ALTERNATIVO = "ANEXO-YY";
    public static final String TRATAMENTO = "TRATAMENTO-XX";
    public static final String ABRANGENCIA = "ABRANGENCIA-XX";
    public static final String DISPOSITIVO = "Dispositivo ficticio para teste";
    public static final String DESCRICAO = "Descricao ficticia de teste";
    public static final String FONTE = "FONTE FICTICIA v0.0";

    /** Primeiro dia do primeiro período fictício. */
    public static final LocalDate INICIO = LocalDate.of(1900, 1, 1);
    /** Último dia do primeiro período fictício. */
    public static final LocalDate FIM = LocalDate.of(1900, 6, 30);
    /** Primeiro dia do segundo período fictício, imediatamente após {@link #FIM}. */
    public static final LocalDate INICIO_SEGUINTE = LocalDate.of(1900, 7, 1);

    private CatalogoFicticio() {
    }

    public static ProcedenciaNormativa procedencia(LocalDate inicio, LocalDate fim) {
        return ProcedenciaNormativa.de(inicio, fim, FONTE);
    }

    public static ProcedenciaNormativa procedenciaAberta(LocalDate inicio) {
        return ProcedenciaNormativa.aPartirDe(inicio, FONTE);
    }

    public static ClassificacaoTributaria classificacao(String codigo, ProcedenciaNormativa procedencia) {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(codigo),
                Set.of(new CodigoCst(CST)),
                DISPOSITIVO,
                false,
                Optional.empty(),
                List.of(),
                procedencia);
    }

    public static RegistroNcm registroNcm(String ncm, ProcedenciaNormativa procedencia) {
        return new RegistroNcm(new Ncm(ncm), DESCRICAO, procedencia);
    }

    public static ItemAnexo itemAnexo(String ncm, String anexo, ProcedenciaNormativa procedencia) {
        return new ItemAnexo(new Ncm(ncm), new IdentificadorAnexo(anexo), TRATAMENTO, procedencia);
    }

    public static AliquotaVigente aliquota(
            Tributo tributo, String abrangencia, String percentual, ProcedenciaNormativa procedencia) {
        return new AliquotaVigente(
                tributo, new BigDecimal(percentual), new Abrangencia(abrangencia), procedencia);
    }
}
