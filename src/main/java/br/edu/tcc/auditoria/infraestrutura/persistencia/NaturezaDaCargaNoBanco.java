package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.catalogo.ConsultaDaNaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Classe que lê do banco a procedência declarada de uma carga, da tabela natureza_da_carga criada na V9. É o único lugar que faz essa tradução; o ProvedorDeCatalogoNoBanco usa esta classe, para a faixa de aviso nunca discordar da tela.
@Component
class NaturezaDaCargaNoBanco implements ConsultaDaNaturezaDaCarga {

    private final CargaCatalogoJpa cargas;
    private final NaturezaDaCargaJpa naturezas;

    // Construtor que recebe os repositórios de carga e de natureza.
    NaturezaDaCargaNoBanco(CargaCatalogoJpa cargas, NaturezaDaCargaJpa naturezas) {
        this.cargas = cargas;
        this.naturezas = naturezas;
    }

    // Busca a procedência da carga pela versão; sem versão ou sem carga, é procedência não declarada.
    @Override
    @Transactional(readOnly = true)
    public NaturezaDaCarga daVersao(String versao) {
        if (versao == null || versao.isBlank()) {
            return NaturezaDaCarga.naoDeclarada();
        }
        return cargas.findByVersao(versao)
                .map(carga -> porCargaId(carga.id()))
                .orElseGet(NaturezaDaCarga::naoDeclarada);
    }

    // Busca a procedência das tabelas de uma carga. Sem linha nenhuma, é não declarada, e nunca vira normativo.
    @Transactional(readOnly = true)
    NaturezaDaCarga porCargaId(UUID cargaId) {
        Map<String, Natureza> porTabela = new LinkedHashMap<>();
        for (NaturezaDaCargaEntidade linha : naturezas.findByCargaId(cargaId)) {
            porTabela.put(linha.tabela(), Natureza.valueOf(linha.natureza()));
        }
        return new NaturezaDaCarga(
                Optional.ofNullable(porTabela.get(NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS)),
                Optional.ofNullable(porTabela.get(NaturezaDaCarga.REGISTROS_DE_NCM)),
                Optional.ofNullable(porTabela.get(NaturezaDaCarga.ITENS_DE_ANEXO)),
                Optional.ofNullable(porTabela.get(NaturezaDaCarga.ALIQUOTAS)));
    }
}
