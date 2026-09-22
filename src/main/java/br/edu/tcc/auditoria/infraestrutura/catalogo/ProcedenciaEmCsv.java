package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;

/**
 * Leitura das três colunas que todo CSV de catálogo tem obrigatoriamente.
 *
 * <p>Fica num lugar só para que nenhum importador possa esquecer de exigi-las:
 * registro sem vigência não pode ser resolvido no tempo, e registro sem fonte
 * gera apontamento que ninguém consegue conferir. Os dois casos recusam a linha
 * inteira.</p>
 *
 * <p>{@code vigenciaFim} em branco não é erro — é vigência ainda aberta.</p>
 */
final class ProcedenciaEmCsv {

    static final String COLUNA_VIGENCIA_INICIO = "vigenciaInicio";
    static final String COLUNA_VIGENCIA_FIM = "vigenciaFim";
    static final String COLUNA_FONTE_NORMATIVA = "fonteNormativa";

    private ProcedenciaEmCsv() {
    }

    static ProcedenciaNormativa ler(LinhaCsv linha) {
        return ler(linha, lida -> lida.textoObrigatorio(COLUNA_FONTE_NORMATIVA));
    }

    /*
     * Emenda de 14/09/2026, sobre a Etapa 2.
     *
     * A fonte deixou de ser sempre a coluna fonteNormativa: o CSV de
     * classificação pode trazê-la por tributo, e quem sabe juntar as duas é o
     * importador dele. As vigências continuam lidas só aqui, e antes da fonte,
     * como eram — a primeira recusa de uma linha com os dois defeitos não mudou.
     */
    static ProcedenciaNormativa ler(LinhaCsv linha, Function<LinhaCsv, String> leituraDaFonte) {
        LocalDate inicio = linha.dataObrigatoria(COLUNA_VIGENCIA_INICIO);
        Optional<LocalDate> fim = linha.data(COLUNA_VIGENCIA_FIM);
        String fonteNormativa = leituraDaFonte.apply(linha);

        return linha.converterCom(() ->
                new ProcedenciaNormativa(new PeriodoVigencia(inicio, fim), fonteNormativa));
    }
}
