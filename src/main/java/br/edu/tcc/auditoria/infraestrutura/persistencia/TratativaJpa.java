package br.edu.tcc.auditoria.infraestrutura.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso às tratativas registradas, sempre pela chave de conteúdo. */
interface TratativaJpa extends JpaRepository<TratativaEntidade, UUID> {

    Optional<TratativaEntidade> findByHashItemAndRegraIdAndRegraVersao(
            String hashItem, String regraId, String regraVersao);

    List<TratativaEntidade> findByHashItemIn(Collection<String> hashesDeItem);
}
