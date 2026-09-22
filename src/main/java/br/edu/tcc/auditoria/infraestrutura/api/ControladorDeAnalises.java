package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ResultadoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.conferencia.ChaveDoGrupo;
import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.OrdemDosGrupos;
import br.edu.tcc.auditoria.aplicacao.analise.ServicoDeAnalise;
import br.edu.tcc.auditoria.infraestrutura.upload.AreaDaAnalise;
import br.edu.tcc.auditoria.infraestrutura.upload.LimitesDeUpload;
import br.edu.tcc.auditoria.infraestrutura.upload.PacoteRecusado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

/**
 * A primeira porta de escrita da API.
 *
 * <p>Ela emenda a D009, que decidiu HTTP somente para leitura, e a emenda é
 * estreita: <strong>importar catálogo e tratar achado continuam fora</strong>,
 * pelos mesmos motivos de lá — o primeiro decide o que o sistema afirma sobre a
 * norma, o segundo é ato de uma pessoa identificada, e não há autenticação aqui.
 * O que entrou foi analisar, porque a pergunta que a ferramenta passou a
 * responder não existe sem a nota entrar, e mandar quem trabalha no fiscal
 * digitar {@code java -jar} não é ter produto.</p>
 *
 * <p>O bind continua em {@code 127.0.0.1}, e agora ele segura uma porta de
 * escrita. Ver {@code application-api.properties}.</p>
 *
 * <h2>O arquivo enviado é entrada não confiável</h2>
 *
 * <p>Nada do que chega é usado para montar caminho, e o pacote passa pelo
 * {@code GuardaDePacote} antes de qualquer leitura. A área temporária vive
 * dentro do {@code try}, e some no fim dele mesmo quando a análise falha.</p>
 */
@RestController
@RequestMapping("/api/analises")
class ControladorDeAnalises {

    static final String CAMPO_DO_ARQUIVO = "arquivo";

    private final ServicoDeAnalise analises;
    private final MontadorDeRecibo recibos;
    private final MontadorDaConferenciaExposta conferencias;
    private final LimitesDeUpload limites;

    ControladorDeAnalises(
            ServicoDeAnalise analises,
            MontadorDeRecibo recibos,
            MontadorDaConferenciaExposta conferencias,
            LimitesDeUpload limites) {
        this.analises = analises;
        this.recibos = recibos;
        this.conferencias = conferencias;
        this.limites = limites;
    }

    @PostMapping
    ResponseEntity<RespostaDaConferencia> analisar(
            @RequestParam(CAMPO_DO_ARQUIVO) MultipartFile arquivo) {

        if (arquivo == null || arquivo.isEmpty()) {
            throw new PedidoInvalido(
                    ("Nenhum arquivo foi enviado no campo \"%s\". Envie um .xml de documento fiscal "
                            + "ou um .zip com vários deles.").formatted(CAMPO_DO_ARQUIVO));
        }

        try (InputStream conteudo = arquivo.getInputStream();
             AreaDaAnalise area = AreaDaAnalise.receber(
                     arquivo.getOriginalFilename(), conteudo, limites)) {

            ResultadoDaAnalise resultado = analises.analisar(area.origem());
            ReciboDaAnalise recibo = recibos.de(resultado);
            return ResponseEntity.created(URI.create(recibo.recurso()))
                    .body(conferencias.resultado(resultado.id()));

        } catch (IOException naoLeuOEnvio) {
            throw new PacoteRecusado(
                    "Não foi possível ler o arquivo enviado até o fim. O envio pode ter sido "
                            + "interrompido; tente de novo.",
                    naoLeuOEnvio);
        }
    }

    @GetMapping("/{id}")
    RespostaDaConferencia detalhar(@PathVariable UUID id) {
        return conferencias.resultado(id);
    }

