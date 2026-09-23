package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.RepositorioTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Repositório que grava e lê as tratativas no banco, sempre com a versão da regra na busca: se a regra mudou de versão, a busca não acha nada e o apontamento reabre.
@Repository
class RepositorioTratativaNoBanco implements RepositorioTratativa {

    private final TratativaJpa tratativas;

    // Construtor que recebe o repositório JPA de tratativas.
    RepositorioTratativaNoBanco(TratativaJpa tratativas) {
        this.tratativas = tratativas;
    }

    // Busca a tratativa da chave: hash do item, regra e versão.
    @Override
    @Transactional(readOnly = true)
    public Optional<Tratativa> buscar(ChaveDeTratativa chave) {
        return tratativas.findByHashItemAndRegraIdAndRegraVersao(
                        chave.hashDoItem().valor(), chave.regraId(), chave.regraVersao())
                .map(MapeadorDeTratativa::paraDominio);
    }

    // Busca as tratativas de várias chaves de uma vez.
    @Override
    @Transactional(readOnly = true)
    public Map<ChaveDeTratativa, Tratativa> buscarTodas(Collection<ChaveDeTratativa> chaves) {
        if (chaves == null || chaves.isEmpty()) {
            return Map.of();
        }
        Set<ChaveDeTratativa> procuradas = new LinkedHashSet<>(chaves);
        Set<String> hashes = new LinkedHashSet<>();
        procuradas.forEach(chave -> hashes.add(chave.hashDoItem().valor()));

        Map<ChaveDeTratativa, Tratativa> encontradas = new LinkedHashMap<>();
        for (TratativaEntidade entidade : tratativas.findByHashItemIn(hashes)) {
            Tratativa tratativa = MapeadorDeTratativa.paraDominio(entidade);
            // A consulta filtra só pelo hash do item; a versão da regra é conferida aqui, para não devolver tratativa de outra versão.
            if (procuradas.contains(tratativa.chave())) {
                encontradas.put(tratativa.chave(), tratativa);
            }
        }
        return Map.copyOf(encontradas);
    }

    // Grava a decisão, atualizando a tratativa da mesma chave ou criando uma nova.
    @Override
    @Transactional
    public void salvar(Tratativa tratativa) {
        ChaveDeTratativa chave = tratativa.chave();
        TratativaEntidade entidade = tratativas
                .findByHashItemAndRegraIdAndRegraVersao(
                        chave.hashDoItem().valor(), chave.regraId(), chave.regraVersao())
                .orElseGet(() -> new TratativaEntidade(
                        UUID.randomUUID(),
                        chave.hashDoItem().valor(),
                        chave.regraId(),
                        chave.regraVersao()));

        entidade.decidir(tratativa.decisao(), tratativa.justificativa(), tratativa.registradoEm());
        tratativas.save(entidade);
    }
}
