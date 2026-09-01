package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.time.LocalDate;
import java.util.Optional;

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
        LocalDate inicio = linha.dataObrigatoria(COLUNA_VIGENCIA_INICIO);
        Optional<LocalDate> fim = linha.data(COLUNA_VIGENCIA_FIM);
        String fonteNormativa = linha.textoObrigatorio(COLUNA_FONTE_NORMATIVA);

        return linha.converterCom(() ->
                new ProcedenciaNormativa(new PeriodoVigencia(inicio, fim), fonteNormativa));
    }
}
