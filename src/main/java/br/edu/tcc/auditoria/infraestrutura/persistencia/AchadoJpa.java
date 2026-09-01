package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Severidade;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** Acesso aos apontamentos gravados. */
interface AchadoJpa extends JpaRepository<AchadoEntidade, UUID> {

    /** Busca pela identidade do apontamento, que é a mesma chave da tratativa. */
    Optional<AchadoEntidade> findByHashItemAndRegraIdAndRegraVersao(
            String hashItem, String regraId, String regraVersao);

    /**
     * Apontamentos que atendem ao filtro, do mais grave para o menos grave.
     *
     * <p>A ordenação não pode sair do texto da severidade: em ordem alfabética
     * "INFORMATIVA" viria antes de "MODERADA", e o relatório abriria pela
     * observação em vez da incoerência. O {@code case} abaixo recebe as
     * constantes por parâmetro e devolve a ordem de gravidade declarada no enum.
     * Dentro da mesma severidade a ordem é documento, item e regra, para que duas
     * consultas iguais produzam a mesma listagem.</p>
     *
     * <p>{@code apenasAbertos} usa {@code not exists} contra a tratativa em vez de
     * junção: não há chave estrangeira entre as duas tabelas, e é deliberado —
     * ver {@link TratativaEntidade}. Note que a comparação inclui
     * {@code regraVersao}: apontamento cuja regra mudou de versão conta como
     * aberto, mesmo havendo tratativa na versão anterior.</p>
     */
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

    /** Quantos apontamentos atendem ao filtro, ignorando o limite da listagem. */
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
