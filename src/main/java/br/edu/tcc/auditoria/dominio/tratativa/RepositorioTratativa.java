package br.edu.tcc.auditoria.dominio.tratativa;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// Repositório utilizado para buscar e gravar as tratativas; a busca usa sempre a chave completa, com a versão da regra.
public interface RepositorioTratativa {

    // Busca a tratativa da chave, se houver.
    Optional<Tratativa> buscar(ChaveDeTratativa chave);

    // Busca de uma vez as tratativas de várias chaves, para não fazer uma consulta por apontamento; chave sem tratativa não aparece no resultado.
    Map<ChaveDeTratativa, Tratativa> buscarTodas(Collection<ChaveDeTratativa> chaves);

    // Grava a tratativa, trocando a que já existir para a mesma chave: vale a última decisão.
    void salvar(Tratativa tratativa);
}
