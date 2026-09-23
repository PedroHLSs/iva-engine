package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.DocumentoInvalido;

import java.time.LocalDate;
import java.util.Optional;

// Representa a nota fiscal na parte que a auditoria usa: identificação, UFs e regime do emitente. Não guarda CNPJ, CPF, nome nem endereço: os participantes entram só como pseudônimo. Campo que pode não vir é Optional.
public record Documento(
        ChaveAcesso chaveAcesso,
        String modelo,
        String serie,
        String numero,
        LocalDate dataEmissao,
        Uf ufEmitente,
        Optional<Uf> ufDestinatario,
        Optional<String> crtEmitente,
        Optional<String> indicadorDestinatario,
        IdentificadorPseudonimizado identificadorEmitentePseudonimizado,
        Optional<IdentificadorPseudonimizado> identificadorDestinatarioPseudonimizado) {

    // Valida a nota: exige chave, modelo, série, número, data, UF e pseudônimo do emitente, e não aceita nulo nos campos opcionais.
    public Documento {
        exigirPresente(chaveAcesso, "chaveAcesso");
        exigirCodigo(modelo, "modelo");
        exigirCodigo(serie, "serie");
        exigirCodigo(numero, "numero");
        exigirPresente(dataEmissao, "dataEmissao");
        exigirPresente(ufEmitente, "ufEmitente");
        exigirPresente(identificadorEmitentePseudonimizado, "identificadorEmitentePseudonimizado");

        exigirOptional(ufDestinatario, "ufDestinatario");
        exigirOptional(crtEmitente, "crtEmitente");
        exigirOptional(indicadorDestinatario, "indicadorDestinatario");
        exigirOptional(identificadorDestinatarioPseudonimizado, "identificadorDestinatarioPseudonimizado");

        exigirCodigoQuandoPresente(crtEmitente, "crtEmitente");
        exigirCodigoQuandoPresente(indicadorDestinatario, "indicadorDestinatario");
    }

    // Diz se emitente e destinatário estão em UFs diferentes. Sem UF de destino devolve vazio, e não false, porque não dá para saber. Ainda não é usado em produção.
    public Optional<Boolean> ehInterestadual() {
        return ufDestinatario.map(destino -> destino != ufEmitente);
    }

    // Método auxiliar para verificar se um campo obrigatório é nulo e lançar uma exceção.
    private static void exigirPresente(Object valor, String nomeDoCampo) {
        if (valor == null) {
            throw new DocumentoInvalido("O campo \"%s\" do documento é obrigatório.".formatted(nomeDoCampo));
        }
    }

    // Método auxiliar para verificar se um campo opcional é nulo e lançar uma exceção.
    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new DocumentoInvalido(
                    "O campo \"%s\" deve ser Optional.empty() quando não informado, nunca nulo."
                            .formatted(nomeDoCampo));
        }
    }

    // Método auxiliar que exige um código preenchido e não vazio.
    private static void exigirCodigo(String valor, String nomeDoCampo) {
        exigirPresente(valor, nomeDoCampo);
        if (valor.isBlank()) {
            throw new DocumentoInvalido(
                    "O campo \"%s\" do documento não pode ser vazio.".formatted(nomeDoCampo));
        }
    }

    // Método auxiliar que, se o código opcional veio, não deixa que venha em branco.
    private static void exigirCodigoQuandoPresente(Optional<String> valor, String nomeDoCampo) {
        valor.ifPresent(codigo -> {
            if (codigo.isBlank()) {
                throw new DocumentoInvalido(
                        ("O campo \"%s\" veio presente e vazio. Ausência se representa com "
                                + "Optional.empty(), não com texto em branco.").formatted(nomeDoCampo));
            }
        });
    }
}
