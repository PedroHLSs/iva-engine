package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Classe que lista do banco os apontamentos gravados, já com a tratativa de cada um, para ninguém reexaminar o que já foi tratado. As tratativas são buscadas de uma vez e casadas pela chave completa; tratativa de outra versão da regra não casa, e o apontamento aparece aberto.
@Component
class ConsultaDeAchadosNoBanco implements ConsultaDeAchados {

    private final AchadoJpa achados;
    private final TratativaJpa tratativas;

    // Construtor que recebe os repositórios de apontamentos e de tratativas.
    ConsultaDeAchadosNoBanco(AchadoJpa achados, TratativaJpa tratativas) {
        this.achados = achados;
        this.tratativas = tratativas;
    }

    // Lista os apontamentos do filtro, do mais grave para o menos grave, até o limite.
    @Override
    @Transactional(readOnly = true)
    public List<AchadoRegistrado> listar(FiltroDeAchados filtro) {
        List<AchadoEntidade> encontrados = achados.filtrar(
                filtro.severidade().orElse(null),
                filtro.regraId().orElse(null),
                filtro.chaveAcesso().map(ChaveAcesso::valor).orElse(null),
                filtro.apenasAbertos(),
                Severidade.CRITICA,
                Severidade.GRAVE,
                Severidade.MODERADA,
                PageRequest.of(0, filtro.limite()));

        Map<ChaveDeTratativa, Tratativa> tratativasAplicaveis = buscarTratativas(encontrados);
        return encontrados.stream()
                .map(entidade -> montar(entidade, tratativasAplicaveis))
                .toList();
    }

    // Busca um apontamento pelo identificador, com a tratativa.
    @Override
    @Transactional(readOnly = true)
    public Optional<AchadoRegistrado> porId(UUID id) {
        return achados.findById(id)
                .map(entidade -> montar(entidade, buscarTratativas(List.of(entidade))));
    }

    // Conta os apontamentos do filtro, sem o limite.
    @Override
    @Transactional(readOnly = true)
    public long contar(FiltroDeAchados filtro) {
        return achados.contar(
                filtro.severidade().orElse(null),
                filtro.regraId().orElse(null),
                filtro.chaveAcesso().map(ChaveAcesso::valor).orElse(null),
                filtro.apenasAbertos());
    }

    // Método auxiliar que busca de uma vez as tratativas dos itens encontrados.
    private Map<ChaveDeTratativa, Tratativa> buscarTratativas(List<AchadoEntidade> encontrados) {
        if (encontrados.isEmpty()) {
            return Map.of();
        }
        Set<String> hashes = encontrados.stream()
                .map(AchadoEntidade::hashItem)
                .collect(Collectors.toSet());

        Map<ChaveDeTratativa, Tratativa> porChave = new LinkedHashMap<>();
        for (TratativaEntidade entidade : tratativas.findByHashItemIn(hashes)) {
            Tratativa tratativa = MapeadorDeTratativa.paraDominio(entidade);
            porChave.put(tratativa.chave(), tratativa);
        }
        return porChave;
    }

    // Método auxiliar que junta o apontamento com a tratativa da mesma chave, se houver.
    private static AchadoRegistrado montar(
            AchadoEntidade entidade, Map<ChaveDeTratativa, Tratativa> tratativas) {

        HashDoItem hashDoItem = new HashDoItem(entidade.hashItem());
        ChaveDeTratativa chave =
                new ChaveDeTratativa(hashDoItem, entidade.regraId(), entidade.regraVersao());

        return new AchadoRegistrado(
                entidade.id(),
                MapeadorDeAchado.paraDominio(entidade),
                hashDoItem,
                Optional.ofNullable(tratativas.get(chave)),
                entidade.detectadoEm(),
                entidade.vistoEm());
    }
}
