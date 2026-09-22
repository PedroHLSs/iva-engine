package br.edu.tcc.auditoria.aplicacao.acuracia;

import java.nio.file.Path;

public interface FonteDeGabarito {
    //Verifica a extensão do arquivo e carrega o gabarito correspondente.
    Gabarito carregar(Path arquivo);
}