    /**
     * Os produtos da análise, paginados.
     *
     * <p>Cada linha traz a situação do produto <strong>e as quatro contagens
     * dele</strong>: a situação sozinha diria "possível divergência" sobre um
     * produto que também tem três verificações sem conclusão, e a tela precisa
     * das duas coisas.</p>
     */
    @GetMapping("/{id}/produtos")
    RespostaDeProdutos produtos(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        return conferencias.produtos(id, Parametros.pagina(pagina), Parametros.tamanho(tamanho));
    }

    /**
     * O detalhe de um produto: a tela em que a conferência de fato acontece.
     *
     * <p>O endereço é o resumo do item, e não um identificador de linha. Dois
     * efeitos, os dois desejados: ele não carrega nada em texto claro, e deixa de
     * existir quando o item é reprocessado com outro conteúdo — caso em que a
     * resposta é 404 dizendo isso, e não o detalhe de um item que virou outro.</p>
     */
    @GetMapping("/{id}/produtos/{endereco}")
    RespostaDoDetalhe detalharProduto(@PathVariable UUID id, @PathVariable String endereco) {
        return conferencias.detalhe(id, endereco);
    }

    /**
     * A tela do lote: grupos por NCM, cClassTrib e situação.
     *
     * <p>Quem trabalha no fiscal corrige cadastro, não nota. Um NCM classificado
     * errado aparece em quatrocentas notas e continua sendo um erro de
     * parametrização, e é assim que ele tem de aparecer.</p>
     *
     * <p>{@code ordem} é opcional e cai na padrão — valor dos produtos envolvidos,
     * decrescente. A resposta diz o que essa ordem mede, porque ela mede exposição
     * e não gravidade, e oferece a alternativa por quantidade.</p>
     */
    @GetMapping("/{id}/grupos")
    RespostaDeGrupos grupos(
            @PathVariable UUID id, @RequestParam(required = false) String ordem) {
        return conferencias.grupos(
                id,
                Parametros.constante(
                        ordem, OrdemDosGrupos.class, "ordem", OrdemDosGrupos.padrao()));
    }

    /**
     * As notas e os itens que compõem um grupo.
     *
     * <p>O grupo se identifica pelos três componentes que o definem, e não por um
     * código sintético: assim a URL diz o que está sendo olhado, e um NCM ou um
     * cClassTrib ausente se escreve omitindo o parâmetro — que é a mesma ausência
     * que o agrupamento registrou.</p>
     */
    @GetMapping("/{id}/grupos/produtos")
    RespostaDeProdutosDoGrupo produtosDoGrupo(
            @PathVariable UUID id,
            @RequestParam(required = false) String ncm,
            @RequestParam(name = "cClassTrib", required = false) String cClassTrib,
            @RequestParam(required = false) String situacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {

        if (situacao == null || situacao.isBlank()) {
            throw new PedidoInvalido(
                    "O parâmetro \"situacao\" é obrigatório: a situação faz parte da chave do grupo, "
                            + "porque o mesmo NCM com o mesmo cClassTrib pode ter desfechos diferentes "
                            + "em notas diferentes.");
        }
        ChaveDoGrupo chave = new ChaveDoGrupo(
                Parametros.textoOpcional(ncm, "ncm"),
                Parametros.textoOpcional(cClassTrib, "cClassTrib"),
                Parametros.constante(situacao, EstadoDeConferencia.class, "situacao", null));

        return conferencias.produtosDoGrupo(
                id, chave, Parametros.pagina(pagina), Parametros.tamanho(tamanho));
    }

    /**
     * Um envio maior que o teto do contêiner nem chega ao método acima.
     *
     * <p>O contêiner de servlet corta antes, e a exceção dele é genérica. Este
     * tratador a traduz para a mesma recusa que a área da análise daria, para que
     * a pessoa receba a mesma explicação nos dois caminhos.</p>
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErroExposto> envioGrandeDemais() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErroExposto(
                "ENVIO_GRANDE_DEMAIS",
                "O arquivo enviado passa de %s. Divida o acervo em envios menores."
                        .formatted(limites.envioEmMegabytes())));
    }
}
