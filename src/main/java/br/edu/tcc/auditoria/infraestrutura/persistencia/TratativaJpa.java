package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Repositório utilizado para acessar as tratativas, sempre pela chave de conteúdo.
interface TratativaJpa extends JpaRepository<TratativaEntidade, UUID> {

    // Busca a tratativa pelo hash do item, pela regra e pela versão da regra.
    Optional<TratativaEntidade> findByHashItemAndRegraIdAndRegraVersao(
            String hashItem, String regraId, String regraVersao);

    // Busca as tratativas de vários itens de uma vez.
    List<TratativaEntidade> findByHashItemIn(Collection<String> hashesDeItem);
}
