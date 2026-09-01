package br.edu.tcc.auditoria.aplicacao.acuracia;

import java.nio.file.Path;

/**
 * Porta de leitura do gabarito rotulado à mão.
 *
 * <p>A aplicação pede o gabarito de um caminho e recebe afirmações já
 * validadas. Formato de arquivo, codificação e separador são problema de quem
 * implementa esta interface: o harness mede acurácia, não interpreta CSV.</p>
 */
public interface FonteDeGabarito {

    /** Lê o gabarito do arquivo indicado, recusando o que estiver malformado. */
    Gabarito carregar(Path arquivo);
}
