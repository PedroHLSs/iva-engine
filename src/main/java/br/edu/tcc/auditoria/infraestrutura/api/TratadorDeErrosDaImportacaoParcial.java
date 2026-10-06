package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.OrigemDaImportacaoMudou;
import br.edu.tcc.auditoria.aplicacao.catalogo.OrigemDaImportacaoNaoInformada;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

// Classe que transforma em resposta HTTP as recusas da importação parcial. As duas levam a carga mais recente de agora, para a tela mostrar de onde as tabelas viriam antes de a pessoa enviar de novo. Acrescentada em 04/10/2026 (D026).
@RestControllerAdvice
class TratadorDeErrosDaImportacaoParcial {

    // Representa a recusa, com a origem atual em campo próprio.
    record OrigemRecusadaExposta(String erro, String mensagem, OrigemExposta origemAtual) {
    }

    // Representa a carga mais recente como a tela precisa dela: a versão e os dois instantes que o pedido devolve. Instante ausente vem null com o motivo ao lado.
    record OrigemExposta(
            String versao,
            Instant importadoEm,
            Instant alteradaEm,
            String motivoDaAlteracaoAusente,
            boolean selada,
            FaixaDeNatureza natureza) {

        // Método estático que monta a origem exposta a partir do estado da carga.
        static OrigemExposta de(EstadoDaCarga estado) {
            return new OrigemExposta(
                    estado.versao(),
                    estado.importadoEm(),
                    estado.alteradaEm().orElse(null),
                    estado.alteradaEm().isPresent() ? null : "o conteúdo nunca foi alterado depois de importado",
                    estado.selada(),
                    FaixaDeNatureza.de(estado.natureza(), estado.versao()));
        }
    }

    // Importação parcial sem dizer de qual carga herdar, ou sem o instante de importação que a tela mostrou: 400.
    @ExceptionHandler(OrigemDaImportacaoNaoInformada.class)
    ResponseEntity<OrigemRecusadaExposta> origemNaoInformada(OrigemDaImportacaoNaoInformada recusa) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OrigemRecusadaExposta(
                "ORIGEM_NAO_INFORMADA", recusa.getMessage(), OrigemExposta.de(recusa.origemAtual())));
    }

    // A origem que a tela mostrou não é mais a carga mais recente, ou mudou de conteúdo: 409, sem gravar nada.
    @ExceptionHandler(OrigemDaImportacaoMudou.class)
    ResponseEntity<OrigemRecusadaExposta> origemMudou(OrigemDaImportacaoMudou recusa) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new OrigemRecusadaExposta(
                "ORIGEM_DESATUALIZADA", recusa.getMessage(), OrigemExposta.de(recusa.origemAtual())));
    }
}
