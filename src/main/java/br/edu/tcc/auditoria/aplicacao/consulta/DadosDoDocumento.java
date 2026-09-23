package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Uf;

import java.time.LocalDate;

// Representa os dados que identificam um documento auditado sem identificar ninguém: modelo, série, número, data e UF, no lugar da chave de acesso.
public record DadosDoDocumento(
        ChaveAcesso chaveAcesso,
        String modelo,
        String serie,
        String numero,
        LocalDate dataEmissao,
        Uf ufEmitente) {

    // Valida que os dados do documento tenham chave de acesso, data de emissão e UF do emitente.
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
