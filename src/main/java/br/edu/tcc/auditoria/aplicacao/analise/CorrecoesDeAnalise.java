package br.edu.tcc.auditoria.aplicacao.analise;

import java.time.Instant;
import java.util.UUID;

// Interface responsável por registrar que uma análise nova corrige uma anterior. A anterior não muda: continua com o próprio hash de entrada, a própria carga e os próprios apontamentos. Acrescentada na Etapa 12.
public interface CorrecoesDeAnalise {

    // Diz se existe análise com o identificador.
    boolean existeAnalise(UUID id);

    // Registra que a análise nova corrige a anterior, com quem pediu e quando.
    void registrar(UUID nova, UUID anterior, UUID autor, Instant quando);

    // Devolve os vínculos de correção da análise.
    VinculosDaAnalise vinculos(UUID analise);
}
