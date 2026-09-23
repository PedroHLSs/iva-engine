package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Severidade;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

// Repositório utilizado para acessar os apontamentos gravados.
interface AchadoJpa extends JpaRepository<AchadoEntidade, UUID> {

    // Busca pela identidade do apontamento, que é a mesma chave da tratativa.
    Optional<AchadoEntidade> findByHashItemAndRegraIdAndRegraVersao(
            String hashItem, String regraId, String regraVersao);

    // Busca os apontamentos do filtro, do mais grave para o menos grave e depois por documento, item e regra. A gravidade é ordenada pelo case, e não pelo texto, e apenasAbertos considera a versão da regra: se ela mudou, o apontamento conta como aberto.
    @Query("""
            select a from AchadoEntidade a
            where (:severidade is null or a.severidade = :severidade)
              and (:regraId is null or a.regraId = :regraId)
              and (:chaveAcesso is null or a.chaveAcesso = :chaveAcesso)
              and (:apenasAbertos = false or not exists (
                    select t.id from TratativaEntidade t
                    where t.hashItem = a.hashItem
                      and t.regraId = a.regraId
                      and t.regraVersao = a.regraVersao))
            order by
              case
                when a.severidade = :critica then 0
                when a.severidade = :grave then 1
                when a.severidade = :moderada then 2
                else 3
              end,
              a.chaveAcesso, a.numeroItem, a.regraId
            """)
    List<AchadoEntidade> filtrar(
            @Param("severidade") Severidade severidade,
            @Param("regraId") String regraId,
            @Param("chaveAcesso") String chaveAcesso,
            @Param("apenasAbertos") boolean apenasAbertos,
            @Param("critica") Severidade critica,
            @Param("grave") Severidade grave,
            @Param("moderada") Severidade moderada,
            Pageable pagina);

    // Conta os apontamentos do filtro, sem o limite da listagem.
    @Query("""
            select count(a) from AchadoEntidade a
            where (:severidade is null or a.severidade = :severidade)
              and (:regraId is null or a.regraId = :regraId)
              and (:chaveAcesso is null or a.chaveAcesso = :chaveAcesso)
              and (:apenasAbertos = false or not exists (
                    select t.id from TratativaEntidade t
                    where t.hashItem = a.hashItem
                      and t.regraId = a.regraId
                      and t.regraVersao = a.regraVersao))
            """)
    long contar(
            @Param("severidade") Severidade severidade,
            @Param("regraId") String regraId,
            @Param("chaveAcesso") String chaveAcesso,
            @Param("apenasAbertos") boolean apenasAbertos);
}
