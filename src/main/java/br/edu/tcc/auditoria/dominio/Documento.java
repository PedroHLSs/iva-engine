package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.DocumentoInvalido;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Documento fiscal eletrônico já emitido, na parte que interessa à auditoria de
 * coerência de IBS/CBS: identificação, localização das partes e regime do
 * emitente.
 *
 * <p><strong>Nenhum dado pessoal em texto claro.</strong> CNPJ, CPF, razão
 * social e endereço não têm campo aqui e não têm como entrar: os participantes
 * aparecem apenas como {@link IdentificadorPseudonimizado}, que só aceita
 * resumo criptográfico.</p>
 *
 * <p>O documento não carrega seus itens. {@link ItemDocumento} é um tipo
 * separado, e uma regra que precise dos dois recebe o par. A composição dos
 * dois lados é responsabilidade da camada de aplicação.</p>
 *
 * <p>Campos que podem não vir no documento são {@code Optional}: destinatário
 * no exterior ou consumidor não identificado são situações normais, e o modelo
 * as representa como ausência, nunca como texto vazio.</p>
 *
 * <p>{@code modelo}, {@code serie}, {@code numero}, {@code crtEmitente} e
 * {@code indicadorDestinatario} são guardados como o código declarado, sem
 * juízo sobre quais valores existem: isso é leiaute e norma, e não é afirmado
 * em código.</p>
 */
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

    /** Indica se emitente e destinatário estão em unidades federativas diferentes. */
    public boolean ehInterestadual() {
        return ufDestinatario.map(destino -> destino != ufEmitente).orElse(false);
    }

    private static void exigirPresente(Object valor, String nomeDoCampo) {
        if (valor == null) {
            throw new DocumentoInvalido("O campo \"%s\" do documento é obrigatório.".formatted(nomeDoCampo));
        }
    }

    private static void exigirOptional(Optional<?> valor, String nomeDoCampo) {
        if (valor == null) {
            throw new DocumentoInvalido(
                    "O campo \"%s\" deve ser Optional.empty() quando não informado, nunca nulo."
                            .formatted(nomeDoCampo));
        }
    }

    private static void exigirCodigo(String valor, String nomeDoCampo) {
        exigirPresente(valor, nomeDoCampo);
        if (valor.isBlank()) {
            throw new DocumentoInvalido(
                    "O campo \"%s\" do documento não pode ser vazio.".formatted(nomeDoCampo));
        }
    }

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
