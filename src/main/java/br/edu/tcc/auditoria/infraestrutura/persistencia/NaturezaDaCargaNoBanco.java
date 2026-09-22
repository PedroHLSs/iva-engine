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

/**
 * A procedência declarada de uma carga, lida da V9.
 *
 * <p>Dono único da tradução entre as linhas de {@code natureza_da_carga} e
 * {@link NaturezaDaCarga}. {@code ProvedorDeCatalogoNoBanco} chama esta classe em
 * vez de repetir o mapeamento: duas leituras do mesmo fato podem divergir, e a
 * divergência aqui seria a faixa de aviso discordando do que a tela mostra.</p>
 */
@Component
class NaturezaDaCargaNoBanco implements ConsultaDaNaturezaDaCarga {

    private final CargaCatalogoJpa cargas;
    private final NaturezaDaCargaJpa naturezas;

    NaturezaDaCargaNoBanco(CargaCatalogoJpa cargas, NaturezaDaCargaJpa naturezas) {
        this.cargas = cargas;
        this.naturezas = naturezas;
    }

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

    /**
     * A procedência das tabelas daquela carga.
     *
     * <p>Sem linha nenhuma, {@link NaturezaDaCarga#naoDeclarada()}: carga anterior
     * à declaração. Não vira "normativo" — supor que dado de origem desconhecida é
     * norma vigente é a afirmação mais cara que este sistema pode fazer por
     * engano.</p>
     */
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
