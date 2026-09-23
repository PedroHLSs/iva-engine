package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;

// Classe que lê as três colunas que todo CSV de catálogo tem: vigenciaInicio, vigenciaFim e fonteNormativa. Fica num lugar só para nenhum importador esquecer delas; vigenciaFim em branco não é erro, quer dizer que ainda está valendo.
final class ProcedenciaEmCsv {

    static final String COLUNA_VIGENCIA_INICIO = "vigenciaInicio";
    static final String COLUNA_VIGENCIA_FIM = "vigenciaFim";
    static final String COLUNA_FONTE_NORMATIVA = "fonteNormativa";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private ProcedenciaEmCsv() {
    }

    // Método estático que lê a vigência e a fonte da coluna fonteNormativa.
    static ProcedenciaNormativa ler(LinhaCsv linha) {
        return ler(linha, lida -> lida.textoObrigatorio(COLUNA_FONTE_NORMATIVA));
    }

    // Método estático que lê a vigência e usa a leitura da fonte que o importador passar. Existe desde 14/09/2026, porque a classificação pode trazer a fonte separada por tributo.
    static ProcedenciaNormativa ler(LinhaCsv linha, Function<LinhaCsv, String> leituraDaFonte) {
        LocalDate inicio = linha.dataObrigatoria(COLUNA_VIGENCIA_INICIO);
        Optional<LocalDate> fim = linha.data(COLUNA_VIGENCIA_FIM);
        String fonteNormativa = leituraDaFonte.apply(linha);

        return linha.converterCom(() ->
                new ProcedenciaNormativa(new PeriodoVigencia(inicio, fim), fonteNormativa));
    }
}
