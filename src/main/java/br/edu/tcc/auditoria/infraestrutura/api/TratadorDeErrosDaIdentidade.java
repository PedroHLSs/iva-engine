package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaNaoEncontrada;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaSelada;
import br.edu.tcc.auditoria.aplicacao.catalogo.EdicaoDesatualizada;
import br.edu.tcc.auditoria.aplicacao.identidade.AcessoNegado;
import br.edu.tcc.auditoria.aplicacao.identidade.IdentidadeInvalida;
import br.edu.tcc.auditoria.aplicacao.identidade.UltimoAdministrador;
import br.edu.tcc.auditoria.aplicacao.identidade.UsuarioNaoEncontrado;
import br.edu.tcc.auditoria.infraestrutura.catalogo.CargaRecusada;
import br.edu.tcc.auditoria.infraestrutura.catalogo.ImportacaoDeCatalogoInvalida;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LinhaRecusada;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

// Classe que transforma em resposta HTTP as recusas da Etapa 12: usuários, tratativa com autor e cargas de catálogo. Fica separada do TratadorDeErrosDaApi para não mexer nele. A mensagem das camadas de dentro é repassada inteira.
@RestControllerAdvice
class TratadorDeErrosDaIdentidade {

    // Representa a recusa da importação, com cada linha recusada em campo próprio, para a tela montar uma tabela.
    record CargaRecusadaExposta(String erro, String mensagem, List<LinhaRecusadaExposta> recusadas) {
    }

    // Representa uma linha recusada. Linha, coluna e valor vêm null quando o problema é do arquivo inteiro.
    record LinhaRecusadaExposta(String arquivo, Integer linha, String coluna, String valor, String motivo) {
    }

    // Representa a recusa por edição desatualizada, com a prévia nova para a tela mostrar antes de a pessoa tentar de novo.
    record EdicaoDesatualizadaExposta(String erro, String mensagem, CargaExposta.PreviaExposta previaAtual) {
    }

    // Usuário que não existe: 404.
    @ExceptionHandler(UsuarioNaoEncontrado.class)
    ResponseEntity<ErroExposto> usuarioNaoEncontrado(UsuarioNaoEncontrado recusa) {
        return resposta(HttpStatus.NOT_FOUND, "USUARIO_NAO_ENCONTRADO", recusa);
    }

    // Último administrador ativo: 409, porque o pedido é válido mas deixaria o sistema sem administrador.
    @ExceptionHandler(UltimoAdministrador.class)
    ResponseEntity<ErroExposto> ultimoAdministrador(UltimoAdministrador recusa) {
        return resposta(HttpStatus.CONFLICT, "ULTIMO_ADMINISTRADOR", recusa);
    }

    // Ação recusada para quem está logado, como senha atual errada ou usuário desativado: 403.
    @ExceptionHandler(AcessoNegado.class)
    ResponseEntity<ErroExposto> acessoNegado(AcessoNegado recusa) {
        return resposta(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", recusa);
    }

    // Dado de usuário que não serve, como login fora do formato ou senha curta: 400.
    @ExceptionHandler(IdentidadeInvalida.class)
    ResponseEntity<ErroExposto> identidadeInvalida(IdentidadeInvalida recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "USUARIO_INVALIDO", recusa);
    }

    // Carga que não existe: 404.
    @ExceptionHandler(CargaNaoEncontrada.class)
    ResponseEntity<ErroExposto> cargaNaoEncontrada(CargaNaoEncontrada recusa) {
        return resposta(HttpStatus.NOT_FOUND, "CARGA_NAO_ENCONTRADA", recusa);
    }

    // Carga selada que alguém tentou excluir: 409, com a quantidade de análises na mensagem.
    @ExceptionHandler(CargaSelada.class)
    ResponseEntity<ErroExposto> cargaSelada(CargaSelada recusa) {
        return resposta(HttpStatus.CONFLICT, "CARGA_SELADA", recusa);
    }

    // Efeito da edição mudou enquanto a pessoa editava: 409, nada gravado, e a prévia nova vai junto.
    @ExceptionHandler(EdicaoDesatualizada.class)
    ResponseEntity<EdicaoDesatualizadaExposta> edicaoDesatualizada(EdicaoDesatualizada recusa) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new EdicaoDesatualizadaExposta(
                "EDICAO_DESATUALIZADA", recusa.getMessage(),
                CargaExposta.PreviaExposta.de(recusa.previaAtual())));
    }

    // Carga recusada na importação ou na edição: 400, com todas as linhas recusadas.
    @ExceptionHandler(CargaRecusada.class)
    ResponseEntity<CargaRecusadaExposta> cargaRecusada(CargaRecusada recusa) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new CargaRecusadaExposta(
                "CARGA_RECUSADA", recusa.getMessage(),
                recusa.recusadas().stream().map(TratadorDeErrosDaIdentidade::expor).toList()));
    }

    // Outra recusa da leitura do catálogo: 400.
    @ExceptionHandler(ImportacaoDeCatalogoInvalida.class)
    ResponseEntity<ErroExposto> importacaoInvalida(ImportacaoDeCatalogoInvalida recusa) {
        return resposta(HttpStatus.BAD_REQUEST, "CARGA_RECUSADA", recusa);
    }

    // O banco recusou a escrita, por exemplo pelo gatilho que protege carga selada ou o histórico de tratativa: 409, sem repassar o texto do banco.
    // Emenda de 04/10/2026 (D019): a mensagem diz a causa. Até essa data era uma só para toda recusa — "outra pessoa mudou o mesmo dado ao mesmo tempo. Recarregue e tente de novo" —, inclusive para a violação de restrição que um lote com documento repetido produzia, que nenhuma repetição resolve. Só a concorrência de verdade manda tentar de novo.
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErroExposto> bancoRecusou(DataAccessException recusa) {
        if (recusa instanceof ConcurrencyFailureException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroExposto("BANCO_RECUSOU",
                    "Outra operação mudou o mesmo dado ao mesmo tempo, e a gravação foi recusada para não "
                            + "sobrescrevê-la. Recarregue e tente de novo."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroExposto("BANCO_RECUSOU",
                CausaDaRecusaDoBanco.de(recusa)));
    }

    // Método auxiliar que monta uma linha recusada exposta.
    private static LinhaRecusadaExposta expor(LinhaRecusada linha) {
        return new LinhaRecusadaExposta(linha.arquivo(), linha.linha().orElse(null),
                linha.coluna().orElse(null), linha.valor().orElse(null), linha.motivo());
    }

    // Método auxiliar que monta a resposta com o código HTTP, o código do erro e a mensagem original.
    private static ResponseEntity<ErroExposto> resposta(
            HttpStatus situacao, String codigo, RuntimeException recusa) {
        return ResponseEntity.status(situacao).body(new ErroExposto(codigo, recusa.getMessage()));
    }
}
