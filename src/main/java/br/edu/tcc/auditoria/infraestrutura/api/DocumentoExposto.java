package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.LocalDate;

/**
 * O documento auditado como a API o mostra.
 *
 * <h2>Pseudônimo sempre, chave de acesso só por configuração</h2>
 *
 * <p>{@code pseudonimo} é o resumo criptográfico da chave, calculado com o mesmo
 * sal de instalação que a Etapa 6 usa no papel de trabalho (D007). Ele identifica
 * o documento entre respostas sem dizer quem é: dois achados do mesmo documento
 * trazem o mesmo pseudônimo.</p>
 *
 * <p>{@code chaveAcesso} vem {@code null} por padrão, com o motivo escrito ao
 * lado. Os dígitos intermediários da chave são o CNPJ do emitente, por definição
 * do leiaute (D005): expor a chave é expor o CNPJ com um passo a mais de
 * trabalho. Quem precisa dela para conferir a nota no ERP liga
 * {@code auditoria.api.expor-chave-de-acesso} na instalação — não é query param
 * (D009).</p>
 *
 * <h2>Modelo, série, número, data e UF ficam ligados</h2>
 *
 * <p>Não são dado de participante: série e número são a numeração sequencial do
 * próprio emitente, e a data e a UF situam a operação. Juntos localizam a nota no
 * sistema da empresa sem que o CNPJ apareça. É a mesma leitura que a D007 fez
 * para a planilha, e ela vale aqui pelo mesmo motivo — com a ressalva, também da
 * D007, de que identificam a operação, e de que por isso a API escuta só em
 * localhost.</p>
 */
public record DocumentoExposto(
        String pseudonimo,
        String chaveAcesso,
        String motivoDaChaveOmitida,
        String modelo,
        String serie,
        String numero,
        LocalDate dataEmissao,
        String ufEmitente) {

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
