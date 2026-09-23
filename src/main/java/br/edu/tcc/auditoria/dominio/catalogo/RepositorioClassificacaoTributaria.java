package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;

import java.time.LocalDate;
import java.util.Optional;

// Interface responsável por buscar as classificações tributárias do catálogo numa data; aqui a data é parâmetro, mas as regras consultam pelo ContextoNormativo.
public interface RepositorioClassificacaoTributaria {

    // Retorna a versão do código que valia na data; vazio quer dizer que o catálogo nada diz, e a regra deve tratar como NAO_AVALIADO.
    Optional<ClassificacaoTributaria> buscarVigenteEm(CodigoClassificacaoTributaria codigo, LocalDate data);
}
