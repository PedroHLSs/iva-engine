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

/**
 * Grava e lê tratativas, sempre pela chave de conteúdo.
 *
 * <p>Não há consulta que ignore a versão da regra. Isso é o que garante que uma
 * decisão dada contra um critério não silencie um apontamento produzido por
 * critério diferente: mudou a versão, a busca não encontra nada, e o apontamento
 * reabre.</p>
 */
@Repository
class RepositorioTratativaNoBanco implements RepositorioTratativa {

    private final TratativaJpa tratativas;

    RepositorioTratativaNoBanco(TratativaJpa tratativas) {
        this.tratativas = tratativas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tratativa> buscar(ChaveDeTratativa chave) {
        return tratativas.findByHashItemAndRegraIdAndRegraVersao(
                        chave.hashDoItem().valor(), chave.regraId(), chave.regraVersao())
                .map(MapeadorDeTratativa::paraDominio);
    }

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
            // A consulta filtra pelo resumo do item; a versão da regra é conferida
            // aqui, e é ela que faz a tratativa de outra versão não ser devolvida.
            if (procuradas.contains(tratativa.chave())) {
                encontradas.put(tratativa.chave(), tratativa);
            }
        }
        return Map.copyOf(encontradas);
    }

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
