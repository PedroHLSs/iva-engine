package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;

import java.time.LocalDate;
import java.util.Optional;

// Interface comum a todo registro do catálogo: cada um informa a procedência e monta a chave da série a que pertence.
public interface RegistroNormativo {

    // Retorna a vigência e a fonte normativa do registro.
    ProcedenciaNormativa procedencia();

    // Retorna a chave da série temporal; registros com a mesma chave são versões sucessivas do mesmo assunto e não podem se sobrepor.
    String chaveDeVigencia();

    default PeriodoVigencia vigencia() {
        return procedencia().vigencia();
    }

    default LocalDate vigenciaInicio() {
        return procedencia().vigenciaInicio();
    }

    default Optional<LocalDate> vigenciaFim() {
        return procedencia().vigenciaFim();
    }

    default String fonteNormativa() {
        return procedencia().fonteNormativa();
    }

    default boolean vigenteEm(LocalDate data) {
        return procedencia().vigenteEm(data);
    }
}
