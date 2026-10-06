package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.acuracia.AvaliacaoDeAcuraciaInvalida;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaEsperadaMudou;
import br.edu.tcc.auditoria.aplicacao.historico.HistoricoInvalido;
import br.edu.tcc.auditoria.infraestrutura.acuracia.GabaritoInvalido;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Classe que transforma em resposta HTTP as recusas da Etapa 13: histórico e medição de acurácia pela web. A mensagem das camadas de dentro vai inteira, e diz o que fazer.
@RestControllerAdvice
class TratadorDeErrosDaApresentacao {

    // Filtro do histórico que não serve: 400.
    @ExceptionHandler(HistoricoInvalido.class)
    ResponseEntity<ErroExposto> historicoInvalido(HistoricoInvalido recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO", recusa);
    }

    // Gabarito malformado: 400, com a linha que o leitor apontou.
    @ExceptionHandler(GabaritoInvalido.class)
    ResponseEntity<ErroExposto> gabaritoInvalido(GabaritoInvalido recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "GABARITO_INVALIDO", recusa);
    }

    // Medição que não pôde ser feita, como gabarito vazio: 400.
    @ExceptionHandler(AvaliacaoDeAcuraciaInvalida.class)
    ResponseEntity<ErroExposto> medicaoInvalida(AvaliacaoDeAcuraciaInvalida recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "MEDICAO_INVALIDA", recusa);
    }

    // A carga mais recente mudou depois que a tela mostrou qual seria usada: 409, nada medido nem selado.
    @ExceptionHandler(CargaEsperadaMudou.class)
    ResponseEntity<ErroExposto> cargaMudou(CargaEsperadaMudou recusa) {
        return resposta(HttpStatus.CONFLICT, "CARGA_MUDOU", recusa);
    }

    // Método auxiliar que monta a resposta com o código HTTP, o código do erro e a mensagem original.
    private static ResponseEntity<ErroExposto> resposta(HttpStatus situacao, String codigo, RuntimeException recusa) {
        return ResponseEntity.status(situacao).body(new ErroExposto(codigo, recusa.getMessage()));
    }
}
