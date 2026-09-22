package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ConferenciaInvalida;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaInvalida;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoInvalido;
import br.edu.tcc.auditoria.dominio.excecao.ExcecaoDeDominio;
import br.edu.tcc.auditoria.infraestrutura.upload.PacoteRecusado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz as recusas das camadas de dentro em resposta HTTP.
 *
 * <p>A mensagem original é repassada inteira, e não trocada por um texto genérico
 * da fronteira. As camadas de dentro deste projeto escrevem recusa acionável — "a
 * linha 7 não tem fonteNormativa", "o catálogo não traz alíquota para esta data" —,
 * e é a mesma escolha que {@code LinhaDeComando} fez para o terminal: quando a
 * recusa já se explica, o texto basta.</p>
 *
 * <p>Não há aqui nenhum tratador que devolva 200 com corpo de erro. Uma auditoria
 * que responde "deu certo" para algo que falhou é pior que uma que falha alto.</p>
 */
@RestControllerAdvice
class TratadorDeErrosDaApi {

    /** Identificador bem formado que não existe. Não é erro de quem perguntou. */
    @ExceptionHandler(ExecucaoNaoEncontrada.class)
    ResponseEntity<ErroExposto> naoEncontrada(ExecucaoNaoEncontrada naoEncontrada) {
        return resposta(HttpStatus.NOT_FOUND, "EXECUCAO_NAO_ENCONTRADA", naoEncontrada);
    }

    /** Produto pedido que não está naquela análise. Também não é erro de quem perguntou. */
    @ExceptionHandler(ProdutoNaoEncontrado.class)
    ResponseEntity<ErroExposto> produtoNaoEncontrado(ProdutoNaoEncontrado naoEncontrado) {
        return resposta(HttpStatus.NOT_FOUND, "PRODUTO_NAO_ENCONTRADO", naoEncontrado);
    }

    /** Grupo pedido que não existe naquela análise. */
    @ExceptionHandler(GrupoNaoEncontrado.class)
    ResponseEntity<ErroExposto> grupoNaoEncontrado(GrupoNaoEncontrado naoEncontrado) {
        return resposta(HttpStatus.NOT_FOUND, "GRUPO_NAO_ENCONTRADO", naoEncontrado);
    }

    /**
     * Recusa da montagem da conferência.
     *
     * <p>Acrescentado na Etapa 11. Sai como 409, e não 500, pelo mesmo motivo de
     * {@code PapelDeTrabalhoInvalido}: o caso que a dispara é o banco ter sido
     * alterado por fora — documento removido depois da análise, item sem
     * verificação. Não é defeito do código nem erro de quem consultou, e a
     * mensagem explica o que houve.</p>
     */
    @ExceptionHandler(ConferenciaInvalida.class)
    ResponseEntity<ErroExposto> conferenciaInvalida(ConferenciaInvalida invalida) {
        return resposta(HttpStatus.CONFLICT, "CONFERENCIA_INVALIDA", invalida);
    }

    /** Parâmetro de consulta que a API não sabe interpretar. */
    @ExceptionHandler(PedidoInvalido.class)
    ResponseEntity<ErroExposto> pedidoInvalido(PedidoInvalido invalido) {
        return resposta(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO", invalido);
    }

    /** Consulta que a camada de aplicação recusou. */
    /**
     * Arquivo enviado que o sistema recusa na fronteira.
     *
     * <p>Acrescentado na Etapa 11, quando a API passou a receber arquivo. É
     * recusa de entrada, e por isso 400: a mensagem diz o que fazer — mandar
     * .zip em vez de .rar, dividir o lote, tirar o que não é documento.</p>
     */
    @ExceptionHandler(PacoteRecusado.class)
    ResponseEntity<ErroExposto> pacoteRecusado(PacoteRecusado recusado) {
        return resposta(HttpStatus.BAD_REQUEST, "PACOTE_RECUSADO", recusado);
    }

    @ExceptionHandler(ConsultaInvalida.class)
    ResponseEntity<ErroExposto> consultaInvalida(ConsultaInvalida invalida) {
        return resposta(HttpStatus.BAD_REQUEST, "CONSULTA_INVALIDA", invalida);
    }

    /** Valor que o domínio se recusa a representar. */
    @ExceptionHandler(ExcecaoDeDominio.class)
    ResponseEntity<ErroExposto> recusaDoDominio(ExcecaoDeDominio recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "RECUSA_DO_DOMINIO", recusa);
    }

    /**
     * Dados gravados que se contradizem — 409, não 500.
     *
     * <p>{@code PapelDeTrabalhoInvalido} sai daqui quando há apontamento sobre
     * documento que não está mais gravado, ou quando o recibo da execução conta
     * apontamento diferente do que as linhas trazem. Não é defeito do código nem
     * erro de quem consultou: é o banco ter sido alterado por fora. A mensagem diz
     * isso, e por isso vai inteira.</p>
     */
    @ExceptionHandler(PapelDeTrabalhoInvalido.class)
    ResponseEntity<ErroExposto> dadosInconsistentes(PapelDeTrabalhoInvalido inconsistente) {
        return resposta(HttpStatus.CONFLICT, "DADOS_INCONSISTENTES", inconsistente);
    }

    private static ResponseEntity<ErroExposto> resposta(
            HttpStatus situacao, String codigo, RuntimeException recusa) {
        return ResponseEntity.status(situacao).body(new ErroExposto(codigo, recusa.getMessage()));
    }
}
