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

// Controlador de /api/analises, a única porta de escrita da API: recebe um .xml ou um .zip, roda a análise e devolve o resultado. Importar catálogo e tratar achado continuam fora, o servidor só escuta em 127.0.0.1, e o arquivo recebido passa pelo GuardaDePacote antes de ser lido.
@RestController
@RequestMapping("/api/analises")
class ControladorDeAnalises {

    // Nome do campo do formulário em que o arquivo chega.
    static final String CAMPO_DO_ARQUIVO = "arquivo";

    private final ServicoDeAnalise analises;
    private final MontadorDeRecibo recibos;
    private final MontadorDaConferenciaExposta conferencias;
    private final LimitesDeUpload limites;

    // Construtor que recebe o serviço de análise, os dois montadores de resposta e os limites de envio.
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

    // Recebe o arquivo, roda a análise e devolve o resultado com o endereço dela. A área temporária é apagada no fim, mesmo se a análise falhar.
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

    // Devolve o resultado de uma análise já feita.
    @GetMapping("/{id}")
    RespostaDaConferencia detalhar(@PathVariable UUID id) {
        return conferencias.resultado(id);
    }

    // Lista uma página dos produtos da análise, cada um com a situação e as quatro contagens.
    @GetMapping("/{id}/produtos")
    RespostaDeProdutos produtos(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        return conferencias.produtos(id, Parametros.pagina(pagina), Parametros.tamanho(tamanho));
    }

    // Devolve o detalhe de um produto. O endereço é o resumo do item: não mostra nada em texto claro e deixa de valer se o item for reprocessado com outro conteúdo.
    @GetMapping("/{id}/produtos/{endereco}")
    RespostaDoDetalhe detalharProduto(@PathVariable UUID id, @PathVariable String endereco) {
        return conferencias.detalhe(id, endereco);
    }

    // Devolve a tela do lote, com os produtos agrupados por NCM, cClassTrib e situação. A ordem é opcional; a padrão é pelo valor dos produtos envolvidos, que mede exposição, e não gravidade.
    @GetMapping("/{id}/grupos")
    RespostaDeGrupos grupos(
            @PathVariable UUID id, @RequestParam(required = false) String ordem) {
        return conferencias.grupos(
                id,
                Parametros.constante(
                        ordem, OrdemDosGrupos.class, "ordem", OrdemDosGrupos.padrao()));
    }

    // Lista as notas e os itens de um grupo. O grupo vem pelos três campos que o definem: NCM ou cClassTrib ausente é só omitir o parâmetro, e a situação é obrigatória.
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

    // Responde 413 quando o arquivo passa do limite do servidor, que corta o envio antes de chegar aos métodos acima, com a mesma explicação que a análise daria.
    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErroExposto> envioGrandeDemais() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErroExposto(
                "ENVIO_GRANDE_DEMAIS",
                "O arquivo enviado passa de %s. Divida o acervo em envios menores."
                        .formatted(limites.envioEmMegabytes())));
    }
}
