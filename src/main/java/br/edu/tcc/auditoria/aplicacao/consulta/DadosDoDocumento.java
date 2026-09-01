package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Uf;

import java.time.LocalDate;

/**
 * O que identifica um documento auditado sem identificar ninguém.
 *
 * <p>Modelo, série e número são a numeração do próprio emitente; a data e a UF
 * situam a operação. Nenhum deles é dado de participante, e juntos permitem
 * localizar a nota no sistema da empresa sem que o CNPJ apareça em lugar
 * nenhum — que é o ponto de existir este tipo em vez de expor a chave de
 * acesso, cujos dígitos carregam o CNPJ do emitente.</p>
 */
public record DadosDoDocumento(
        ChaveAcesso chaveAcesso,
        String modelo,
        String serie,
        String numero,
        LocalDate dataEmissao,
        Uf ufEmitente) {

    public DadosDoDocumento {
        if (chaveAcesso == null) {
            throw new ConsultaInvalida("Os dados do documento precisam da chave de acesso.");
        }
        if (dataEmissao == null) {
            throw new ConsultaInvalida("Os dados do documento precisam da data de emissão.");
        }
        if (ufEmitente == null) {
            throw new ConsultaInvalida("Os dados do documento precisam da UF do emitente.");
        }
    }
}
