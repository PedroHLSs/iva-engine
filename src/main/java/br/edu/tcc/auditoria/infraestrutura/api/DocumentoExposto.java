package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.LocalDate;

// Representa a nota como a API mostra: pseudônimo, modelo, série, número, data e UF vão sempre. A chave de acesso vem null com o motivo, porque contém o CNPJ do emitente, e só aparece se a instalação ligar auditoria.api.expor-chave-de-acesso.
public record DocumentoExposto(
        String pseudonimo,
        String chaveAcesso,
        String motivoDaChaveOmitida,
        String modelo,
        String serie,
        String numero,
        LocalDate dataEmissao,
        String ufEmitente) {

    // Valida que haja pseudônimo e que a chave venha ou com valor ou com o motivo de estar omitida, nunca os dois.
    public DocumentoExposto {
        if (pseudonimo == null || pseudonimo.isBlank()) {
            throw new RespostaInvalida(
                    "O documento exposto precisa do pseudônimo: é o único identificador que a resposta "
                            + "sempre tem, e sem ele duas linhas do mesmo documento não se reconhecem.");
        }
        if (chaveAcesso == null && (motivoDaChaveOmitida == null || motivoDaChaveOmitida.isBlank())) {
            throw new RespostaInvalida(
                    "Chave de acesso omitida sem motivo. Campo em branco sem explicação é o silêncio "
                            + "que esta camada existe para evitar.");
        }
        if (chaveAcesso != null && motivoDaChaveOmitida != null) {
            throw new RespostaInvalida(
                    "A chave de acesso não pode estar presente e omitida ao mesmo tempo.");
        }
    }
}
