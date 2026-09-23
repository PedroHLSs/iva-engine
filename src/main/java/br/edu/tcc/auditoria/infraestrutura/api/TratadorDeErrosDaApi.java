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

// Classe que transforma as recusas das camadas de dentro em resposta HTTP, repassando a mensagem original inteira. Nenhum erro sai como 200: dizer "deu certo" para algo que falhou seria pior que falhar.
@RestControllerAdvice
class TratadorDeErrosDaApi {

    // Execução que não existe: responde 404.
    @ExceptionHandler(ExecucaoNaoEncontrada.class)
    ResponseEntity<ErroExposto> naoEncontrada(ExecucaoNaoEncontrada naoEncontrada) {
        return resposta(HttpStatus.NOT_FOUND, "EXECUCAO_NAO_ENCONTRADA", naoEncontrada);
    }

    // Produto que não está naquela análise: responde 404.
    @ExceptionHandler(ProdutoNaoEncontrado.class)
    ResponseEntity<ErroExposto> produtoNaoEncontrado(ProdutoNaoEncontrado naoEncontrado) {
        return resposta(HttpStatus.NOT_FOUND, "PRODUTO_NAO_ENCONTRADO", naoEncontrado);
    }

    // Grupo que não existe naquela análise: responde 404.
    @ExceptionHandler(GrupoNaoEncontrado.class)
    ResponseEntity<ErroExposto> grupoNaoEncontrado(GrupoNaoEncontrado naoEncontrado) {
        return resposta(HttpStatus.NOT_FOUND, "GRUPO_NAO_ENCONTRADO", naoEncontrado);
    }

    // Recusa da montagem da conferência: responde 409, porque o caso é o banco ter sido alterado por fora, e não erro do código nem de quem consultou.
    @ExceptionHandler(ConferenciaInvalida.class)
    ResponseEntity<ErroExposto> conferenciaInvalida(ConferenciaInvalida invalida) {
        return resposta(HttpStatus.CONFLICT, "CONFERENCIA_INVALIDA", invalida);
    }

    // Parâmetro que a API não sabe interpretar: responde 400.
    @ExceptionHandler(PedidoInvalido.class)
    ResponseEntity<ErroExposto> pedidoInvalido(PedidoInvalido invalido) {
        return resposta(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO", invalido);
    }

    // Arquivo enviado que o sistema recusa, como .rar ou pacote grande demais: responde 400, e a mensagem diz o que fazer.
    @ExceptionHandler(PacoteRecusado.class)
    ResponseEntity<ErroExposto> pacoteRecusado(PacoteRecusado recusado) {
        return resposta(HttpStatus.BAD_REQUEST, "PACOTE_RECUSADO", recusado);
    }

    // Consulta que a camada de aplicação recusou: responde 400.
    @ExceptionHandler(ConsultaInvalida.class)
    ResponseEntity<ErroExposto> consultaInvalida(ConsultaInvalida invalida) {
        return resposta(HttpStatus.BAD_REQUEST, "CONSULTA_INVALIDA", invalida);
    }

    // Valor que o domínio recusa representar: responde 400.
    @ExceptionHandler(ExcecaoDeDominio.class)
    ResponseEntity<ErroExposto> recusaDoDominio(ExcecaoDeDominio recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "RECUSA_DO_DOMINIO", recusa);
    }

    // Dados gravados que se contradizem: responde 409, porque o banco foi alterado por fora.
    @ExceptionHandler(PapelDeTrabalhoInvalido.class)
    ResponseEntity<ErroExposto> dadosInconsistentes(PapelDeTrabalhoInvalido inconsistente) {
        return resposta(HttpStatus.CONFLICT, "DADOS_INCONSISTENTES", inconsistente);
    }

    // Método auxiliar que monta a resposta com o código HTTP, o código do erro e a mensagem original.
    private static ResponseEntity<ErroExposto> resposta(
            HttpStatus situacao, String codigo, RuntimeException recusa) {
        return ResponseEntity.status(situacao).body(new ErroExposto(codigo, recusa.getMessage()));
    }
}
