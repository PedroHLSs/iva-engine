package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.acuracia.ComparadorDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.EscritorDeRelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.FonteDeGabarito;
import br.edu.tcc.auditoria.aplicacao.acuracia.RelatorioDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.acuracia.ServicoDeAvaliacaoDeAcuracia;
import br.edu.tcc.auditoria.aplicacao.analise.FabricaDeLeituraDeLote;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.catalogo.ConsultaDaNaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.infraestrutura.persistencia.ProvedorDaCargaEsperada;
import br.edu.tcc.auditoria.infraestrutura.upload.AreaDaAnalise;
import br.edu.tcc.auditoria.infraestrutura.upload.LimitesDeUpload;
import br.edu.tcc.auditoria.infraestrutura.upload.PacoteRecusado;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

// Controlador de /api/acuracia: mede a acurácia do motor a partir das notas e do gabarito que a pessoa envia, reusando o mesmo serviço e o mesmo comparador do comando avaliar-acuracia. Nada é gravado, porque medir não é auditar (D008). Mas medir entrega a carga mais recente ao motor e a sela, e por isso a tela mostra antes qual carga será usada e selada, e o pedido diz qual carga a tela mostrou. Acrescentado na Etapa 13.
@RestController
@RequestMapping("/api/acuracia")
class ControladorDeAcuracia {

    private final ServicoDeCargas cargas;
    private final FabricaDeLeituraDeLote leituras;
    private final ProvedorDaCargaEsperada provedor;
    private final FonteDeGabarito fonteDeGabarito;
    private final MotorAuditoria motor;
    private final ComparadorDeGabarito comparador;
    private final EscritorDeRelatorioDeAcuracia escritor;
    private final ToleranciaDaExecucao tolerancia;
    private final LimitesDeUpload limites;
    private final ConsultaDaNaturezaDaCarga naturezas;

    // Construtor que recebe o que o serviço de acurácia precisa, a carga mais recente e os limites de envio.
    // Emenda de 04/10/2026 (D023): a tolerância passou a vir com a origem, e a medição a dizê-la, porque as métricas da R05 dependem dela.
    ControladorDeAcuracia(
            ServicoDeCargas cargas,
            FabricaDeLeituraDeLote leituras,
            ProvedorDaCargaEsperada provedor,
            FonteDeGabarito fonteDeGabarito,
            MotorAuditoria motor,
            ComparadorDeGabarito comparador,
            EscritorDeRelatorioDeAcuracia escritor,
            ToleranciaDaExecucao tolerancia,
            LimitesDeUpload limites,
            ConsultaDaNaturezaDaCarga naturezas) {
        this.cargas = cargas;
        this.leituras = leituras;
        this.provedor = provedor;
        this.fonteDeGabarito = fonteDeGabarito;
        this.motor = motor;
        this.comparador = comparador;
        this.escritor = escritor;
        this.tolerancia = tolerancia;
        this.limites = limites;
        this.naturezas = naturezas;
    }

    // Representa o aviso mostrado antes de medir: qual carga será usada, se ela já está selada, e a frase.
    record PreviaDaMedicao(String versaoDaCarga, boolean jaSelada, long analisesQueUsam, String aviso) {
    }

    // Diz, antes de a pessoa confirmar, qual carga a medição vai usar e selar.
    @GetMapping("/previa")
    PreviaDaMedicao previa() {
        EstadoDaCarga maisRecente = cargas.listar().stream()
                .filter(EstadoDaCarga::maisRecente)
                .findFirst()
                .orElseThrow(() -> new PedidoInvalido(
                        "Nenhuma carga de catálogo foi importada ainda: não há contra o que medir."));
        String aviso = maisRecente.selada()
                ? ("Esta medição usará a carga \"%s\", que já está selada e é usada por %d análise(s). Medir "
                        + "não muda nada nela.").formatted(maisRecente.versao(), maisRecente.analisesQueUsam())
                : ("Esta medição usará e selará a carga \"%s\". Hoje ela é rascunho: depois de medir, não poderá "
                        + "mais ser editada no lugar nem excluída — editar passará a criar uma versão nova.")
                        .formatted(maisRecente.versao());
        return new PreviaDaMedicao(maisRecente.versao(), maisRecente.selada(), maisRecente.analisesQueUsam(), aviso);
    }

    // Mede: recebe as notas e o gabarito, roda o motor contra a carga que a tela mostrou, e devolve as métricas.
    @PostMapping
    RespostaDaAcuracia medir(
            @RequestParam(value = "notas", required = false) MultipartFile notas,
            @RequestParam(value = "gabarito", required = false) MultipartFile gabarito,
            @RequestParam(value = "cargaEsperada", required = false) String cargaEsperada) {

        if (notas == null || notas.isEmpty()) {
            throw new PedidoInvalido(
                    "Envie as notas no campo \"notas\": um .zip com os XML, ou um .xml. Medir roda o motor de "
                            + "novo, e o motor precisa das notas.");
        }
        if (gabarito == null || gabarito.isEmpty()) {
            throw new PedidoInvalido(
                    "Envie o gabarito no campo \"gabarito\": o mesmo CSV que o comando avaliar-acuracia "
                            + "consome, com chave_documento, numero_item, regra_id e rotulo_esperado.");
        }

        Path arquivoDoGabarito = null;
        try (InputStream conteudo = notas.getInputStream();
             AreaDaAnalise area = AreaDaAnalise.receber(notas.getOriginalFilename(), conteudo, limites)) {

            arquivoDoGabarito = Files.createTempFile("gabarito-", ".csv");
            gabarito.transferTo(arquivoDoGabarito);

            ServicoDeAvaliacaoDeAcuracia servico = new ServicoDeAvaliacaoDeAcuracia(
                    leituras.nova().fonte(), provedor.para(cargaEsperada), fonteDeGabarito, motor,
                    comparador, escritor, tolerancia);
            RelatorioDeAcuracia relatorio = servico.avaliar(area.origem(), arquivoDoGabarito);
            // D021: a medição sai com a natureza do catálogo contra o qual foi feita.
            return RespostaDaAcuracia.de(relatorio, naturezas.daVersao(relatorio.versaoDoCatalogo()));

        } catch (IOException naoLeuOEnvio) {
            throw new PacoteRecusado(
                    "Não foi possível ler os arquivos enviados até o fim. Tente de novo.", naoLeuOEnvio);
        } finally {
            if (arquivoDoGabarito != null) {
                try {
                    Files.deleteIfExists(arquivoDoGabarito);
                } catch (IOException naoApagou) {
                    // O arquivo temporário é do sistema operacional; sobra no máximo até a próxima limpeza dele.
                }
            }
        }
    }
}
