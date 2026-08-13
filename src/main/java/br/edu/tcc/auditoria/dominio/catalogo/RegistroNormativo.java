package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Contrato comum a todo registro do catálogo normativo.
 *
 * <p>Duas obrigações, e ambas existem para que o catálogo possa ser resolvido no
 * tempo sem que cada tipo de registro reinvente como fazê-lo:</p>
 *
 * <ul>
 *   <li>{@link #procedencia()} — vigência e fonte, reunidas em
 *       {@link ProcedenciaNormativa};</li>
 *   <li>{@link #chaveDeVigencia()} — o que identifica a <em>série</em> a que o
 *       registro pertence. Duas versões com a mesma chave são o mesmo assunto em
 *       épocas diferentes, e por isso não podem ter vigências sobrepostas.</li>
 * </ul>
 *
 * <p>A chave nem sempre é um único campo: um item de anexo é identificado pelo
 * par NCM e anexo, uma alíquota pelo par tributo e abrangência. Deixar cada
 * registro montar a própria chave evita que o catálogo afirme, por acidente de
 * modelagem, que um NCM só pode pertencer a um anexo de cada vez — isso seria
 * afirmação sobre a norma, e o código não a faz.</p>
 */
public interface RegistroNormativo {

    /** Vigência e fonte normativa do registro. */
    ProcedenciaNormativa procedencia();

    /**
     * Identificação da série temporal a que este registro pertence.
     *
     * <p>Registros com a mesma chave são versões sucessivas do mesmo assunto.</p>
     */
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
